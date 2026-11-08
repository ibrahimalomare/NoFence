package com.nofence.browser.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nofence.browser.R
import com.nofence.browser.settings.BridgeTransportType
import com.nofence.browser.settings.BrowserSettingsState
import com.nofence.browser.tor.TorConnectionStatus
import com.nofence.browser.tor.TorRuntimeState
import com.nofence.browser.ui.theme.CharcoalSurface
import com.nofence.browser.ui.theme.CyberCyan
import com.nofence.browser.ui.theme.ElevatedCharcoal
import com.nofence.browser.ui.theme.ObsidianBlack
import com.nofence.browser.ui.theme.StatusFailClosedError
import com.nofence.browser.ui.theme.StatusTorProtected
import com.nofence.browser.ui.theme.TextPrimary
import com.nofence.browser.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorSettingsScreen(
    torState: TorRuntimeState,
    settings: BrowserSettingsState,
    onToggleTorConnect: () -> Unit,
    onRequestNewIdentity: () -> Unit,
    onSaveTorSettings: (
        strictFailClosed: Boolean,
        useExternalOrbot: Boolean,
        socksHost: String,
        socksPort: Int,
        controlPort: Int,
        bridgeType: BridgeTransportType,
        customBridgeLines: String
    ) -> Unit,
    onBack: () -> Unit
) {
    var strictFailClosed by remember(settings) { mutableStateOf(settings.torStrictFailClosed) }
    var useExternalOrbot by remember(settings) { mutableStateOf(settings.torUseExternalOrbot) }
    var socksHost by remember(settings) { mutableStateOf(settings.torSocksHost) }
    var socksPortText by remember(settings) { mutableStateOf(settings.torSocksPort.toString()) }
    var controlPortText by remember(settings) { mutableStateOf(settings.torControlPort.toString()) }
    var bridgeType by remember(settings) { mutableStateOf(settings.bridgeTransportType) }
    var customBridgeLines by remember(settings) { mutableStateOf(settings.customBridgeLines) }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                title = { Text("Tor & Anti-Censorship Bridges") },
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
            // Live Tor Status & Bootstrap Progress Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Tor Status: ${torState.status.name} (${torState.bootstrapProgress}%)",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (torState.status == TorConnectionStatus.CONNECTED) StatusTorProtected else CyberCyan
                    )
                    Text(
                        text = torState.bootstrapSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    LinearProgressIndicator(
                        progress = { (torState.bootstrapProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = if (torState.status == TorConnectionStatus.CONNECTED) StatusTorProtected else CyberCyan
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val isRunning = torState.status == TorConnectionStatus.CONNECTED ||
                            torState.status == TorConnectionStatus.CONNECTING

                        Button(
                            onClick = onToggleTorConnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) StatusFailClosedError else StatusTorProtected
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isRunning) "Stop Tor" else "Connect Tor")
                        }

                        OutlinedButton(
                            onClick = onRequestNewIdentity,
                            enabled = torState.status == TorConnectionStatus.CONNECTED,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("New Identity (NEWNYM)")
                        }
                    }
                }
            }

            // Fail-Closed & Runtime Mode Switches
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strict Tor-Only Fail-Closed Mode", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Prevent all browsing and DNS requests when Tor is disconnected or bootstrapping.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(checked = strictFailClosed, onCheckedChange = { strictFailClosed = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Use External Orbot / Custom Tor Daemon", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Connect to an external Tor SOCKS5/ControlPort service (e.g., Guardian Project Orbot) instead of the embedded daemon.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(checked = useExternalOrbot, onCheckedChange = { useExternalOrbot = it })
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = socksHost,
                            onValueChange = { socksHost = it },
                            label = { Text("SOCKS5 Host") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                        OutlinedTextField(
                            value = socksPortText,
                            onValueChange = { socksPortText = it.filter(Char::isDigit) },
                            label = { Text("SOCKS Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = controlPortText,
                            onValueChange = { controlPortText = it.filter(Char::isDigit) },
                            label = { Text("Control Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Pluggable Transports & Bridges Configuration (obfs4 / Snowflake / Custom)
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Tor Bridges & Pluggable Transports",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "Use bridges when direct access to public Tor guard relays is blocked by ISP or national firewalls. Note: obfs4 and Snowflake require compatible PT client binaries or Orbot PT support.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    BridgeTransportType.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = bridgeType == option,
                                onClick = { bridgeType = option }
                            )
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                        }
                    }

                    if (bridgeType == BridgeTransportType.CUSTOM_BRIDGES ||
                        bridgeType == BridgeTransportType.OBFS4_BUILT_IN
                    ) {
                        OutlinedTextField(
                            value = customBridgeLines,
                            onValueChange = { customBridgeLines = it },
                            label = { Text("Bridge Descriptor Lines (one obfs4 / snowflake bridge per line)") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = {
                            onSaveTorSettings(
                                strictFailClosed,
                                useExternalOrbot,
                                socksHost.ifBlank { "127.0.0.1" },
                                socksPortText.toIntOrNull() ?: 9050,
                                controlPortText.toIntOrNull() ?: 9051,
                                bridgeType,
                                customBridgeLines
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Tor & Bridge Configuration")
                    }
                }
            }

            // Tor Bootstrap & ControlPort Logs
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCharcoal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Tor Daemon & ControlPort Log", style = MaterialTheme.typography.titleMedium)
                    val logText = if (torState.logs.isEmpty()) {
                        "No Tor daemon events logged yet."
                    } else {
                        torState.logs.takeLast(15).joinToString("
")
                    }
                    Text(
                        text = logText,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            // Mandatory Tor Browser Distinction Disclosure
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "How NoFence Tor Mode Differs from Official Tor Browser",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.tor_disclaimer),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
