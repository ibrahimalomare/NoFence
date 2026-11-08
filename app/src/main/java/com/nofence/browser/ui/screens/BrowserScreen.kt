package com.nofence.browser.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nofence.browser.browser.BrowserTabModel
import com.nofence.browser.networking.CoordinatedConnectionState
import com.nofence.browser.networking.OverallRoutingStatus
import com.nofence.browser.settings.BrowserSettingsState
import com.nofence.browser.storage.BookmarkEntity
import com.nofence.browser.ui.components.FindInPageBar
import com.nofence.browser.ui.components.SslSecurityDialog
import com.nofence.browser.ui.theme.CharcoalSurface
import com.nofence.browser.ui.theme.CyberCyan
import com.nofence.browser.ui.theme.ElevatedCharcoal
import com.nofence.browser.ui.theme.ObsidianBlack
import com.nofence.browser.ui.theme.PrivatePurple
import com.nofence.browser.ui.theme.StatusConnecting
import com.nofence.browser.ui.theme.StatusDirectWarning
import com.nofence.browser.ui.theme.StatusFailClosedError
import com.nofence.browser.ui.theme.StatusTorProtected
import com.nofence.browser.ui.theme.SubtleBorder
import com.nofence.browser.ui.theme.TextPrimary
import com.nofence.browser.ui.theme.TextSecondary
import org.mozilla.geckoview.GeckoView

