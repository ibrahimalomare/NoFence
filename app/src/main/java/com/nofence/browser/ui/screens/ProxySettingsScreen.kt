package com.nofence.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.nofence.browser.proxy.ProxyConnectionStatus
import com.nofence.browser.proxy.ProxyManagerState
import com.nofence.browser.settings.BrowserSettingsState
import com.nofence.browser.storage.ProxyProfileEntity
import com.nofence.browser.storage.ProxyProtocol
import com.nofence.browser.ui.theme.CharcoalSurface
import com.nofence.browser.ui.theme.CyberCyan
import com.nofence.browser.ui.theme.ElevatedCharcoal
import com.nofence.browser.ui.theme.ObsidianBlack
import com.nofence.browser.ui.theme.StatusFailClosedError
import com.nofence.browser.ui.theme.StatusTorProtected
import com.nofence.browser.ui.theme.SubtleBorder
import com.nofence.browser.ui.theme.TextPrimary
import com.nofence.browser.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(
    proxyState: ProxyManagerState,
    profiles: List<ProxyProfileEntity>,
    settings: BrowserSettingsState,
    onSaveProfile: (
        name: String,
        protocol: ProxyProtocol,
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDns: Boolean,
        isFallback: Boolean,
        activateNow: Boolean
    ) -> Unit,
    onSelectAndVerifyProfile: (Long) -> Unit,
    onDeleteProfile: (ProxyProfileEntity) -> Unit,
    onUpdateProxyOptions: (Boolean?, Boolean?) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(ProxyProtocol.SOCKS5) }
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("1080") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var remoteDns by remember { mutableStateOf(true) }
    var isFallback by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                title = { Text("Custom Proxy Manager") },
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
            // Live Diagnostic Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCharcoal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Proxy Status: ${proxyState.status.name}",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (proxyState.status == ProxyConnectionStatus.CONNECTED) StatusTorProtected else CyberCyan
                    )
                    Text(
                        text = proxyState.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    proxyState.lastDiagnostic?.let { diag ->
                        Text(
                            text = "Verified Protocol: ${diag.protocolVerified} • Latency: ${diag.latencyMs}ms • Remote DNS: ${diag.remoteDnsSupported}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (diag.success) StatusTorProtected else StatusFailClosedError
                        )
                    }
                }
            }

            // Global Proxy Protection Options
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
                            Text("Fail-Closed Proxy Enforcement", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Block all tabs from loading if the selected proxy fails verification. Never fall back to a direct connection.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = settings.proxyFailClosed,
                            onCheckedChange = { onUpdateProxyOptions(it, null) }
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Fallback Proxy Switching", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "If the primary proxy fails, automatically test and switch to saved profiles marked as Fallback.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = settings.proxyAutoFallback,
                            onCheckedChange = { onUpdateProxyOptions(null, it) }
                        )
                    }
                }
            }

            // Add New Proxy Profile Form
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Add Proxy Profile", style = MaterialTheme.typography.titleLarge)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProxyProtocol.entries.forEach { proto ->
                            FilterChip(
                                selected = protocol == proto,
                                onClick = { protocol = proto },
                                label = { Text(proto.name) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Profile Name (e.g., My Mullvad SOCKS5 / Outline Proxy)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("Hostname or IP") },
                            singleLine = true,
                            modifier = Modifier.weight(1.8f)
                        )
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter(Char::isDigit) },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username (Optional)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password (Encrypted)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Route DNS through Proxy (Prevent DNS Leaks)", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = remoteDns, onCheckedChange = { remoteDns = it })
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Include in Automatic Fallback Pool", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = isFallback, onCheckedChange = { isFallback = it })
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (host.isNotBlank()) {
                                    onSaveProfile(
                                        name,
                                        protocol,
                                        host,
                                        portText.toIntOrNull() ?: 1080,
                                        username,
                                        password,
                                        remoteDns,
                                        isFallback,
                                        true
                                    )
                                    name = ""
                                    host = ""
                                    username = ""
                                    password = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save & Connect Now")
                        }

                        OutlinedButton(
                            onClick = {
                                if (host.isNotBlank()) {
                                    onSaveProfile(
                                        name,
                                        protocol,
                                        host,
                                        portText.toIntOrNull() ?: 1080,
                                        username,
                                        password,
                                        remoteDns,
                                        isFallback,
                                        false
                                    )
                                    name = ""
                                    host = ""
                                    username = ""
                                    password = ""
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Profile Only")
                        }
                    }
                }
            }

            // Saved Proxy Profiles List
            Text("Saved Proxy Profiles (${profiles.size})", style = MaterialTheme.typography.titleLarge)

            if (profiles.isEmpty()) {
                Text(
                    text = "No proxy profiles saved yet. Add a SOCKS5, HTTP, or HTTPS CONNECT proxy above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            } else {
                profiles.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CharcoalSurface)
                            .border(
                                width = if (item.isSelected) 2.dp else 1.dp,
                                color = if (item.isSelected) CyberCyan else SubtleBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.name} (${item.protocol.name})",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${item.host}:${item.port} • Remote DNS=${item.remoteDns} • Auth=${item.hasAuth}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            item.lastLatencyMs?.let { rtt ->
                                Text(
                                    text = "Last verified latency: ${rtt}ms",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StatusTorProtected
                                )
                            }
                        }
                        IconButton(onClick = { onSelectAndVerifyProfile(item.id) }) {
                            Icon(
                                Icons.Default.NetworkCheck,
                                contentDescription = "Test & Activate Proxy",
                                tint = CyberCyan
                            )
                        }
                        IconButton(onClick = { onDeleteProfile(item) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Profile",
                                tint = StatusFailClosedError
                            )
                        }
                    }
                }
            }
        }
    }
}
