package com.nofence.browser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nofence.browser.settings.BrowserSettingsState
import com.nofence.browser.settings.CookiePolicy
import com.nofence.browser.settings.DohMode
import com.nofence.browser.settings.SearchEngine
import com.nofence.browser.ui.theme.CharcoalSurface
import com.nofence.browser.ui.theme.CyberCyan
import com.nofence.browser.ui.theme.ElevatedCharcoal
import com.nofence.browser.ui.theme.ObsidianBlack
import com.nofence.browser.ui.theme.StatusFailClosedError
import com.nofence.browser.ui.theme.TextPrimary
import com.nofence.browser.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySecuritySettingsScreen(
    settings: BrowserSettingsState,
    onUpdatePrivacy: (
        javascriptEnabled: Boolean?,
        blockPopups: Boolean?,
        httpsOnlyMode: Boolean?,
        trackerProtectionStrict: Boolean?,
        disableWebRtc: Boolean?,
        disableIpv6: Boolean?,
        preventDnsLeaks: Boolean?,
        resistFingerprinting: Boolean?,
        cookiePolicy: CookiePolicy?,
        dohMode: DohMode?,
        dohServerUri: String?,
        searchEngine: SearchEngine?,
        restoreSession: Boolean?,
        clearOnExit: Boolean?
    ) -> Unit,
    onClearAllBrowsingData: () -> Unit,
    onBack: () -> Unit
) {
    var dohUriInput by remember(settings.dohServerUri) { mutableStateOf(settings.dohServerUri) }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                title = { Text("Privacy, DNS & Security Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Core Anti-Tracking & Network Hardening Toggles
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("GeckoView Privacy & Leak Protection", style = MaterialTheme.typography.titleLarge)

                    SettingToggleRow(
                        title = "HTTPS-Only / HTTPS-First Mode",
                        subtitle = "Automatically upgrade cleartext HTTP navigations to HTTPS (except .onion hidden services).",
                        checked = settings.httpsOnlyMode,
                        onCheckedChange = { onUpdatePrivacy(null, null, it, null, null, null, null, null, null, null, null, null, null, null) }
                    )

                    SettingToggleRow(
                        title = "Strict Enhanced Tracking Protection",
                        subtitle = "Block cross-site trackers, cryptominers, fingerprinters, and social tracking scripts in GeckoView.",
                        checked = settings.trackerProtectionStrict,
                        onCheckedChange = { onUpdatePrivacy(null, null, null, it, null, null, null, null, null, null, null, null, null, null) }
                    )

                    SettingToggleRow(
                        title = "Disable WebRTC (Prevent STUN/ICE IP Leaks)",
                        subtitle = "Sets media.peerconnection.enabled=false so websites cannot discover your local or public UDP IP address outside the proxy tunnel.",
                        checked = settings.disableWebRtc,
                        onCheckedChange = { onUpdatePrivacy(null, null, null, null, it, null, null, null, null, null, null, null, null, null) }
                    )

                    SettingToggleRow(
                        title = "Disable IPv6 Leak Vectors",
                        subtitle = "Sets network.dns.disableIPv6=true to prevent IPv6 traffic from bypassing IPv4 SOCKS5/HTTP proxies.",
                        checked = settings.disableIpv6,
                        onCheckedChange = { onUpdatePrivacy(null, null, null, null, null, it, null, null, null, null, null, null, null, null) }
                    )

                    SettingToggleRow(
                        title = "Prevent DNS Leaks & Disable Speculative Prefetch",
                        subtitle = "Enforces network.proxy.socks_remote_dns=true and disables DNS/link prefetching.",
                        checked = settings.preventDnsLeaks,
                        onCheckedChange = { onUpdatePrivacy(null, null, null, null, null, null, it, null, null, null, null, null, null, null) }
                    )

                    SettingToggleRow(
                        title = "Resist Browser Fingerprinting",
                        subtitle = "Enables Gecko privacy.resistFingerprinting to spoof canvas, timer precision, and hardware metrics.",
                        checked = settings.resistFingerprinting,
                        onCheckedChange = { onUpdatePrivacy(null, null, null, null, null, null, null, it, null, null, null, null, null, null) }
                    )

                    SettingToggleRow(
                        title = "Enable JavaScript",
                        subtitle = "Disable JavaScript for maximum security on untrusted sites (may break modern dynamic web apps).",
                        checked = settings.javascriptEnabled,
                        onCheckedChange = { onUpdatePrivacy(it, null, null, null, null, null, null, null, null, null, null, null, null, null) }
                    )

                    SettingToggleRow(
                        title = "Block Pop-up Windows",
                        subtitle = "Prevent sites from opening unrequested background tabs or pop-up windows.",
                        checked = settings.blockPopups,
                        onCheckedChange = { onUpdatePrivacy(null, it, null, null, null, null, null, null, null, null, null, null, null, null) }
                    )

                    SettingToggleRow(
                        title = "Restore Open Tabs on Startup",
                        subtitle = "Save and restore standard tabs across restarts (Private tabs are never written to disk).",
                        checked = settings.restoreSessionOnStartup,
                        onCheckedChange = { onUpdatePrivacy(null, null, null, null, null, null, null, null, null, null, null, null, it, null) }
                    )
                }
            }

            // Cookie & Site Data Policy
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Cookie & Cross-Site Storage Policy", style = MaterialTheme.typography.titleLarge)
                    CookiePolicy.entries.forEach { policy ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = settings.cookiePolicy == policy,
                                onClick = { onUpdatePrivacy(null, null, null, null, null, null, null, null, policy, null, null, null, null, null) }
                            )
                            Text(policy.label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        }
                    }
                }
            }

            // DNS-over-HTTPS (Trusted Recursive Resolver) Settings
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("DNS-over-HTTPS (DoH / Gecko TRR)", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "When using Tor mode, DNS is resolved anonymously through the Tor network via SOCKS5 remote DNS. In Direct or HTTP proxy modes, DoH encrypts DNS queries.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    DohMode.entries.forEach { mode ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = settings.dohMode == mode,
                                onClick = { onUpdatePrivacy(null, null, null, null, null, null, null, null, null, mode, null, null, null, null) }
                            )
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        }
                    }
                    OutlinedTextField(
                        value = dohUriInput,
                        onValueChange = { dohUriInput = it },
                        label = { Text("DoH Resolver Endpoint URI") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onUpdatePrivacy(null, null, null, null, null, null, null, null, null, null, dohUriInput, null, null, null)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                    ) {
                        Text("Save DoH Endpoint")
                    }
                }
            }

            // Default Search Engine
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Default Search Engine", style = MaterialTheme.typography.titleLarge)
                    SearchEngine.entries.forEach { engine ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = settings.searchEngine == engine,
                                onClick = { onUpdatePrivacy(null, null, null, null, null, null, null, null, null, null, null, engine, null, null) }
                            )
                            Text(engine.displayName, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        }
                    }
                }
            }

            // Clear Browsing Data Action
            Button(
                onClick = onClearAllBrowsingData,
                colors = ButtonDefaults.buttonColors(containerColor = StatusFailClosedError),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear All Browsing History, Cookies, DOM Storage & Cache")
            }

            // Remaining Privacy Limitations Disclosure (Mandatory Requirement #6)
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCharcoal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Documented Technical & Privacy Limitations",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyberCyan
                    )
                    Text(
                        text = "1. Not Official Tor Browser: While NoFence enforces GeckoView privacy.resistFingerprinting, WebRTC disablement, and SOCKS5 remote DNS, it does not include Tor Browser's full C++ patchset (such as dynamic letterbox window sizing or circuit isolation per first-party domain).
" +
                            "2. WebRTC: Disabled by default via media.peerconnection.enabled=false. If re-enabled by the user, UDP STUN traffic may bypass SOCKS5/HTTP proxies.
" +
                            "3. Traffic Outside GeckoView: NoFence routes GeckoView sessions and BrowserDownloadManager streams through the configured Tor/Proxy endpoint. External Android apps or intents opened outside NoFence are not routed unless a device-wide VPN (such as Orbot VPN mode) is active.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
