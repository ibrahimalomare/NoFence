package com.nofence.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nofence.browser.browser.FindInPageState
import com.nofence.browser.browser.PendingSitePermissionRequest
import com.nofence.browser.browser.SslSecurityInfo
import com.nofence.browser.networking.CoordinatedConnectionState
import com.nofence.browser.networking.OverallRoutingStatus
import com.nofence.browser.ui.theme.CharcoalSurface
import com.nofence.browser.ui.theme.CyberCyan
import com.nofence.browser.ui.theme.ElevatedCharcoal
import com.nofence.browser.ui.theme.StatusConnecting
import com.nofence.browser.ui.theme.StatusDirectWarning
import com.nofence.browser.ui.theme.StatusFailClosedError
import com.nofence.browser.ui.theme.StatusTorProtected
import com.nofence.browser.ui.theme.TextPrimary
import com.nofence.browser.ui.theme.TextSecondary

@Composable
fun ConnectionStatusPill(
    connectionState: CoordinatedConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeColor = when (connectionState.overallStatus) {
        OverallRoutingStatus.PROTECTED_TOR,
        OverallRoutingStatus.PROTECTED_PROXY -> StatusTorProtected
        OverallRoutingStatus.CONNECTING -> StatusConnecting
        OverallRoutingStatus.FAIL_CLOSED_BLOCKED -> StatusFailClosedError
        OverallRoutingStatus.DIRECT_ACTIVE -> StatusDirectWarning
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(ElevatedCharcoal)
            .border(1.dp, badgeColor.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = if (connectionState.overallStatus == OverallRoutingStatus.FAIL_CLOSED_BLOCKED) {
                Icons.Default.Warning
            } else {
                Icons.Default.Shield
            },
            contentDescription = "Connection Security Status",
            tint = badgeColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = connectionState.bannerTitle,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = connectionState.bannerSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            if (connectionState.overallStatus == OverallRoutingStatus.CONNECTING) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (connectionState.torState.bootstrapProgress / 100f).coerceIn(0.05f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = StatusConnecting
                )
            }
        }
    }
}

@Composable
fun FindInPageBar(
    state: FindInPageState,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = CharcoalSurface,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Find in page...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${state.currentMatch}/${state.totalMatches}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous Match")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next Match")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Find in Page")
            }
        }
    }
}

@Composable
fun SslSecurityDialog(
    url: String,
    securityInfo: SslSecurityInfo,
    blockedTrackersCount: Int,
    blockedDomains: List<String>,
    onClearHostData: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (securityInfo.isSecure) Icons.Default.Lock else Icons.Default.Warning,
                contentDescription = null,
                tint = if (securityInfo.isSecure) StatusTorProtected else StatusFailClosedError
            )
        },
        title = {
            Text(
                text = if (securityInfo.isSecure) "HTTPS Connection Secure" else "Connection Not Secure (Cleartext / Unverified)"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("URL: $url", style = MaterialTheme.typography.bodyMedium)
                if (securityInfo.host.isNotBlank()) {
                    Text("Host: ${securityInfo.host}", style = MaterialTheme.typography.bodyMedium)
                }
                if (securityInfo.protocolVersion.isNotBlank()) {
                    Text("Protocol: ${securityInfo.protocolVersion}", style = MaterialTheme.typography.bodyMedium)
                }
                if (securityInfo.issuerOrganization.isNotBlank()) {
                    Text("Certificate Issuer: ${securityInfo.issuerOrganization}", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Trackers Blocked on Page: $blockedTrackersCount",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyberCyan
                )
                if (blockedDomains.isNotEmpty()) {
                    Text(
                        text = blockedDomains.take(8).joinToString("
• ", prefix = "• "),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = {
                onClearHostData()
                onDismiss()
            }) {
                Text("Clear Site Cookies & Storage")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun SitePermissionPromptDialog(
    request: PendingSitePermissionRequest,
    onDecision: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDecision(false) },
        icon = {
            Icon(Icons.Default.Shield, contentDescription = null, tint = CyberCyan)
        },
        title = {
            Text("Site Permission Request")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Origin: ${request.uri}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Requests access to: ${request.permissionNames.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Privacy Warning: Granting hardware or geolocation permissions can reveal real device identifiers or physical location even when routed over Tor or a custom proxy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusConnecting
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onDecision(true) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Text("Allow Once")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onDecision(false) }) {
                Text("Deny (Recommended)")
            }
        }
    )
}
