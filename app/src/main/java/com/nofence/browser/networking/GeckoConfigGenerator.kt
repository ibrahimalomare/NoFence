package com.nofence.browser.networking

import com.nofence.browser.settings.BrowserSettingsState
import com.nofence.browser.settings.ConnectionMode
import com.nofence.browser.settings.CookiePolicy
import com.nofence.browser.storage.ProxyProfileEntity
import com.nofence.browser.storage.ProxyProtocol
import java.io.File

/**
 * Generates the Mozilla GeckoView YAML configuration file read by
 * `GeckoRuntimeSettings.Builder().configFilePath(path)` and computes the preference map
 * enforced across Tor, Custom Proxy, and Direct connection modes.
 *
 * Official Reference:
 * https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/automation.html
 */
object GeckoConfigGenerator {

    data class EffectiveEndpoint(
        val proxyTypeInt: Int, // 0 = direct, 1 = manual proxy
        val socksHost: String = "",
        val socksPort: Int = 0,
        val httpHost: String = "",
        val httpPort: Int = 0,
        val sslHost: String = "",
        val sslPort: Int = 0,
        val socksRemoteDns: Boolean = true,
        val failClosedActive: Boolean = false
    )

    /**
     * Resolves the effective proxy preferences for GeckoView.
     * Critically enforces FAIL-CLOSED behavior:
     * - If ConnectionMode == TOR and `isTorReady` is false (and `torStrictFailClosed` is true),
     *   routes GeckoView to a dead local SOCKS5 port (`127.0.0.1:1`) with `socks_remote_dns = true`
     *   so zero packets or DNS lookups can leak over the direct interface.
     * - If ConnectionMode == CUSTOM_PROXY and `activeProxy` is null or `isProxyVerified` is false
     *   (when `proxyFailClosed` is true), similarly routes to `127.0.0.1:1` fail-closed blackhole.
     */
    fun resolveEndpoint(
        settings: BrowserSettingsState,
        isTorReady: Boolean,
        activeProxy: ProxyProfileEntity?,
        isProxyVerified: Boolean
    ): EffectiveEndpoint {
        return when (settings.connectionMode) {
            ConnectionMode.DIRECT -> EffectiveEndpoint(
                proxyTypeInt = 0,
                socksRemoteDns = false,
                failClosedActive = false
            )

            ConnectionMode.TOR -> {
                if (!isTorReady && settings.torStrictFailClosed) {
                    EffectiveEndpoint(
                        proxyTypeInt = 1,
                        socksHost = "127.0.0.1",
                        socksPort = 1,
                        socksRemoteDns = true,
                        failClosedActive = true
                    )
                } else {
                    EffectiveEndpoint(
                        proxyTypeInt = 1,
                        socksHost = settings.torSocksHost,
                        socksPort = settings.torSocksPort,
                        socksRemoteDns = true,
                        failClosedActive = false
                    )
                }
            }

            ConnectionMode.CUSTOM_PROXY -> {
                if (activeProxy == null || (!isProxyVerified && settings.proxyFailClosed)) {
                    EffectiveEndpoint(
                        proxyTypeInt = 1,
                        socksHost = "127.0.0.1",
                        socksPort = 1,
                        socksRemoteDns = true,
                        failClosedActive = true
                    )
                } else {
                    when (activeProxy.protocol) {
                        ProxyProtocol.SOCKS5 -> EffectiveEndpoint(
                            proxyTypeInt = 1,
                            socksHost = activeProxy.host,
                            socksPort = activeProxy.port,
                            socksRemoteDns = activeProxy.remoteDns || settings.preventDnsLeaks,
                            failClosedActive = false
                        )

                        ProxyProtocol.HTTP -> EffectiveEndpoint(
                            proxyTypeInt = 1,
                            httpHost = activeProxy.host,
                            httpPort = activeProxy.port,
                            sslHost = activeProxy.host,
                            sslPort = activeProxy.port,
                            socksRemoteDns = activeProxy.remoteDns || settings.preventDnsLeaks,
                            failClosedActive = false
                        )

                        ProxyProtocol.HTTPS_CONNECT -> EffectiveEndpoint(
                            proxyTypeInt = 1,
                            httpHost = activeProxy.host,
                            httpPort = activeProxy.port,
                            sslHost = activeProxy.host,
                            sslPort = activeProxy.port,
                            socksRemoteDns = activeProxy.remoteDns || settings.preventDnsLeaks,
                            failClosedActive = false
                        )
                    }
                }
            }
        }
    }

