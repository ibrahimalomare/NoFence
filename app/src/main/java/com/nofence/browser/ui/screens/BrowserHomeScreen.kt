package com.nofence.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nofence.browser.R
import com.nofence.browser.networking.CoordinatedConnectionState
import com.nofence.browser.networking.OverallRoutingStatus
import com.nofence.browser.settings.BrowserSettingsState
import com.nofence.browser.settings.ConnectionMode
import com.nofence.browser.storage.BookmarkEntity
import com.nofence.browser.tor.TorConnectionStatus
import com.nofence.browser.ui.components.ConnectionStatusPill
import com.nofence.browser.ui.theme.CharcoalSurface
import com.nofence.browser.ui.theme.CyberCyan
import com.nofence.browser.ui.theme.ElevatedCharcoal
import com.nofence.browser.ui.theme.ObsidianBlack
import com.nofence.browser.ui.theme.PrivatePurple
import com.nofence.browser.ui.theme.StatusFailClosedError
import com.nofence.browser.ui.theme.StatusTorProtected
import com.nofence.browser.ui.theme.SubtleBorder
import com.nofence.browser.ui.theme.TextPrimary
import com.nofence.browser.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowserHomeScreen(
    connectionState: CoordinatedConnectionState,
    settings: BrowserSettingsState,
    bookmarks: List<BookmarkEntity>,
    isPrivateTab: Boolean,
    onSubmitUrlOrSearch: (String) -> Unit,
    onOpenConnectionManager: () -> Unit,
    onToggleTorConnect: () -> Unit,
    onRequestNewIdentity: () -> Unit,
    onOpenTorSettings: () -> Unit,
    onOpenProxySettings: () -> Unit,
    onOpenPrivacySettings: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Brand Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VpnLock,
                    contentDescription = null,
                    tint = if (isPrivateTab) PrivatePurple else CyberCyan,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isPrivateTab) "NoFence • Private Tab" else "NoFence Browser",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Mozilla GeckoView • Anti-Censorship Routing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // Live Connection Status Banner
        ConnectionStatusPill(
            connectionState = connectionState,
            onClick = onOpenConnectionManager,
            modifier = Modifier.fillMaxWidth()
        )

        // Quick Tor / Circuit Action Row when in Tor mode
        if (settings.connectionMode == ConnectionMode.TOR) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isTorActive = connectionState.torState.status == TorConnectionStatus.CONNECTED ||
                    connectionState.torState.status == TorConnectionStatus.CONNECTING

                Button(
                    onClick = onToggleTorConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTorActive) StatusFailClosedError else StatusTorProtected
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isTorActive) "Disconnect Tor" else "Connect Tor")
                }

                OutlinedButton(
                    onClick = onRequestNewIdentity,
                    enabled = connectionState.torState.status == TorConnectionStatus.CONNECTED,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Identity (NEWNYM)")
                }
            }
        }

        // Search or Enter Address Box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text("Search ${settings.searchEngine.displayName} or enter URL / .onion...")
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    if (searchQuery.isNotBlank()) {
                        onSubmitUrlOrSearch(searchQuery)
                    }
                }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Quick Check & Censorship-Resistant Sites
        Text(
            text = "Verification & Censorship-Resistant Endpoints",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val quickLinks = listOf(
                "Check Tor Status" to "https://check.torproject.org",
                "DuckDuckGo" to "https://duckduckgo.com",
                "DuckDuckGo .onion" to "https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion",
                "EFF Cover Your Tracks" to "https://coveryourtracks.eff.org",
                "DNS Leak Test" to "https://www.dnsleaktest.com",
                "BrowserLeaks IP" to "https://browserleaks.com/ip"
            )
            quickLinks.forEach { (title, url) ->
                AssistChip(
                    onClick = { onSubmitUrlOrSearch(url) },
                    label = { Text(title) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                )
            }
        }

        // Saved Bookmarks Section
        if (bookmarks.isNotEmpty()) {
            Text(
                text = "Saved Bookmarks",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bookmarks.take(6).forEach { bm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CharcoalSurface)
                            .border(1.dp, SubtleBorder, RoundedCornerShape(12.dp))
                            .clickable { onSubmitUrlOrSearch(bm.url) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bm.title, style = MaterialTheme.typography.titleMedium)
                            Text(bm.url, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Active Protection Overview Cards
        Card(
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CyberCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Active Privacy & Anti-Leak Hardening",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = "• Mode: ${settings.connectionMode.name} (Fail-Closed=${if (settings.connectionMode == ConnectionMode.TOR) settings.torStrictFailClosed else settings.proxyFailClosed})
" +
                        "• Remote Proxy DNS: ${settings.preventDnsLeaks} | DoH: ${settings.dohMode.label}
" +
                        "• WebRTC Leak Prevention: ${if (settings.disableWebRtc) "Disabled (Protected)" else "Enabled"}
" +
                        "• HTTPS-Only Mode: ${settings.httpsOnlyMode} | Fingerprinting Resistance: ${settings.resistFingerprinting}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenTorSettings, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tor & Bridges")
                    }
                    OutlinedButton(onClick = onOpenProxySettings, modifier = Modifier.weight(1f)) {
                        Text("Proxy Profiles")
                    }
                }
                OutlinedButton(onClick = onOpenPrivacySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Privacy, Cookies & DNS Settings")
                }
            }
        }

        // Tor Browser Distinction Notice (Mandatory Requirement #3)
        Card(
            colors = CardDefaults.cardColors(containerColor = ElevatedCharcoal),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Security Architecture Notice: NoFence vs. Official Tor Browser",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyberCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.tor_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}
