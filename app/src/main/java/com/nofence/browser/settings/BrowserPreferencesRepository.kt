package com.nofence.browser.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nofence_settings")

enum class ConnectionMode {
    DIRECT,
    TOR,
    CUSTOM_PROXY
}

enum class SearchEngine(val displayName: String, val queryTemplate: String, val homeUrl: String) {
    DUCKDUCKGO_ONION(
        "DuckDuckGo (.onion / HTTPS)",
        "https://duckduckgo.com/?q=%s",
        "https://duckduckgo.com"
    ),
    BRAVE_SEARCH(
        "Brave Search",
        "https://search.brave.com/search?q=%s",
        "https://search.brave.com"
    ),
    STARTPAGE(
        "Startpage",
        "https://www.startpage.com/do/dsearch?query=%s",
        "https://www.startpage.com"
    ),
    MOJEK(
        "Mojeek",
        "https://www.mojeek.com/search?q=%s",
        "https://www.mojeek.com"
    )
}

enum class CookiePolicy(val label: String) {
    ACCEPT_ALL("Allow all cookies"),
    BLOCK_THIRD_PARTY("Block third-party cookies (Recommended)"),
    BLOCK_ALL("Block all cookies (May break logins)")
}

enum class DohMode(val label: String, val trrModeInt: Int) {
    STANDARD_SYSTEM("System DNS (Or SOCKS Remote DNS when routed)", 0),
    DOH_PREFERRED("DNS-over-HTTPS Preferred (TRR Mode 2)", 2),
    DOH_STRICT("DNS-over-HTTPS Only / Fail-Closed (TRR Mode 3)", 3),
    DOH_OFF("Disable DoH (TRR Mode 5)", 5)
}

enum class BridgeTransportType(val label: String) {
    NONE("Direct Tor Network (No Bridges)"),
    OBFS4_BUILT_IN("Built-in obfs4 Bridges"),
    SNOWFLAKE_BUILT_IN("Snowflake Pluggable Transport"),
    CUSTOM_BRIDGES("Custom Bridge Lines (obfs4 / Snowflake)")
}

data class BrowserSettingsState(
    val connectionMode: ConnectionMode = ConnectionMode.TOR,
    val torStrictFailClosed: Boolean = true,
    val torUseExternalOrbot: Boolean = false,
    val torSocksHost: String = "127.0.0.1",
    val torSocksPort: Int = 9050,
    val torControlPort: Int = 9051,
    val bridgeTransportType: BridgeTransportType = BridgeTransportType.NONE,
    val customBridgeLines: String = "",
    val proxyFailClosed: Boolean = true,
    val proxyAutoFallback: Boolean = true,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO_ONION,
    val javascriptEnabled: Boolean = true,
    val blockPopups: Boolean = true,
    val httpsOnlyMode: Boolean = true,
    val trackerProtectionStrict: Boolean = true,
    val disableWebRtc: Boolean = true,
    val disableIpv6: Boolean = true,
    val preventDnsLeaks: Boolean = true,
    val resistFingerprinting: Boolean = true,
    val cookiePolicy: CookiePolicy = CookiePolicy.BLOCK_THIRD_PARTY,
    val dohMode: DohMode = DohMode.DOH_PREFERRED,
    val dohServerUri: String = "https://mozilla.cloudflare-dns.com/dns-query",
    val restoreSessionOnStartup: Boolean = true,
    val clearDataOnExit: Boolean = false
)

class BrowserPreferencesRepository(private val context: Context) {

    private object Keys {
        val CONNECTION_MODE = stringPreferencesKey("connection_mode")
        val TOR_STRICT_FAIL_CLOSED = booleanPreferencesKey("tor_strict_fail_closed")
        val TOR_USE_EXTERNAL_ORBOT = booleanPreferencesKey("tor_use_external_orbot")
        val TOR_SOCKS_HOST = stringPreferencesKey("tor_socks_host")
        val TOR_SOCKS_PORT = intPreferencesKey("tor_socks_port")
        val TOR_CONTROL_PORT = intPreferencesKey("tor_control_port")
        val BRIDGE_TRANSPORT = stringPreferencesKey("bridge_transport")
        val CUSTOM_BRIDGE_LINES = stringPreferencesKey("custom_bridge_lines")
        val PROXY_FAIL_CLOSED = booleanPreferencesKey("proxy_fail_closed")
        val PROXY_AUTO_FALLBACK = booleanPreferencesKey("proxy_auto_fallback")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
        val BLOCK_POPUPS = booleanPreferencesKey("block_popups")
        val HTTPS_ONLY_MODE = booleanPreferencesKey("https_only_mode")
        val TRACKER_PROTECTION_STRICT = booleanPreferencesKey("tracker_protection_strict")
        val DISABLE_WEBRTC = booleanPreferencesKey("disable_webrtc")
        val DISABLE_IPV6 = booleanPreferencesKey("disable_ipv6")
        val PREVENT_DNS_LEAKS = booleanPreferencesKey("prevent_dns_leaks")
        val RESIST_FINGERPRINTING = booleanPreferencesKey("resist_fingerprinting")
        val COOKIE_POLICY = stringPreferencesKey("cookie_policy")
        val DOH_MODE = stringPreferencesKey("doh_mode")
        val DOH_SERVER_URI = stringPreferencesKey("doh_server_uri")
        val RESTORE_SESSION = booleanPreferencesKey("restore_session")
        val CLEAR_ON_EXIT = booleanPreferencesKey("clear_on_exit")
    }

