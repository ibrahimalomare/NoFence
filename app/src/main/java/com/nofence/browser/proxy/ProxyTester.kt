package com.nofence.browser.proxy

import com.nofence.browser.privacy.ProxyCredentials
import com.nofence.browser.storage.ProxyProfileEntity
import com.nofence.browser.storage.ProxyProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.net.ssl.SSLSocketFactory

data class ProxyDiagnosticResult(
    val success: Boolean,
    val latencyMs: Long,
    val protocolVerified: String,
    val remoteDnsSupported: Boolean,
    val message: String
)

/**
 * Performs real network socket verification and protocol handshakes for SOCKS5, HTTP, and HTTPS CONNECT proxies.
 * Does NOT simulate or mock connectivity: opens an actual TCP socket and validates RFC 1928 / RFC 1929 (SOCKS5)
 * or HTTP CONNECT / Proxy-Authorization responses.
 */
object ProxyTester {

    private const val DEFAULT_TIMEOUT_MS = 6500

    suspend fun testProxyProfile(
        profile: ProxyProfileEntity,
        credentials: ProxyCredentials? = null,
        probeHost: String = "check.torproject.org",
        probePort: Int = 443
    ): ProxyDiagnosticResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            when (profile.protocol) {
                ProxyProtocol.SOCKS5 -> verifySocks5Handshake(
                    proxyHost = profile.host,
                    proxyPort = profile.port,
                    credentials = credentials,
                    remoteDns = profile.remoteDns,
                    probeHost = probeHost,
                    probePort = probePort,
                    startTime = startTime
                )

                ProxyProtocol.HTTP -> verifyHttpConnectHandshake(
                    proxyHost = profile.host,
                    proxyPort = profile.port,
                    useTlsToProxy = false,
                    credentials = credentials,
                    probeHost = probeHost,
                    probePort = probePort,
                    startTime = startTime
                )

                ProxyProtocol.HTTPS_CONNECT -> verifyHttpConnectHandshake(
                    proxyHost = profile.host,
                    proxyPort = profile.port,
                    useTlsToProxy = true,
                    credentials = credentials,
                    probeHost = probeHost,
                    probePort = probePort,
                    startTime = startTime
                )
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            ProxyDiagnosticResult(
                success = false,
                latencyMs = elapsed,
                protocolVerified = profile.protocol.name,
                remoteDnsSupported = false,
                message = "Connection failed: ${e.javaClass.simpleName} - ${e.message ?: "Unreachable"}"
            )
        }
    }

    /**
     * Quick check if a local or remote SOCKS5 endpoint (e.g., Tor 127.0.0.1:9050) accepts SOCKS5 method negotiation.
     */
    suspend fun verifySocks5GreetingOnly(
        host: String,
        port: Int,
        timeoutMs: Int = 2500
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                val out = socket.getOutputStream()
                val input = socket.getInputStream()
                // SOCKS5 greeting: VER=0x05, NMETHODS=0x01, METHOD=0x00 (No Auth)
                out.write(byteArrayOf(0x05, 0x01, 0x00))
                out.flush()
                val response = ByteArray(2)
                val read = input.read(response)
                read == 2 && response[0] == 0x05.toByte() && response[1] != 0xFF.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun verifySocks5Handshake(
        proxyHost: String,
        proxyPort: Int,
        credentials: ProxyCredentials?,
        remoteDns: Boolean,
        probeHost: String,
        probePort: Int,
        startTime: Long
    ): ProxyDiagnosticResult {
        Socket().use { socket ->
            socket.soTimeout = DEFAULT_TIMEOUT_MS
            socket.connect(InetSocketAddress(proxyHost, proxyPort), DEFAULT_TIMEOUT_MS)

            val out: OutputStream = socket.getOutputStream()
            val input: InputStream = socket.getInputStream()

            val hasCredentials = credentials != null && credentials.username.isNotBlank()
            if (hasCredentials) {
                // Offer No-Auth (0x00) and Username/Password (0x02)
                out.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                out.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            out.flush()

            val greetingReply = ByteArray(2)
            if (input.readFully(greetingReply) != 2 || greetingReply[0] != 0x05.toByte()) {
                return ProxyDiagnosticResult(
                    success = false,
                    latencyMs = System.currentTimeMillis() - startTime,
                    protocolVerified = "SOCKS5",
                    remoteDnsSupported = false,
                    message = "Invalid SOCKS5 greeting header from $proxyHost:$proxyPort"
                )
            }

            val selectedMethod = greetingReply[1].toInt() and 0xFF
            if (selectedMethod == 0xFF) {
                return ProxyDiagnosticResult(
                    success = false,
                    latencyMs = System.currentTimeMillis() - startTime,
                    protocolVerified = "SOCKS5",
                    remoteDnsSupported = false,
                    message = "SOCKS5 server rejected authentication methods"
                )
            }

            if (selectedMethod == 0x02) {
                if (credentials == null) {
                    return ProxyDiagnosticResult(
                        success = false,
                        latencyMs = System.currentTimeMillis() - startTime,
                        protocolVerified = "SOCKS5",
                        remoteDnsSupported = false,
                        message = "SOCKS5 server requires username/password authentication"
                    )
                }
                val userBytes = credentials.username.toByteArray(StandardCharsets.UTF_8)
                val passBytes = credentials.password.toByteArray(StandardCharsets.UTF_8)
                val authPacket = ByteArray(3 + userBytes.size + passBytes.size)
                authPacket[0] = 0x01 // RFC 1929 subnegotiation version 1
                authPacket[1] = userBytes.size.toByte()
                System.arraycopy(userBytes, 0, authPacket, 2, userBytes.size)
                authPacket[2 + userBytes.size] = passBytes.size.toByte()
                System.arraycopy(passBytes, 0, authPacket, 3 + userBytes.size, passBytes.size)
                out.write(authPacket)
                out.flush()

                val authReply = ByteArray(2)
                if (input.readFully(authReply) != 2 || authReply[1] != 0x00.toByte()) {
                    return ProxyDiagnosticResult(
                        success = false,
                        latencyMs = System.currentTimeMillis() - startTime,
                        protocolVerified = "SOCKS5 (RFC 1929 Auth)",
                        remoteDnsSupported = false,
                        message = "SOCKS5 username/password authentication failed"
                    )
                }
            }

            // Optionally test ATYP=0x03 (DOMAINNAME) CONNECT if probeHost is non-empty
            if (probeHost.isNotBlank()) {
                val hostBytes = probeHost.toByteArray(StandardCharsets.US_ASCII)
                val connectReq = ByteArray(4 + 1 + hostBytes.size + 2)
                connectReq[0] = 0x05 // VER
                connectReq[1] = 0x01 // CMD = CONNECT
                connectReq[2] = 0x00 // RSV
                connectReq[3] = 0x03 // ATYP = DOMAINNAME (Remote DNS resolution at proxy)
                connectReq[4] = hostBytes.size.toByte()
                System.arraycopy(hostBytes, 0, connectReq, 5, hostBytes.size)
                connectReq[5 + hostBytes.size] = ((probePort shr 8) and 0xFF).toByte()
                connectReq[6 + hostBytes.size] = (probePort and 0xFF).toByte()

                out.write(connectReq)
                out.flush()

                val respHeader = ByteArray(4)
                val readBytes = input.readFully(respHeader)
                val latency = System.currentTimeMillis() - startTime
                if (readBytes == 4 && respHeader[0] == 0x05.toByte() && respHeader[1] == 0x00.toByte()) {
                    return ProxyDiagnosticResult(
                        success = true,
                        latencyMs = latency,
                        protocolVerified = "SOCKS5 + Remote DNS (ATYP 0x03)",
                        remoteDnsSupported = true,
                        message = "SOCKS5 handshake and remote DNS tunnel to $probeHost:$probePort succeeded (${latency}ms)"
                    )
                } else {
                    // Handshake succeeded even if target probe was unreachable/filtered
                    val repCode = if (readBytes >= 2) respHeader[1].toInt() and 0xFF else -1
                    return ProxyDiagnosticResult(
                        success = true,
                        latencyMs = latency,
                        protocolVerified = "SOCKS5 Handshake Verified",
                        remoteDnsSupported = remoteDns,
                        message = "SOCKS5 proxy reachable (${latency}ms, target probe code=$repCode)"
                    )
                }
            }

            val latency = System.currentTimeMillis() - startTime
            return ProxyDiagnosticResult(
                success = true,
                latencyMs = latency,
                protocolVerified = "SOCKS5",
                remoteDnsSupported = remoteDns,
                message = "SOCKS5 handshake verified (${latency}ms)"
            )
        }
    }

    private fun verifyHttpConnectHandshake(
        proxyHost: String,
        proxyPort: Int,
        useTlsToProxy: Boolean,
        credentials: ProxyCredentials?,
        probeHost: String,
        probePort: Int,
        startTime: Long
    ): ProxyDiagnosticResult {
        val rawSocket = Socket()
        rawSocket.soTimeout = DEFAULT_TIMEOUT_MS
        rawSocket.connect(InetSocketAddress(proxyHost, proxyPort), DEFAULT_TIMEOUT_MS)

        val activeSocket: Socket = if (useTlsToProxy) {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            factory.createSocket(rawSocket, proxyHost, proxyPort, true).apply {
                soTimeout = DEFAULT_TIMEOUT_MS
            }
        } else {
            rawSocket
        }

        activeSocket.use { socket ->
            val out = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))

            val authHeader = if (credentials != null && credentials.username.isNotBlank()) {
                val token = Base64.getEncoder().encodeToString(
                    "${credentials.username}:${credentials.password}".toByteArray(StandardCharsets.UTF_8)
                )
                "Proxy-Authorization: Basic $token
"
            } else {
                ""
            }

            val request = buildString {
                append("CONNECT $probeHost:$probePort HTTP/1.1
")
                append("Host: $probeHost:$probePort
")
                append("Proxy-Connection: keep-alive
")
                append(authHeader)
                append("
")
            }

            out.write(request.toByteArray(StandardCharsets.US_ASCII))
            out.flush()

            val statusLine = reader.readLine() ?: ""
            val latency = System.currentTimeMillis() - startTime
            val protoLabel = if (useTlsToProxy) "HTTPS CONNECT" else "HTTP CONNECT"

            return when {
                statusLine.contains(" 200 ") -> ProxyDiagnosticResult(
                    success = true,
                    latencyMs = latency,
                    protocolVerified = protoLabel,
                    remoteDnsSupported = true,
                    message = "$protoLabel tunnel established (${latency}ms): $statusLine"
                )

                statusLine.contains(" 407 ") -> ProxyDiagnosticResult(
                    success = false,
                    latencyMs = latency,
                    protocolVerified = protoLabel,
                    remoteDnsSupported = true,
                    message = "Proxy requires authentication (HTTP 407 Proxy Authentication Required)"
                )

                statusLine.startsWith("HTTP/") -> ProxyDiagnosticResult(
                    success = true,
                    latencyMs = latency,
                    protocolVerified = protoLabel,
                    remoteDnsSupported = true,
                    message = "$protoLabel endpoint responded (${latency}ms): $statusLine"
                )

                else -> ProxyDiagnosticResult(
                    success = false,
                    latencyMs = latency,
                    protocolVerified = protoLabel,
                    remoteDnsSupported = false,
                    message = "Unexpected response from proxy: ${statusLine.ifBlank { "Empty response" }}"
                )
            }
        }
    }

    private fun InputStream.readFully(buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = this.read(buffer, offset, buffer.size - offset)
            if (read == -1) break
            offset += read
        }
        return offset
    }
}
