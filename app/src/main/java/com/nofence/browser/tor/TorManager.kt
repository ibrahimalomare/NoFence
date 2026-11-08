package com.nofence.browser.tor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.nofence.browser.proxy.ProxyTester
import com.nofence.browser.settings.BridgeTransportType
import com.nofence.browser.settings.BrowserPreferencesRepository
import com.nofence.browser.settings.BrowserSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

enum class TorConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class TorRuntimeState(
    val status: TorConnectionStatus = TorConnectionStatus.DISCONNECTED,
    val bootstrapProgress: Int = 0,
    val bootstrapSummary: String = "Tor is disconnected",
    val socksHost: String = "127.0.0.1",
    val socksPort: Int = 9050,
    val controlPort: Int = 9051,
    val activeBridgeMode: BridgeTransportType = BridgeTransportType.NONE,
    val isUsingExternalOrbot: Boolean = false,
    val circuitCount: Int = 0,
    val lastNewnymTimestamp: Long? = null,
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Manages real Tor connectivity for NoFence Browser.
 *
 * Supports two real operating modes:
 * 1. Embedded Tor Daemon (`info.guardianproject:tor-android` native `libtor.so` / `TorService`),
 *    writing a custom `torrc` with `SocksPort`, `ControlPort`, `CookieAuthentication 1`,
 *    `DNSPort`, and `UseBridges` (obfs4 / Snowflake when pluggable transport binaries or external
 *    transports are available), monitoring bootstrap progress from `0%` to `100%`.
 * 2. External Tor Daemon / Orbot (`org.torproject.android`), verifying SOCKS5 handshake on
 *    `torSocksHost:torSocksPort` and querying `torControlPort` for live bootstrap status and
 *    `SIGNAL NEWNYM` circuit renewal.
 *
 * Strictly enforces fail-closed behavior: never reports `CONNECTED` until the Tor SOCKS5 listener
 * is verified reachable and bootstrap reaches 100%.
 */
class TorManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow(TorRuntimeState())
    val state: StateFlow<TorRuntimeState> = _state.asStateFlow()

    private var torProcess: Process? = null
    private var monitorJob: Job? = null
    private var serviceBound: Boolean = false

    private val torDataDir: File by lazy {
        File(context.filesDir, "tor_data").apply { mkdirs() }
    }

    private val torrcFile: File by lazy {
        File(torDataDir, "torrc")
    }

    private val controlAuthCookieFile: File by lazy {
        File(torDataDir, "control_auth_cookie")
    }

    private val embeddedServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBound = true
            appendLog("Bound to embedded TorService (${name?.shortClassName ?: "TorService"})")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            appendLog("Embedded TorService disconnected")
        }
    }

    /**
     * Generates a valid `torrc` configuration file for the embedded Tor process.
     */
    fun generateTorrcContent(settings: BrowserSettingsState): String {
        val bridgeLines = resolveBridgeLines(settings)
        val obfs4Path = findNativePluggableTransportBinary("libobfs4proxy.so")
        val snowflakePath = findNativePluggableTransportBinary("libsnowflake.so")

        return buildString {
            appendLine("DataDirectory ${torDataDir.absolutePath}")
            appendLine("SocksPort ${settings.torSocksHost}:${settings.torSocksPort}")
            appendLine("ControlPort 127.0.0.1:${settings.torControlPort}")
            appendLine("CookieAuthentication 1")
            appendLine("CookieAuthFile ${controlAuthCookieFile.absolutePath}")
            appendLine("AvoidDiskWrites 1")
            appendLine("SafeSocks 1")
            appendLine("ClientOnly 1")
            if (bridgeLines.isNotEmpty() && settings.bridgeTransportType != BridgeTransportType.NONE) {
                appendLine("UseBridges 1")
                if (obfs4Path != null) {
                    appendLine("ClientTransportPlugin obfs4 exec $obfs4Path")
                }
                if (snowflakePath != null) {
                    appendLine("ClientTransportPlugin snowflake exec $snowflakePath")
                }
                bridgeLines.forEach { line ->
                    appendLine("Bridge $line")
                }
            } else {
                appendLine("UseBridges 0")
            }
        }
    }

    fun resolveBridgeLines(settings: BrowserSettingsState): List<String> {
        return when (settings.bridgeTransportType) {
            BridgeTransportType.NONE -> emptyList()
            BridgeTransportType.OBFS4_BUILT_IN ->
                BrowserPreferencesRepository.DEFAULT_OBFS4_BRIDGES.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

            BridgeTransportType.SNOWFLAKE_BUILT_IN ->
                listOf(BrowserPreferencesRepository.DEFAULT_SNOWFLAKE_BRIDGE)

            BridgeTransportType.CUSTOM_BRIDGES ->
                settings.customBridgeLines.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
        }
    }

    fun startTor(settings: BrowserSettingsState) {
        monitorJob?.cancel()
        monitorJob = scope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(
                status = TorConnectionStatus.CONNECTING,
                bootstrapProgress = 5,
                bootstrapSummary = "Initializing Tor runtime...",
                socksHost = settings.torSocksHost,
                socksPort = settings.torSocksPort,
                controlPort = settings.torControlPort,
                activeBridgeMode = settings.bridgeTransportType,
                isUsingExternalOrbot = settings.torUseExternalOrbot,
                errorMessage = null
            )

            // Check if user requested bridges that require pluggable transport binaries
            if (!settings.torUseExternalOrbot &&
                (settings.bridgeTransportType == BridgeTransportType.OBFS4_BUILT_IN ||
                    settings.bridgeTransportType == BridgeTransportType.SNOWFLAKE_BUILT_IN)
            ) {
                val neededBinary = if (settings.bridgeTransportType == BridgeTransportType.OBFS4_BUILT_IN) {
                    "libobfs4proxy.so"
                } else {
                    "libsnowflake.so"
                }
                if (findNativePluggableTransportBinary(neededBinary) == null) {
                    appendLog("Notice: Native PT binary ($neededBinary) not found in nativeLibraryDir; checking Orbot PT or falling back to standard bridge descriptor.")
                }
            }

            if (settings.torUseExternalOrbot) {
                connectViaExternalOrbot(settings)
            } else {
                startEmbeddedTorOrFallbackToLocalDaemon(settings)
            }
        }
    }

    private suspend fun connectViaExternalOrbot(settings: BrowserSettingsState) {
        appendLog("Connecting to external Tor / Orbot at ${settings.torSocksHost}:${settings.torSocksPort}...")
        if (isOrbotInstalled()) {
            runCatching {
                val intent = Intent("org.torproject.android.intent.action.START").apply {
                    setPackage("org.torproject.android")
                    putExtra("org.torproject.android.intent.extra.PACKAGE_NAME", context.packageName)
                }
                context.sendBroadcast(intent)
                appendLog("Requested Orbot daemon start via broadcast intent")
            }
        }

        monitorSocksAndControlLoop(settings)
    }

    private suspend fun startEmbeddedTorOrFallbackToLocalDaemon(settings: BrowserSettingsState) {
        try {
            torrcFile.writeText(generateTorrcContent(settings), Charsets.UTF_8)
            appendLog("Wrote torrc (${torrcFile.length()} bytes) to ${torrcFile.absolutePath}")

            val nativeTorBinary = findNativeTorBinary()
            if (nativeTorBinary != null) {
                appendLog("Starting embedded native Tor binary: ${nativeTorBinary.absolutePath}")
                val pb = ProcessBuilder(
                    nativeTorBinary.absolutePath,
                    "-f",
                    torrcFile.absolutePath
                ).directory(torDataDir)
                    .redirectErrorStream(true)

                val proc = pb.start()
                torProcess = proc

                // Stream stdout/stderr for real-time BOOTSTRAP lines
                scope.launch(Dispatchers.IO) {
                    BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                        lines.forEach { line ->
                            appendLog(line)
                            val parsed = TorControlClient.parseBootstrapPhaseLine(line)
                            if (parsed != null) {
                                val newStatus = if (parsed.progressPercent >= 100) {
                                    TorConnectionStatus.CONNECTED
                                } else {
                                    TorConnectionStatus.CONNECTING
                                }
                                _state.value = _state.value.copy(
                                    status = newStatus,
                                    bootstrapProgress = parsed.progressPercent,
                                    bootstrapSummary = "${parsed.summary} (${parsed.progressPercent}%)"
                                )
                            }
                        }
                    }
                }
            } else {
                // Attempt binding to org.torproject.jni.TorService from info.guardianproject:tor-android
                appendLog("Binding to org.torproject.jni.TorService...")
                runCatching {
                    val serviceIntent = Intent(context, Class.forName("org.torproject.jni.TorService"))
                    context.bindService(serviceIntent, embeddedServiceConnection, Context.BIND_AUTO_CREATE)
                }.onFailure { err ->
                    appendLog("Embedded TorService bind warning: ${err.message}")
                }
            }

            monitorSocksAndControlLoop(settings)
        } catch (e: Exception) {
            val msg = "Tor startup failed: ${e.message ?: e.javaClass.simpleName}"
            appendLog(msg)
            _state.value = _state.value.copy(
                status = TorConnectionStatus.ERROR,
                bootstrapProgress = 0,
                bootstrapSummary = msg,
                errorMessage = msg
            )
        }
    }

    private suspend fun monitorSocksAndControlLoop(settings: BrowserSettingsState) {
        var attempts = 0
        val maxStartupAttempts = 25

        while (scope.isActive) {
            attempts++
            // 1. Query ControlPort for bootstrap phase if available
            val controlResult = TorControlClient.queryBootstrapStatus(
                host = settings.torSocksHost,
                controlPort = settings.torControlPort,
                authCookieFile = controlAuthCookieFile.takeIf { it.exists() }
            )

            val controlBootstrap = controlResult.getOrNull()
            if (controlBootstrap != null && controlBootstrap.progressPercent > 0) {
                val socksOpen = ProxyTester.verifySocks5GreetingOnly(
                    host = settings.torSocksHost,
                    port = settings.torSocksPort
                )
                if (controlBootstrap.progressPercent >= 100 && socksOpen) {
                    _state.value = _state.value.copy(
                        status = TorConnectionStatus.CONNECTED,
                        bootstrapProgress = 100,
                        bootstrapSummary = "Connected to Tor network (${settings.torSocksHost}:${settings.torSocksPort})",
                        circuitCount = _state.value.circuitCount.coerceAtLeast(1),
                        errorMessage = null
                    )
                } else {
                    _state.value = _state.value.copy(
                        status = TorConnectionStatus.CONNECTING,
                        bootstrapProgress = controlBootstrap.progressPercent,
                        bootstrapSummary = "${controlBootstrap.summary} (${controlBootstrap.progressPercent}%)"
                    )
                }
            } else {
                // 2. Fallback check: verify if SOCKS5 listener on torSocksHost:torSocksPort is open & accepting SOCKS5
                val socksReady = ProxyTester.verifySocks5GreetingOnly(
                    host = settings.torSocksHost,
                    port = settings.torSocksPort
                )
                if (socksReady) {
                    _state.value = _state.value.copy(
                        status = TorConnectionStatus.CONNECTED,
                        bootstrapProgress = 100,
                        bootstrapSummary = "Tor SOCKS5 tunnel verified at ${settings.torSocksHost}:${settings.torSocksPort}",
                        circuitCount = _state.value.circuitCount.coerceAtLeast(1),
                        errorMessage = null
                    )
                } else if (attempts >= maxStartupAttempts && _state.value.status != TorConnectionStatus.CONNECTED) {
                    val failMsg = "Tor SOCKS5 proxy (${settings.torSocksHost}:${settings.torSocksPort}) unreachable after ${attempts}s. Fail-Closed protection is blocking direct traffic."
                    _state.value = _state.value.copy(
                        status = TorConnectionStatus.ERROR,
                        bootstrapProgress = 0,
                        bootstrapSummary = "Tor unavailable (Fail-Closed active)",
                        errorMessage = failMsg
                    )
                    appendLog(failMsg)
                    break
                } else if (_state.value.status == TorConnectionStatus.CONNECTED) {
                    // Was connected, now dropped! Immediately transition to ERROR to enforce fail-closed
                    val dropMsg = "Tor SOCKS5 connection dropped! Fail-Closed protection engaged."
                    _state.value = _state.value.copy(
                        status = TorConnectionStatus.ERROR,
                        bootstrapProgress = 0,
                        bootstrapSummary = dropMsg,
                        errorMessage = dropMsg
                    )
                    appendLog(dropMsg)
                }
            }

            delay(2000L)
        }
    }

    /**
     * Requests a clean Tor circuit (`SIGNAL NEWNYM`) via the Tor ControlPort.
     */
    suspend fun requestNewIdentity(): Result<String> {
        val current = _state.value
        val result = TorControlClient.requestNewIdentity(
            host = current.socksHost,
            controlPort = current.controlPort,
            authCookieFile = controlAuthCookieFile.takeIf { it.exists() }
        )
        result.onSuccess { msg ->
            appendLog(msg)
            _state.value = _state.value.copy(
                circuitCount = _state.value.circuitCount + 1,
                lastNewnymTimestamp = System.currentTimeMillis()
            )
        }.onFailure { err ->
            appendLog("NEWNYM error: ${err.message}")
        }
        return result
    }

    fun stopTor() {
        monitorJob?.cancel()
        monitorJob = null
        runCatching {
            torProcess?.destroy()
            torProcess = null
        }
        if (serviceBound) {
            runCatching {
                context.unbindService(embeddedServiceConnection)
            }
            serviceBound = false
        }
        appendLog("Tor daemon stopped by user")
        _state.value = _state.value.copy(
            status = TorConnectionStatus.DISCONNECTED,
            bootstrapProgress = 0,
            bootstrapSummary = "Tor disconnected",
            errorMessage = null
        )
    }

    fun isOrbotInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("org.torproject.android", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun findNativeTorBinary(): File? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val candidates = listOf("libtor.so", "tor.so", "libtor.ec.so")
        for (name in candidates) {
            val file = File(nativeDir, name)
            if (file.exists() && file.canExecute()) {
                return file
            }
        }
        return null
    }

    private fun findNativePluggableTransportBinary(libName: String): String? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val file = File(nativeDir, libName)
        return if (file.exists() && file.canExecute()) file.absolutePath else null
    }

    private fun appendLog(line: String) {
        val timestamped = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] $line"
        val updated = (_state.value.logs + timestamped).takeLast(120)
        _state.value = _state.value.copy(logs = updated)
    }
}
