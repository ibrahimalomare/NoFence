package com.nofence.browser.tor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

data class TorBootstrapInfo(
    val progressPercent: Int,
    val tag: String,
    val summary: String,
    val rawLine: String
)

/**
 * Pure Kotlin implementation of the Tor Control Protocol (TC / RFC-style line protocol on ControlPort).
 * Supports:
 * - Cookie authentication (`AUTHENTICATE <hex_cookie>`) and password authentication (`AUTHENTICATE "<password>"`)
 * - Bootstrap status queries (`GETINFO status/bootstrap-phase`)
 * - Circuit establishment verification (`GETINFO status/circuit-established`)
 * - Requesting a clean Tor identity / circuit (`SIGNAL NEWNYM`)
 * - Dynamic bridge & pluggable transport reconfiguration (`SETCONF UseBridges=1 Bridge="..."`)
 */
object TorControlClient {

    private const val CONTROL_TIMEOUT_MS = 3500

    /**
     * Parses a Tor `status/bootstrap-phase` response line such as:
     * `250-status/bootstrap-phase=NOTICE BOOTSTRAP PROGRESS=85 TAG=ap_conn_done SUMMARY="Connected to a relay to build circuits"`
     */
    fun parseBootstrapPhaseLine(line: String): TorBootstrapInfo? {
        if (!line.contains("BOOTSTRAP")) return null
        val progressRegex = Regex("PROGRESS=(\d+)")
        val tagRegex = Regex("TAG=([^\s]+)")
        val summaryRegex = Regex("SUMMARY="([^"]+)"")

        val progress = progressRegex.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        val tag = tagRegex.find(line)?.groupValues?.getOrNull(1) ?: "unknown"
        val summary = summaryRegex.find(line)?.groupValues?.getOrNull(1) ?: "Bootstrapping Tor..."

        return TorBootstrapInfo(
            progressPercent = progress.coerceIn(0, 100),
            tag = tag,
            summary = summary,
            rawLine = line
        )
    }

    suspend fun queryBootstrapStatus(
        host: String = "127.0.0.1",
        controlPort: Int = 9051,
        authCookieFile: File? = null,
        password: String = ""
    ): Result<TorBootstrapInfo> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.soTimeout = CONTROL_TIMEOUT_MS
                socket.connect(InetSocketAddress(host, controlPort), CONTROL_TIMEOUT_MS)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII)

                authenticate(reader, writer, authCookieFile, password)

                writer.write("GETINFO status/bootstrap-phase
")
                writer.flush()

                var bootstrapInfo: TorBootstrapInfo? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.contains("BOOTSTRAP")) {
                        bootstrapInfo = parseBootstrapPhaseLine(line)
                    }
                    if (line.startsWith("250 ") || line.startsWith("5")) {
                        break
                    }
                }

                writer.write("QUIT
")
                writer.flush()

                bootstrapInfo ?: TorBootstrapInfo(
                    progressPercent = 0,
                    tag = "unknown",
                    summary = "ControlPort responded without bootstrap phase",
                    rawLine = ""
                )
            }
        }
    }

    /**
     * Sends `SIGNAL NEWNYM` to the Tor ControlPort so Tor switches to clean circuits for new connections.
     */
    suspend fun requestNewIdentity(
        host: String = "127.0.0.1",
        controlPort: Int = 9051,
        authCookieFile: File? = null,
        password: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.soTimeout = CONTROL_TIMEOUT_MS
                socket.connect(InetSocketAddress(host, controlPort), CONTROL_TIMEOUT_MS)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII)

                authenticate(reader, writer, authCookieFile, password)

                writer.write("SIGNAL NEWNYM
")
                writer.flush()
                val response = reader.readLine() ?: throw IllegalStateException("No reply to SIGNAL NEWNYM")
                if (!response.startsWith("250")) {
                    throw IllegalStateException("Tor rejected SIGNAL NEWNYM: $response")
                }

                writer.write("QUIT
")
                writer.flush()
                "New Tor circuit and identity requested (250 OK)"
            }
        }
    }

    /**
     * Applies bridge lines dynamically over the Tor ControlPort (`SETCONF`).
     */
    suspend fun applyBridgesOverControlPort(
        host: String = "127.0.0.1",
        controlPort: Int = 9051,
        authCookieFile: File? = null,
        password: String = "",
        enableBridges: Boolean,
        bridgeLines: List<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.soTimeout = CONTROL_TIMEOUT_MS
                socket.connect(InetSocketAddress(host, controlPort), CONTROL_TIMEOUT_MS)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII)

                authenticate(reader, writer, authCookieFile, password)

                val command = if (!enableBridges || bridgeLines.isEmpty()) {
                    "SETCONF UseBridges=0
"
                } else {
                    val formattedBridges = bridgeLines
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .joinToString(" ") { "Bridge="${it.replace(""", "\"")}"" }
                    "SETCONF UseBridges=1 $formattedBridges
"
                }

                writer.write(command)
                writer.flush()
                val reply = reader.readLine() ?: throw IllegalStateException("No reply to SETCONF")
                if (!reply.startsWith("250")) {
                    throw IllegalStateException("SETCONF failed: $reply")
                }

                writer.write("QUIT
")
                writer.flush()
            }
        }
    }

    private fun authenticate(
        reader: BufferedReader,
        writer: OutputStreamWriter,
        authCookieFile: File?,
        password: String
    ) {
        val authCmd = when {
            authCookieFile != null && authCookieFile.exists() && authCookieFile.canRead() -> {
                val hexCookie = authCookieFile.readBytes().joinToString("") { "%02x".format(it) }
                "AUTHENTICATE $hexCookie
"
            }
            password.isNotEmpty() -> {
                "AUTHENTICATE "${password.replace(""", "\"")}"
"
            }
            else -> "AUTHENTICATE
"
        }
        writer.write(authCmd)
        writer.flush()
        val authReply = reader.readLine() ?: throw IllegalStateException("ControlPort closed during AUTHENTICATE")
        if (!authReply.startsWith("250")) {
            throw IllegalStateException("Tor ControlPort authentication failed: $authReply")
        }
    }
}
