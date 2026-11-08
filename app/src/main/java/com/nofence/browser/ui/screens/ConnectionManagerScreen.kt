package com.nofence.browser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nofence.browser.networking.CoordinatedConnectionState
import com.nofence.browser.settings.BrowserSettingsState
import com.nofence.browser.settings.ConnectionMode
import com.nofence.browser.ui.components.ConnectionStatusPill
import com.nofence.browser.ui.theme.CharcoalSurface
import com.nofence.browser.ui.theme.CyberCyan
import com.nofence.browser.ui.theme.ElevatedCharcoal
import com.nofence.browser.ui.theme.ObsidianBlack
import com.nofence.browser.ui.theme.SubtleBorder
import com.nofence.browser.ui.theme.TextPrimary
import com.nofence.browser.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionManagerScreen(
    connectionState: CoordinatedConnectionState,
    settings: BrowserSettingsState,
    onSelectConnectionMode: (ConnectionMode) -> Unit,
    onOpenTorSettings: () -> Unit,
    onOpenProxySettings: () -> Unit,
    onToggleTorConnect: () -> Unit,
    onRequestNewIdentity: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                title = { Text("Connection & Routing Manager") },
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
            ConnectionStatusPill(
                connectionState = connectionState,
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Select Routing Mode",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )

            ConnectionModeOptionCard(
                title = "1. Tor Connection (Highest Anonymity)",
                subtitle = "Routes all GeckoView tabs and DNS queries through Tor SOCKS5 (${settings.torSocksHost}:${settings.torSocksPort}). Supports obfs4 & Snowflake bridges and Fail-Closed blocking.",
                icon = Icons.Default.VpnLock,
                selected = settings.connectionMode == ConnectionMode.TOR,
                onClick = { onSelectConnectionMode(ConnectionMode.TOR) },
                actionButtonLabel = "Configure Tor & Bridges",
                onActionClick = onOpenTorSettings
            )

            ConnectionModeOptionCard(
                title = "2. Custom Proxy Connection (SOCKS5 / HTTP / HTTPS)",
                subtitle = "Routes all browser traffic through your selected proxy profile (${connectionState.proxyState.activeProfile?.name ?: "None selected"}). Enforces remote proxy DNS and automatic fallback.",
                icon = Icons.Default.Router,
                selected = settings.connectionMode == ConnectionMode.CUSTOM_PROXY,
                onClick = { onSelectConnectionMode(ConnectionMode.CUSTOM_PROXY) },
                actionButtonLabel = "Manage Proxy Profiles",
                onActionClick = onOpenProxySettings
            )

            ConnectionModeOptionCard(
                title = "3. Direct Connection",
                subtitle = "Connects directly using device network (${connectionState.networkStatus.interfaceSummary}). Tracker blocking, HTTPS-Only, and DoH remain active, but your IP address is visible to websites.",
                icon = Icons.Default.Public,
                selected = settings.connectionMode == ConnectionMode.DIRECT,
                onClick = { onSelectConnectionMode(ConnectionMode.DIRECT) },
                actionButtonLabel = null,
                onActionClick = null
            )

            // Live Diagnostics Summary Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Hub, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Live Routing Diagnostics", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "• Physical Interface: ${connectionState.networkStatus.interfaceSummary}
" +
                            "• Selected Mode: ${connectionState.mode.name}
" +
                            "• Browsing Allowed (Fail-Closed Gate): ${connectionState.isBrowsingAllowed}
" +
                            "• Tor Status: ${connectionState.torState.status.name} (${connectionState.torState.bootstrapProgress}%)
" +
                            "• Custom Proxy Status: ${connectionState.proxyState.status.name} (${connectionState.proxyState.statusMessage})
" +
                            "• Remote Proxy DNS Enforced: ${settings.preventDnsLeaks}",
                        style = MaterialTheme.typography.labelMedium
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onToggleTorConnect, modifier = Modifier.weight(1f)) {
                            Text("Start / Stop Tor")
                        }
                        OutlinedButton(onClick = onRequestNewIdentity, modifier = Modifier.weight(1f)) {
                            Text("New Tor Circuit")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionModeOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    actionButtonLabel: String?,
    onActionClick: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) ElevatedCharcoal else CharcoalSurface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) CyberCyan else SubtleBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(icon, contentDescription = null, tint = if (selected) CyberCyan else TextSecondary)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberCyan)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        if (actionButtonLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = onActionClick) {
                Text(actionButtonLabel)
            }
        }
    }
}