    val settingsFlow: Flow<BrowserSettingsState> = context.dataStore.data.map { prefs ->
        BrowserSettingsState(
            connectionMode = prefs[Keys.CONNECTION_MODE]?.let {
                runCatching { ConnectionMode.valueOf(it) }.getOrNull()
            } ?: ConnectionMode.TOR,
            torStrictFailClosed = prefs[Keys.TOR_STRICT_FAIL_CLOSED] ?: true,
            torUseExternalOrbot = prefs[Keys.TOR_USE_EXTERNAL_ORBOT] ?: false,
            torSocksHost = prefs[Keys.TOR_SOCKS_HOST] ?: "127.0.0.1",
            torSocksPort = prefs[Keys.TOR_SOCKS_PORT] ?: 9050,
            torControlPort = prefs[Keys.TOR_CONTROL_PORT] ?: 9051,
            bridgeTransportType = prefs[Keys.BRIDGE_TRANSPORT]?.let {
                runCatching { BridgeTransportType.valueOf(it) }.getOrNull()
            } ?: BridgeTransportType.NONE,
            customBridgeLines = prefs[Keys.CUSTOM_BRIDGE_LINES] ?: DEFAULT_OBFS4_BRIDGES,
            proxyFailClosed = prefs[Keys.PROXY_FAIL_CLOSED] ?: true,
            proxyAutoFallback = prefs[Keys.PROXY_AUTO_FALLBACK] ?: true,
            searchEngine = prefs[Keys.SEARCH_ENGINE]?.let {
                runCatching { SearchEngine.valueOf(it) }.getOrNull()
            } ?: SearchEngine.DUCKDUCKGO_ONION,
            javascriptEnabled = prefs[Keys.JAVASCRIPT_ENABLED] ?: true,
            blockPopups = prefs[Keys.BLOCK_POPUPS] ?: true,
            httpsOnlyMode = prefs[Keys.HTTPS_ONLY_MODE] ?: true,
            trackerProtectionStrict = prefs[Keys.TRACKER_PROTECTION_STRICT] ?: true,
            disableWebRtc = prefs[Keys.DISABLE_WEBRTC] ?: true,
            disableIpv6 = prefs[Keys.DISABLE_IPV6] ?: true,
            preventDnsLeaks = prefs[Keys.PREVENT_DNS_LEAKS] ?: true,
            resistFingerprinting = prefs[Keys.RESIST_FINGERPRINTING] ?: true,
            cookiePolicy = prefs[Keys.COOKIE_POLICY]?.let {
                runCatching { CookiePolicy.valueOf(it) }.getOrNull()
            } ?: CookiePolicy.BLOCK_THIRD_PARTY,
            dohMode = prefs[Keys.DOH_MODE]?.let {
                runCatching { DohMode.valueOf(it) }.getOrNull()
            } ?: DohMode.DOH_PREFERRED,
            dohServerUri = prefs[Keys.DOH_SERVER_URI] ?: "https://mozilla.cloudflare-dns.com/dns-query",
            restoreSessionOnStartup = prefs[Keys.RESTORE_SESSION] ?: true,
            clearDataOnExit = prefs[Keys.CLEAR_ON_EXIT] ?: false
        )
    }

    suspend fun setConnectionMode(mode: ConnectionMode) {
        context.dataStore.edit { it[Keys.CONNECTION_MODE] = mode.name }
    }