    /**
     * Generates the YAML content for `geckoview-config.yaml`.
     */
    fun buildYamlString(
        settings: BrowserSettingsState,
        isTorReady: Boolean,
        activeProxy: ProxyProfileEntity?,
        isProxyVerified: Boolean
    ): String {
        val endpoint = resolveEndpoint(settings, isTorReady, activeProxy, isProxyVerified)

        val cookieBehavior = when (settings.cookiePolicy) {
            CookiePolicy.ACCEPT_ALL -> 0
            CookiePolicy.BLOCK_THIRD_PARTY -> 5 // Total Cookie Protection / dFPI + block cross-site trackers
            CookiePolicy.BLOCK_ALL -> 2
        }

        // In Tor or SOCKS5 Remote DNS mode, we use SOCKS remote DNS (TRR mode 0 over SOCKS tunnel)
        // unless the user explicitly selected strict DoH.
        val trrMode = if (settings.connectionMode == ConnectionMode.TOR) {
            0 // Resolve via Tor exit node (`network.proxy.socks_remote_dns: true`)
        } else {
            settings.dohMode.trrModeInt
        }

        val allowOnion = settings.connectionMode == ConnectionMode.TOR

        return buildString {
            appendLine("# Auto-generated NoFence GeckoView Runtime Configuration")
            appendLine("prefs:")
            appendLine("  network.proxy.type: ${endpoint.proxyTypeInt}")
            appendLine("  network.proxy.socks: "${escapeYaml(endpoint.socksHost)}"")
            appendLine("  network.proxy.socks_port: ${endpoint.socksPort}")
            appendLine("  network.proxy.socks_version: 5")
            appendLine("  network.proxy.socks_remote_dns: ${endpoint.socksRemoteDns}")
            appendLine("  network.proxy.http: "${escapeYaml(endpoint.httpHost)}"")
            appendLine("  network.proxy.http_port: ${endpoint.httpPort}")
            appendLine("  network.proxy.ssl: "${escapeYaml(endpoint.sslHost)}"")
            appendLine("  network.proxy.ssl_port: ${endpoint.sslPort}")
            appendLine("  network.proxy.share_proxy_settings: ${endpoint.httpHost.isNotEmpty()}")
            appendLine("  network.proxy.no_proxies_on: """)
            appendLine("  network.proxy.failover_direct: false")
            appendLine("  network.proxy.allow_hijacking_localhost: false")
            appendLine("  network.dns.blockDotOnion: ${!allowOnion}")
            appendLine("  network.dns.disableIPv6: ${settings.disableIpv6 || settings.connectionMode == ConnectionMode.TOR}")
            appendLine("  network.dns.disablePrefetch: ${settings.preventDnsLeaks}")
            appendLine("  network.dns.disablePrefetchFromHTTPS: ${settings.preventDnsLeaks}")
            appendLine("  network.predictor.enabled: ${!settings.preventDnsLeaks}")
            appendLine("  network.prefetch-next: ${!settings.preventDnsLeaks}")
            appendLine("  network.http.speculative-parallel-limit: ${if (settings.preventDnsLeaks) 0 else 6}")
            appendLine("  network.trr.mode: $trrMode")
            appendLine("  network.trr.uri: "${escapeYaml(settings.dohServerUri)}"")
            appendLine("  dom.security.https_only_mode: ${settings.httpsOnlyMode}")
            appendLine("  dom.security.https_only_mode_pbm: true")
            appendLine("  javascript.enabled: ${settings.javascriptEnabled}")
            appendLine("  dom.disable_open_during_load: ${settings.blockPopups}")
            appendLine("  media.peerconnection.enabled: ${!settings.disableWebRtc}")
            appendLine("  media.peerconnection.ice.default_address_only: true")
            appendLine("  media.peerconnection.ice.no_host: true")
            appendLine("  media.peerconnection.ice.proxy_only_if_behind_proxy: true")
            appendLine("  privacy.resistFingerprinting: ${settings.resistFingerprinting}")
            appendLine("  network.cookie.cookieBehavior: $cookieBehavior")
            appendLine("  toolkit.telemetry.unified: false")
            appendLine("  datareporting.healthreport.uploadEnabled: false")
            appendLine("  datareporting.policy.dataSubmissionEnabled: false")
        }
    }

    fun writeConfigFile(
        configDir: File,
        settings: BrowserSettingsState,
        isTorReady: Boolean,
        activeProxy: ProxyProfileEntity?,
        isProxyVerified: Boolean
    ): File {
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        val configFile = File(configDir, "nofence-geckoview-config.yaml")
        val yamlContent = buildYamlString(settings, isTorReady, activeProxy, isProxyVerified)
        configFile.writeText(yamlContent, Charsets.UTF_8)
        return configFile
    }

    private fun escapeYaml(input: String): String {
        return input.replace("\", "\\").replace(""", "\"")
    }
}
