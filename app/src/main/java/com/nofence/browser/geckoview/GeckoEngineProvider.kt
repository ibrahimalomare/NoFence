package com.nofence.browser.geckoview

import android.content.Context
import com.nofence.browser.networking.CoordinatedConnectionState
import com.nofence.browser.networking.GeckoConfigGenerator
import com.nofence.browser.privacy.EncryptedCredentialStore
import com.nofence.browser.settings.BrowserSettingsState
import com.nofence.browser.settings.ConnectionMode
import com.nofence.browser.settings.CookiePolicy
import com.nofence.browser.storage.ProxyProtocol
import com.nofence.browser.tor.TorConnectionStatus
import com.nofence.browser.proxy.ProxyConnectionStatus
import org.json.JSONObject
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File

/**
 * Manages the singleton Mozilla `GeckoRuntime`, applies `GeckoRuntimeSettings`, writes the
 * `nofence-geckoview-config.yaml` file via `GeckoConfigGenerator`, installs the built-in
 * `proxy-enforcer@nofence.com` WebExtension (`resource://android/assets/extensions/nofence-proxy/`),
 * and pushes live routing updates over `WebExtension.Port` native messaging.
 *
 * Uses Mozilla GeckoView directly — never Android `WebView`.
 */
class GeckoEngineProvider(
    private val context: Context,
    private val credentialStore: EncryptedCredentialStore
) {

    @Volatile
    private var geckoRuntime: GeckoRuntime? = null

    @Volatile
    private var proxyExtensionPort: WebExtension.Port? = null

    @Volatile
    private var latestSettings: BrowserSettingsState = BrowserSettingsState()

    @Volatile
    private var latestConnectionState: CoordinatedConnectionState = CoordinatedConnectionState()

    private val configDir: File by lazy {
        File(context.filesDir, "gecko_config").apply { mkdirs() }
    }

    @Synchronized
    fun getOrCreateRuntime(
        initialSettings: BrowserSettingsState,
        initialConnection: CoordinatedConnectionState
    ): GeckoRuntime {
        latestSettings = initialSettings
        latestConnectionState = initialConnection

        geckoRuntime?.let { return it }

        val isTorReady = initialConnection.torState.status == TorConnectionStatus.CONNECTED
        val isProxyVerified = initialConnection.proxyState.status == ProxyConnectionStatus.CONNECTED

        val yamlFile = GeckoConfigGenerator.writeConfigFile(
            configDir = configDir,
            settings = initialSettings,
            isTorReady = isTorReady,
            activeProxy = initialConnection.proxyState.activeProfile,
            isProxyVerified = isProxyVerified
        )

        val contentBlockingSettings = buildContentBlockingSettings(initialSettings)

        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .configFilePath(yamlFile.absolutePath)
            .javaScriptEnabled(initialSettings.javascriptEnabled)
            .consoleOutput(false)
            .aboutConfigEnabled(false)
            .contentBlocking(contentBlockingSettings)
            .build()

        val runtime = GeckoRuntime.create(context.applicationContext, runtimeSettings)
        geckoRuntime = runtime

        installBuiltInProxyEnforcerExtension(runtime)
        return runtime
    }

    /**
     * Dynamically updates both `GeckoRuntimeSettings`, the on-disk `nofence-geckoview-config.yaml`
     * file, and the built-in WebExtension `browser.proxy.onRequest` listener so switching between
     * Direct, Tor, and Custom Proxy modes takes effect immediately without requiring an app restart.
     */
    fun applyUpdatedSettingsAndRouting(
        settings: BrowserSettingsState,
        connectionState: CoordinatedConnectionState
    ) {
        latestSettings = settings
        latestConnectionState = connectionState

        val isTorReady = connectionState.torState.status == TorConnectionStatus.CONNECTED
        val isProxyVerified = connectionState.proxyState.status == ProxyConnectionStatus.CONNECTED

        GeckoConfigGenerator.writeConfigFile(
            configDir = configDir,
            settings = settings,
            isTorReady = isTorReady,
            activeProxy = connectionState.proxyState.activeProfile,
            isProxyVerified = isProxyVerified
        )

        geckoRuntime?.settings?.apply {
            javaScriptEnabled = settings.javascriptEnabled
        }

        pushRoutingStateToWebExtension(settings, connectionState)
    }

    private fun installBuiltInProxyEnforcerExtension(runtime: GeckoRuntime) {
        val extController: WebExtensionController = runtime.webExtensionController
        val messageDelegate = object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                proxyExtensionPort = port
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        // Acknowledged routing update from background.js
                    }

                    override fun onDisconnect(port: WebExtension.Port) {
                        if (proxyExtensionPort === port) {
                            proxyExtensionPort = null
                        }
                    }
                })
                pushRoutingStateToWebExtension(latestSettings, latestConnectionState)
            }
        }

        extController
            .ensureBuiltIn(
                "resource://android/assets/extensions/nofence-proxy/",
                "proxy-enforcer@nofence.com"
            )
            .accept(
                { extension ->
                    if (extension != null) {
                        // Allow the proxy enforcer extension to run in Private Browsing tabs as well
                        extController.setAllowedInPrivateBrowsing(extension, true)
                        extension.setMessageDelegate(messageDelegate, "nofence_proxy_channel")
                    }
                },
                { _ ->
                    // Fallback to installBuiltIn if ensureBuiltIn fails on first run
                    extController.installBuiltIn("resource://android/assets/extensions/nofence-proxy/")
                        .accept({ ext ->
                            if (ext != null) {
                                extController.setAllowedInPrivateBrowsing(ext, true)
                                ext.setMessageDelegate(messageDelegate, "nofence_proxy_channel")
                            }
                        }, { })
                }
            )
    }

    private fun pushRoutingStateToWebExtension(
        settings: BrowserSettingsState,
        connectionState: CoordinatedConnectionState
    ) {
        val port = proxyExtensionPort ?: return
        val json = JSONObject().apply {
            put("type", "UPDATE_ROUTING")
            put("mode", settings.connectionMode.name)
            put("httpsFirst", settings.httpsOnlyMode)

            when (settings.connectionMode) {
                ConnectionMode.DIRECT -> {
                    put("failClosedBlock", false)
                    put("proxyType", "direct")
                    put("host", "")
                    put("port", 0)
                    put("username", "")
                    put("password", "")
                    put("proxyDns", false)
                }

                ConnectionMode.TOR -> {
                    val isTorReady = connectionState.torState.status == TorConnectionStatus.CONNECTED
                    val failClosed = !isTorReady && settings.torStrictFailClosed
                    put("failClosedBlock", failClosed)
                    put("proxyType", "socks")
                    put("host", settings.torSocksHost)
                    put("port", settings.torSocksPort)
                    put("username", "")
                    put("password", "")
                    put("proxyDns", true)
                }

                ConnectionMode.CUSTOM_PROXY -> {
                    val profile = connectionState.proxyState.activeProfile
                    val isVerified = connectionState.proxyState.status == ProxyConnectionStatus.CONNECTED
                    val failClosed = profile == null || (!isVerified && settings.proxyFailClosed)
                    put("failClosedBlock", failClosed)

                    if (profile != null) {
                        val proxyTypeStr = when (profile.protocol) {
                            ProxyProtocol.SOCKS5 -> "socks"
                            ProxyProtocol.HTTP -> "http"
                            ProxyProtocol.HTTPS_CONNECT -> "https"
                        }
                        val creds = credentialStore.getProxyCredentials(profile.id)
                        put("proxyType", proxyTypeStr)
                        put("host", profile.host)
                        put("port", profile.port)
                        put("username", creds?.username ?: "")
                        put("password", creds?.password ?: "")
                        put("proxyDns", profile.remoteDns || settings.preventDnsLeaks)
                    } else {
                        put("proxyType", "socks")
                        put("host", "127.0.0.1")
                        put("port", 1)
                        put("username", "")
                        put("password", "")
                        put("proxyDns", true)
                    }
                }
            }
        }
        runCatching {
            port.postMessage(json)
        }
    }

    private fun buildContentBlockingSettings(settings: BrowserSettingsState): ContentBlocking.Settings {
        val antiTrackingFlags = if (settings.trackerProtectionStrict) {
            ContentBlocking.AntiTracking.STRICT or ContentBlocking.AntiTracking.STP
        } else {
            ContentBlocking.AntiTracking.DEFAULT
        }

        val cookieBehavior = when (settings.cookiePolicy) {
            CookiePolicy.ACCEPT_ALL -> ContentBlocking.CookieBehavior.ACCEPT_ALL
            CookiePolicy.BLOCK_THIRD_PARTY -> ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
            CookiePolicy.BLOCK_ALL -> ContentBlocking.CookieBehavior.ACCEPT_NONE
        }

        return ContentBlocking.Settings.Builder()
            .antiTracking(antiTrackingFlags)
            .safeBrowsing(ContentBlocking.SafeBrowsing.NONE) // Avoid phoning home to Google SafeBrowsing in a privacy browser
            .cookieBehavior(cookieBehavior)
            .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
            .enhancedTrackingProtectionLevel(
                if (settings.trackerProtectionStrict) {
                    ContentBlocking.EtpLevel.STRICT
                } else {
                    ContentBlocking.EtpLevel.DEFAULT
                }
            )
            .build()
    }

    /**
     * Clears cookies, cache, DOM storage, auth tokens, and site permissions inside GeckoView's
     * `StorageController`.
     */
    fun clearGeckoBrowsingData(
        clearCookiesAndSiteData: Boolean = true,
        clearCache: Boolean = true,
        clearSitePermissions: Boolean = false,
        onComplete: () -> Unit = {}
    ) {
        val runtime = geckoRuntime ?: run {
            onComplete()
            return
        }
        var flags = 0L
        if (clearCookiesAndSiteData) {
            flags = flags or
                StorageController.ClearFlags.COOKIES or
                StorageController.ClearFlags.DOM_STORAGES or
                StorageController.ClearFlags.AUTH_SESSIONS
        }
        if (clearCache) {
            flags = flags or
                StorageController.ClearFlags.IMAGE_CACHE or
                StorageController.ClearFlags.NETWORK_CACHE
        }
        if (clearSitePermissions) {
            flags = flags or StorageController.ClearFlags.PERMISSIONS
        }
        if (flags == 0L) {
            flags = StorageController.ClearFlags.ALL
        }

        val result: GeckoResult<Void> = runtime.storageController.clearData(flags)
        result.accept(
            { onComplete() },
            { onComplete() }
        )
    }

    fun clearSiteDataForHost(host: String, onComplete: () -> Unit = {}) {
        val runtime = geckoRuntime ?: run {
            onComplete()
            return
        }
        runtime.storageController
            .clearDataFromHost(host, StorageController.ClearFlags.ALL_CACHES or StorageController.ClearFlags.COOKIES or StorageController.ClearFlags.DOM_STORAGES)
            .accept({ onComplete() }, { onComplete() })
    }
}