    suspend fun updateTorSettings(
        strictFailClosed: Boolean? = null,
        useExternalOrbot: Boolean? = null,
        socksHost: String? = null,
        socksPort: Int? = null,
        controlPort: Int? = null,
        bridgeTransportType: BridgeTransportType? = null,
        customBridgeLines: String? = null
    ) {
        context.dataStore.edit { prefs ->
            strictFailClosed?.let { prefs[Keys.TOR_STRICT_FAIL_CLOSED] = it }
            useExternalOrbot?.let { prefs[Keys.TOR_USE_EXTERNAL_ORBOT] = it }
            socksHost?.let { prefs[Keys.TOR_SOCKS_HOST] = it.trim() }
            socksPort?.let { prefs[Keys.TOR_SOCKS_PORT] = it }
            controlPort?.let { prefs[Keys.TOR_CONTROL_PORT] = it }
            bridgeTransportType?.let { prefs[Keys.BRIDGE_TRANSPORT] = it.name }
            customBridgeLines?.let { prefs[Keys.CUSTOM_BRIDGE_LINES] = it }
        }
    }

    suspend fun updateProxySettings(
        failClosed: Boolean? = null,
        autoFallback: Boolean? = null
    ) {
        context.dataStore.edit { prefs ->
            failClosed?.let { prefs[Keys.PROXY_FAIL_CLOSED] = it }
            autoFallback?.let { prefs[Keys.PROXY_AUTO_FALLBACK] = it }
        }
    }

    suspend fun updatePrivacySettings(
        javascriptEnabled: Boolean? = null,
        blockPopups: Boolean? = null,
        httpsOnlyMode: Boolean? = null,
        trackerProtectionStrict: Boolean? = null,
        disableWebRtc: Boolean? = null,
        disableIpv6: Boolean? = null,
        preventDnsLeaks: Boolean? = null,
        resistFingerprinting: Boolean? = null,
        cookiePolicy: CookiePolicy? = null,
        dohMode: DohMode? = null,
        dohServerUri: String? = null,
        searchEngine: SearchEngine? = null,
        restoreSession: Boolean? = null,
        clearOnExit: Boolean? = null
    ) {
        context.dataStore.edit { prefs ->
            javascriptEnabled?.let { prefs[Keys.JAVASCRIPT_ENABLED] = it }
            blockPopups?.let { prefs[Keys.BLOCK_POPUPS] = it }
            httpsOnlyMode?.let { prefs[Keys.HTTPS_ONLY_MODE] = it }
            trackerProtectionStrict?.let { prefs[Keys.TRACKER_PROTECTION_STRICT] = it }
            disableWebRtc?.let { prefs[Keys.DISABLE_WEBRTC] = it }
            disableIpv6?.let { prefs[Keys.DISABLE_IPV6] = it }
            preventDnsLeaks?.let { prefs[Keys.PREVENT_DNS_LEAKS] = it }
            resistFingerprinting?.let { prefs[Keys.RESIST_FINGERPRINTING] = it }
            cookiePolicy?.let { prefs[Keys.COOKIE_POLICY] = it.name }
            dohMode?.let { prefs[Keys.DOH_MODE] = it.name }
            dohServerUri?.let { prefs[Keys.DOH_SERVER_URI] = it.trim() }
            searchEngine?.let { prefs[Keys.SEARCH_ENGINE] = it.name }
            restoreSession?.let { prefs[Keys.RESTORE_SESSION] = it }
            clearOnExit?.let { prefs[Keys.CLEAR_ON_EXIT] = it }
        }
    }

    companion object {
        val DEFAULT_OBFS4_BRIDGES = listOf(
            "obfs4 192.95.36.142:443 CDF2E852BF539B82BD10E27E9115A31734E378C2 cert=qUVQ0srL1JI/vO6V6m/24anYXiJD3QP2HgzUKQtQ7GRqqU+7P+tG43+9N6kC6B/535205A iat-mode=1",
            "obfs4 37.218.245.14:38224 D9A82D2F9C2F65A18407B1D2B764F130847F8B5D cert=bjRaMrr1BRiAW8IE9U5z27fQaYgOhX1UCmOpg2pFpoMvo6ZgQMzLsaTzzQNTlm7hNcb+Sg iat-mode=0",
            "obfs4 85.31.186.98:443 011F2599C0E9B27EE74B353155E244813763C3E5 cert=ayq0XzCwhpdysn5o0EyDUbmSOx3X/oTEbzDMvczHOdBJKlvIdHHLJGkZARtT4dcBFArPPg iat-mode=0"
        ).joinToString("
")

        const val DEFAULT_SNOWFLAKE_BRIDGE =
            "snowflake 192.0.2.3:80 2B280B23E1107BB62ABFC40DDCC8824814F80A72 fingerprint=2B280B23E1107BB62ABFC40DDCC8824814F80A72 url=https://snowflake-broker.torproject.net.global.prod.fastly.net/ front=cdn.sstatic.net ice=stun:stun.l.google.com:19302,stun:stun.antisip.com:3478 utls-imitate=hellorandomizedalpn"
    }
}
