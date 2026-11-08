package com.nofence.browser.networking

import com.nofence.browser.proxy.ProxyConnectionStatus
import com.nofence.browser.proxy.ProxyManager
import com.nofence.browser.proxy.ProxyManagerState
import com.nofence.browser.settings.BrowserPreferencesRepository
import com.nofence.browser.settings.BrowserSettingsState
import com.nofence.browser.settings.ConnectionMode
import com.nofence.browser.storage.ProxyProtocol
import com.nofence.browser.tor.TorConnectionStatus
import com.nofence.browser.tor.TorManager
import com.nofence.browser.tor.TorRuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Proxy

enum class OverallRoutingStatus {
    DIRECT_ACTIVE,
    CONNECTING,
    PROTECTED_TOR,
    PROTECTED_PROXY,
    FAIL_CLOSED_BLOCKED
}

data class CoordinatedConnectionState(
    val mode: ConnectionMode = ConnectionMode.TOR,
    val overallStatus: OverallRoutingStatus = OverallRoutingStatus.FAIL_CLOSED_BLOCKED,
    val isBrowsingAllowed: Boolean = false,
    val bannerTitle: String = "Tor Mode (Fail-Closed: Disconnected)",
    val bannerSubtitle: String = "Connect Tor to start browsing, or switch connection mode.",
    val torState: TorRuntimeState = TorRuntimeState(),
    val proxyState: ProxyManagerState = ProxyManagerState(),
    val networkStatus: DeviceNetworkStatus = DeviceNetworkStatus()
)

/**
 * Single source of truth coordinating:
 * 1. User-selected `ConnectionMode` (`DIRECT`, `TOR`, `CUSTOM_PROXY`)
 * 2. Live `TorManager` state
 * 3. Live `ProxyManager` state
 * 4. Device `NetworkMonitor` state
 *
 * Enforces the strict Acceptance Criteria rule:
 * - Never silently downgrade from Tor or Custom Proxy to a Direct connection.
 * - Block browsing (`isBrowsingAllowed = false`) when Tor-only mode is selected and Tor is not `CONNECTED`,
 *   or when Custom Proxy mode is selected with fail-closed enabled and the proxy is not `CONNECTED`.
 */