@Composable
fun BrowserScreen(
    activeTab: BrowserTabModel?,
    totalTabCount: Int,
    connectionState: CoordinatedConnectionState,
    settings: BrowserSettingsState,
    bookmarks: List<BookmarkEntity>,
    onNavigateToUrlOrSearch: (String) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenTabManager: () -> Unit,
    onOpenConnectionManager: () -> Unit,
    onOpenTorSettings: () -> Unit,
    onOpenProxySettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenPrivacySettings: () -> Unit,
    onToggleTorConnect: () -> Unit,
    onRequestNewIdentity: () -> Unit,
    onOpenFindInPage: () -> Unit,
    onCloseFindInPage: () -> Unit,
    onFindInPageQuery: (String, Boolean) -> Unit,
    onClearHostData: (String) -> Unit,
    onClearAllBrowsingData: () -> Unit
) {
    val context = LocalContext.current
    var addressInput by remember(activeTab?.id, activeTab?.url) {
        mutableStateOf(
            if (activeTab?.url == "about:blank") "" else (activeTab?.url ?: "")
        )
    }
    var isEditingAddress by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSslDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeTab?.url) {
        if (!isEditingAddress) {
            addressInput = if (activeTab?.url == "about:blank") "" else (activeTab?.url ?: "")
        }
    }

    if (showSslDialog && activeTab != null) {
        SslSecurityDialog(
            url = activeTab.url,
            securityInfo = activeTab.securityInfo,
            blockedTrackersCount = activeTab.blockedTrackersCount,
            blockedDomains = activeTab.blockedTrackerDomains,
            onClearHostData = {
                if (activeTab.securityInfo.host.isNotBlank()) {
                    onClearHostData(activeTab.securityInfo.host)
                }
            },
            onDismiss = { showSslDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // Find-in-page bar (when active)
        if (activeTab?.findInPageState?.isVisible == true) {
            FindInPageBar(
                state = activeTab.findInPageState,
                onQueryChange = { q -> onFindInPageQuery(q, true) },
                onNext = { onFindInPageQuery(activeTab.findInPageState.query, true) },
                onPrevious = { onFindInPageQuery(activeTab.findInPageState.query, false) },
                onClose = onCloseFindInPage
            )
        }

        // Main Viewport: Home Dashboard, Fail-Closed Interstitial, or Mozilla GeckoView Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                activeTab == null || activeTab.url == "about:blank" -> {
                    BrowserHomeScreen(
                        connectionState = connectionState,
                        settings = settings,
                        bookmarks = bookmarks,
                        isPrivateTab = activeTab?.isPrivate == true,
                        onSubmitUrlOrSearch = onNavigateToUrlOrSearch,
                        onOpenConnectionManager = onOpenConnectionManager,
                        onToggleTorConnect = onToggleTorConnect,
                        onRequestNewIdentity = onRequestNewIdentity,
                        onOpenTorSettings = onOpenTorSettings,
                        onOpenProxySettings = onOpenProxySettings,
                        onOpenPrivacySettings = onOpenPrivacySettings
                    )
                }

                activeTab.failClosedBlockedUrl != null || !connectionState.isBrowsingAllowed -> {
                    FailClosedOverlayScreen(
                        blockedUrl = activeTab.failClosedBlockedUrl ?: activeTab.url,
                        errorMessage = activeTab.pageErrorDescription ?: connectionState.bannerSubtitle,
                        connectionState = connectionState,
                        onOpenConnectionManager = onOpenConnectionManager,
                        onRetry = onReloadOrStop
                    )
                }

                else -> {
                    // Real Mozilla GeckoView rendering surface
                    AndroidView(
                        factory = { ctx ->
                            GeckoView(ctx).apply {
                                setSession(activeTab.session)
                            }
                        },
                        update = { geckoView ->
                            if (geckoView.session !== activeTab.session) {
                                geckoView.releaseSession()
                                geckoView.setSession(activeTab.session)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Loading progress bar at bottom edge of viewport
            if (activeTab?.isLoading == true) {
                LinearProgressIndicator(
                    progress = { (activeTab.progress / 100f).coerceIn(0.05f, 1f) },
                    color = if (activeTab.isPrivate) PrivatePurple else CyberCyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                )
            }
        }

        // Bottom Browser Toolbar (Dark-First Ergonomic Mobile Layout)
        Surface(
            color = CharcoalSurface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SubtleBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Row 1: Connection Status Dot + HTTPS Lock + Address/Search Bar + Reload
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val statusColor = when (connectionState.overallStatus) {
                        OverallRoutingStatus.PROTECTED_TOR,
                        OverallRoutingStatus.PROTECTED_PROXY -> StatusTorProtected
                        OverallRoutingStatus.CONNECTING -> StatusConnecting
                        OverallRoutingStatus.FAIL_CLOSED_BLOCKED -> StatusFailClosedError
                        OverallRoutingStatus.DIRECT_ACTIVE -> StatusDirectWarning
                    }

                    // Connection Mode Shield Button
                    IconButton(onClick = onOpenConnectionManager) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Connection Routing Mode",
                            tint = statusColor
                        )
                    }

                    // SSL / Security Indicator Button
                    if (activeTab != null && activeTab.url != "about:blank") {
                        IconButton(onClick = { showSslDialog = true }) {
                            Icon(
                                imageVector = if (activeTab.securityInfo.isSecure) Icons.Default.Lock else Icons.Default.Warning,
                                contentDescription = "HTTPS Security Info",
                                tint = if (activeTab.securityInfo.isSecure) StatusTorProtected else StatusConnecting,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = {
                            isEditingAddress = true
                            addressInput = it
                        },
                        placeholder = {
                            Text(
                                text = if (activeTab?.isPrivate == true) "Private search or URL..." else "Search or enter address...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                isEditingAddress = false
                                if (addressInput.isNotBlank()) {
                                    onNavigateToUrlOrSearch(addressInput)
                                }
                            }
                        ),
                        trailingIcon = {
                            if (isEditingAddress && addressInput.isNotEmpty()) {
                                IconButton(onClick = { addressInput = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear URL")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    )

                    IconButton(onClick = onReloadOrStop) {
                        Icon(
                            imageVector = if (activeTab?.isLoading == true) Icons.Default.Close else Icons.Default.Refresh,
                            contentDescription = "Reload or Stop",
                            tint = TextPrimary
                        )
                    }
                }

                // Row 2: Navigation Controls (Back, Forward, Home, New Identity, Tabs, Overflow Menu)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onGoBack,
                        enabled = activeTab?.canGoBack == true
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }

                    IconButton(
                        onClick = onGoForward,
                        enabled = activeTab?.canGoForward == true
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }

                    IconButton(
                        onClick = { onNavigateToUrlOrSearch("about:blank") }
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Browser Home")
                    }

                    IconButton(
                        onClick = onToggleBookmark,
                        enabled = activeTab != null && activeTab.url != "about:blank"
                    ) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = "Bookmark Page")
                    }

                    // Tab Switcher Counter Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElevatedCharcoal)
                            .border(
                                1.dp,
                                if (activeTab?.isPrivate == true) PrivatePurple else CyberCyan,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(onClick = onOpenTabManager)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Tab,
                                contentDescription = "Tabs",
                                modifier = Modifier.size(16.dp),
                                tint = if (activeTab?.isPrivate == true) PrivatePurple else CyberCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$totalTabCount",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    // Overflow Menu
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Browser Menu")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Connection & Routing (${connectionState.mode.name})") },
                                leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenConnectionManager()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("New Tor Circuit / Identity") },
                                leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onRequestNewIdentity()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (activeTab?.isDesktopMode == true) "Switch to Mobile Site" else "Desktop Site Mode")
                                },
                                leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onToggleDesktopMode()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Find in Page") },
                                leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenFindInPage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Current Link") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    val urlToShare = activeTab?.url ?: ""
                                    if (urlToShare.isNotBlank() && urlToShare != "about:blank") {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, urlToShare)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share link"))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bookmarks") },
                                leadingIcon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenBookmarks()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Browsing History") },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenHistory()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Downloads") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenDownloads()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Privacy & Security Settings") },
                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenPrivacySettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Burn / Clear Browsing Data Now") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = StatusFailClosedError) },
                                onClick = {
                                    showOverflowMenu = false
                                    onClearAllBrowsingData()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FailClosedOverlayScreen(
    blockedUrl: String,
    errorMessage: String,
    connectionState: CoordinatedConnectionState,
    onOpenConnectionManager: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = StatusFailClosedError,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Fail-Closed Protection Blocked Navigation",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "NoFence prevented loading "$blockedUrl" because ${connectionState.mode.name} mode is selected but the tunnel is not verified connected. NoFence never silently downgrades to a direct connection.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.labelMedium,
            color = StatusFailClosedError
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onOpenConnectionManager,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Text("Configure Tor / Proxy")
            }
            OutlinedButton(onClick = onRetry) {
                Text("Retry Connection")
            }
        }
    }
}
