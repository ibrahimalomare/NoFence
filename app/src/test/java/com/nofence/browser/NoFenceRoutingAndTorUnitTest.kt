package com.nofence.browser

import com.nofence.browser.networking.GeckoConfigGenerator
import com.nofence.browser.settings.BrowserSettingsState
import com.nofence.browser.settings.ConnectionMode
import com.nofence.browser.storage.ProxyProfileEntity
import com.nofence.browser.storage.ProxyProtocol
import com.nofence.browser.tor.TorControlClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoFenceRoutingAndTorUnitTest {

    @Test
    fun `tor mode fails closed to port 1 blackhole when Tor is disconnected`() {
        val settings = BrowserSettingsState(
            connectionMode = ConnectionMode.TOR,
            torStrictFailClosed = true,
            torSocksHost = "127.0.0.1",
            torSocksPort = 9050
        )

        val endpoint = GeckoConfigGenerator.resolveEndpoint(
            settings = settings,
            isTorReady = false,
            activeProxy = null,
            isProxyVerified = false
        )

        assertTrue("Expected fail-closed to be active when Tor is not ready", endpoint.failClosedActive)
        assertEquals(1, endpoint.proxyTypeInt)
        assertEquals("127.0.0.1", endpoint.socksHost)
        assertEquals(1, endpoint.socksPort)
        assertTrue(endpoint.socksRemoteDns)
    }

    @Test
    fun `tor mode routes through configured SOCKS5 port when Tor is connected`() {
        val settings = BrowserSettingsState(
            connectionMode = ConnectionMode.TOR,
            torStrictFailClosed = true,
            torSocksHost = "127.0.0.1",
            torSocksPort = 9050
        )

        val endpoint = GeckoConfigGenerator.resolveEndpoint(
            settings = settings,
            isTorReady = true,
            activeProxy = null,
            isProxyVerified = false
        )

        assertFalse(endpoint.failClosedActive)
        assertEquals(1, endpoint.proxyTypeInt)
        assertEquals("127.0.0.1", endpoint.socksHost)
        assertEquals(9050, endpoint.socksPort)
        assertTrue(endpoint.socksRemoteDns)
    }

    @Test
    fun `custom proxy mode fails closed when proxy is unverified`() {
        val settings = BrowserSettingsState(
            connectionMode = ConnectionMode.CUSTOM_PROXY,
            proxyFailClosed = true
        )
        val profile = ProxyProfileEntity(
            id = 1L,
            name = "Test SOCKS5",
            protocol = ProxyProtocol.SOCKS5,
            host = "10.0.0.5",
            port = 1080,
            remoteDns = true
        )

        val endpoint = GeckoConfigGenerator.resolveEndpoint(
            settings = settings,
            isTorReady = false,
            activeProxy = profile,
            isProxyVerified = false
        )

        assertTrue("Expected fail-closed when custom proxy is unverified", endpoint.failClosedActive)
        assertEquals("127.0.0.1", endpoint.socksHost)
        assertEquals(1, endpoint.socksPort)
    }

    @Test
    fun `gecko yaml config disables WebRTC and enforces remote DNS in Tor mode`() {
        val settings = BrowserSettingsState(
            connectionMode = ConnectionMode.TOR,
            disableWebRtc = true,
            preventDnsLeaks = true
        )
        val yaml = GeckoConfigGenerator.buildYamlString(
            settings = settings,
            isTorReady = true,
            activeProxy = null,
            isProxyVerified = false
        )

        assertTrue(yaml.contains("network.proxy.type: 1"))
        assertTrue(yaml.contains("network.proxy.socks_port: 9050"))
        assertTrue(yaml.contains("network.proxy.socks_remote_dns: true"))
        assertTrue(yaml.contains("network.proxy.failover_direct: false"))
        assertTrue(yaml.contains("media.peerconnection.enabled: false"))
        assertTrue(yaml.contains("network.dns.blockDotOnion: false"))
    }

    @Test
    fun `tor control client parses bootstrap phase line accurately`() {
        val sampleLine = "250-status/bootstrap-phase=NOTICE BOOTSTRAP PROGRESS=85 TAG=ap_conn_done SUMMARY="Connected to a relay to build circuits""
        val parsed = TorControlClient.parseBootstrapPhaseLine(sampleLine)

        assertNotNull(parsed)
        assertEquals(85, parsed?.progressPercent)
        assertEquals("ap_conn_done", parsed?.tag)
        assertEquals("Connected to a relay to build circuits", parsed?.summary)
    }
}