class ConnectionCoordinator(
    private val preferencesRepository: BrowserPreferencesRepository,
    val torManager: TorManager,
    val proxyManager: ProxyManager,
    val networkMonitor: NetworkMonitor,
    private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow(CoordinatedConnectionState())
    val state: StateFlow<CoordinatedConnectionState> = _state.asStateFlow()

    init {
        networkMonitor.startMonitoring()
        scope.launch(Dispatchers.Default) {
            combine(
                preferencesRepository.settingsFlow,
                torManager.state,
                proxyManager.state,
                networkMonitor.networkStatus
            ) { settings, tor, proxy, net ->
                computeCoordinatedState(settings, tor, proxy, net)
            }.collect { newState ->
                _state.value = newState
            }
        }
    }

    fun computeCoordinatedState(
        settings: BrowserSettingsState,
        tor: TorRuntimeState,
        proxy: ProxyManagerState,
        net: DeviceNetworkStatus
    ): CoordinatedConnectionState {
        return when (settings.connectionMode) {
            ConnectionMode.DIRECT -> CoordinatedConnectionState(
                mode = ConnectionMode.DIRECT,
                overallStatus = OverallRoutingStatus.DIRECT_ACTIVE,
                isBrowsingAllowed = net.isConnected,
                bannerTitle = "Direct Connection (${net.interfaceSummary})",
                bannerSubtitle = "Traffic is not anonymized via Tor or a custom proxy.",
                torState = tor,
                proxyState = proxy,
                networkStatus = net
            )

            ConnectionMode.TOR -> {
                when (tor.status) {
                    TorConnectionStatus.CONNECTED -> CoordinatedConnectionState(
                        mode = ConnectionMode.TOR,
                        overallStatus = OverallRoutingStatus.PROTECTED_TOR,
                        isBrowsingAllowed = true,
                        bannerTitle = "Tor Connected (${tor.socksHost}:${tor.socksPort})",
                        bannerSubtitle = "SOCKS5 + Remote Tor DNS active • ${tor.activeBridgeMode.label}",
                        torState = tor,
                        proxyState = proxy,
                        networkStatus = net
                    )

                    TorConnectionStatus.CONNECTING -> CoordinatedConnectionState(
                        mode = ConnectionMode.TOR,
                        overallStatus = OverallRoutingStatus.CONNECTING,
                        isBrowsingAllowed = !settings.torStrictFailClosed,
                        bannerTitle = "Bootstrapping Tor (${tor.bootstrapProgress}%)",
                        bannerSubtitle = tor.bootstrapSummary,
                        torState = tor,
                        proxyState = proxy,
                        networkStatus = net
                    )

                    TorConnectionStatus.DISCONNECTED, TorConnectionStatus.ERROR -> {
                        val blocked = settings.torStrictFailClosed
                        CoordinatedConnectionState(
                            mode = ConnectionMode.TOR,
                            overallStatus = OverallRoutingStatus.FAIL_CLOSED_BLOCKED,
                            isBrowsingAllowed = !blocked,
                            bannerTitle = if (tor.status == TorConnectionStatus.ERROR) {
                                "Tor Error — Fail-Closed Active"
                            } else {
                                "Tor Disconnected — Fail-Closed Active"
                            },
                            bannerSubtitle = tor.errorMessage
                                ?: "Browsing is blocked until Tor connects so your IP never leaks.",
                            torState = tor,
                            proxyState = proxy,
                            networkStatus = net
                        )
                    }
                }
            }

            ConnectionMode.CUSTOM_PROXY -> {
                val activeProfile = proxy.activeProfile
                when (proxy.status) {
                    ProxyConnectionStatus.CONNECTED -> CoordinatedConnectionState(
                        mode = ConnectionMode.CUSTOM_PROXY,
                        overallStatus = OverallRoutingStatus.PROTECTED_PROXY,
                        isBrowsingAllowed = true,
                        bannerTitle = "Proxy Connected: ${activeProfile?.name ?: "Custom"}",
                        bannerSubtitle = "${activeProfile?.protocol?.name ?: ""} ${activeProfile?.host}:${activeProfile?.port} • Remote DNS=${activeProfile?.remoteDns ?: true}",
                        torState = tor,
                        proxyState = proxy,
                        networkStatus = net
                    )

                    ProxyConnectionStatus.VERIFYING -> CoordinatedConnectionState(
                        mode = ConnectionMode.CUSTOM_PROXY,
                        overallStatus = OverallRoutingStatus.CONNECTING,
                        isBrowsingAllowed = !settings.proxyFailClosed,
                        bannerTitle = "Verifying Proxy...",
                        bannerSubtitle = proxy.statusMessage,
                        torState = tor,
                        proxyState = proxy,
                        networkStatus = net
                    )

                    ProxyConnectionStatus.DISCONNECTED, ProxyConnectionStatus.ERROR -> {
                        val blocked = settings.proxyFailClosed
                        CoordinatedConnectionState(
                            mode = ConnectionMode.CUSTOM_PROXY,
                            overallStatus = OverallRoutingStatus.FAIL_CLOSED_BLOCKED,
                            isBrowsingAllowed = !blocked,
                            bannerTitle = "Custom Proxy Unverified — Fail-Closed Active",
                            bannerSubtitle = proxy.statusMessage,
                            torState = tor,
                            proxyState = proxy,
                            networkStatus = net
                        )
                    }
                }
            }
        }
    }

    /**
     * Returns a `java.net.Proxy` matching the active connection mode so app-managed downloads
     * are routed through the exact same SOCKS5/HTTP tunnel and never leak over a direct connection.
     * Throws `IllegalStateException` if fail-closed blocking is active.
     */
    fun getJavaNetProxyForDownloads(): Proxy {
        val current = _state.value
        if (!current.isBrowsingAllowed) {
            throw IllegalStateException("Fail-Closed active: Downloads are blocked while ${current.mode.name} is disconnected.")
        }
        return when (current.mode) {
            ConnectionMode.DIRECT -> Proxy.NO_PROXY
            ConnectionMode.TOR -> Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress.createUnresolved(current.torState.socksHost, current.torState.socksPort)
            )
            ConnectionMode.CUSTOM_PROXY -> {
                val profile = current.proxyState.activeProfile
                    ?: throw IllegalStateException("No active custom proxy profile for download")
                val type = when (profile.protocol) {
                    ProxyProtocol.SOCKS5 -> Proxy.Type.SOCKS
                    ProxyProtocol.HTTP, ProxyProtocol.HTTPS_CONNECT -> Proxy.Type.HTTP
                }
                Proxy(type, InetSocketAddress.createUnresolved(profile.host, profile.port))
            }
        }
    }
}
