package com.nofence.browser.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nofence.browser.app.NoFenceViewModel
import com.nofence.browser.ui.components.SitePermissionPromptDialog
import com.nofence.browser.ui.screens.BookmarksScreen
import com.nofence.browser.ui.screens.BrowserScreen
import com.nofence.browser.ui.screens.ConnectionManagerScreen
import com.nofence.browser.ui.screens.DownloadsScreen
import com.nofence.browser.ui.screens.HistoryScreen
import com.nofence.browser.ui.screens.PrivacySecuritySettingsScreen
import com.nofence.browser.ui.screens.ProxySettingsScreen
import com.nofence.browser.ui.screens.TabManagerScreen
import com.nofence.browser.ui.screens.TorSettingsScreen
import com.nofence.browser.ui.theme.ObsidianBlack

object NoFenceRoutes {
    const val BROWSER = "browser"
    const val TABS = "tabs"
    const val CONNECTION_MANAGER = "connection_manager"
    const val TOR_SETTINGS = "tor_settings"
    const val PROXY_SETTINGS = "proxy_settings"
    const val BOOKMARKS = "bookmarks"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val PRIVACY_SETTINGS = "privacy_settings"
}

@Composable
fun NoFenceApp(viewModel: NoFenceViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by viewModel.settingsState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val tabs by viewModel.tabsState.collectAsState()
    val selectedTabId by viewModel.selectedTabId.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val bookmarks by viewModel.bookmarksState.collectAsState()
    val history by viewModel.historyState.collectAsState()
    val downloads by viewModel.downloadsState.collectAsState()
    val proxyProfiles by viewModel.proxyProfilesState.collectAsState()
    val pendingFileUpload by viewModel.pendingFileUpload.collectAsState()
    val pendingPermission by viewModel.pendingSitePermission.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    // File upload picker launcher for GeckoView PromptDelegate.FilePrompt
    val singleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.handleFileUploadResult(arrayOf(uri))
        } else {
            viewModel.dismissFileUpload()
        }
    }

    val multiFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.handleFileUploadResult(uris.toTypedArray())
        } else {
            viewModel.dismissFileUpload()
        }
    }

    LaunchedEffect(pendingFileUpload) {
        val req = pendingFileUpload ?: return@LaunchedEffect
        if (req.isMultiple) {
            multiFileLauncher.launch(req.mimeTypes)
        } else {
            singleFileLauncher.launch(req.mimeTypes)
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    pendingPermission?.let { req ->
        SitePermissionPromptDialog(
            request = req,
            onDecision = { granted -> viewModel.respondToSitePermission(granted) }
        )
    }

    Scaffold(
        containerColor = ObsidianBlack,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = NoFenceRoutes.BROWSER
            ) {
                composable(NoFenceRoutes.BROWSER) {
                    BrowserScreen(
                        activeTab = activeTab,
                        totalTabCount = tabs.size,
                        connectionState = connectionState,
                        settings = settings,
                        bookmarks = bookmarks,
                        onNavigateToUrlOrSearch = viewModel::navigateToUrlOrSearch,
                        onGoBack = viewModel::goBack,
                        onGoForward = viewModel::goForward,
                        onReloadOrStop = viewModel::reloadOrStop,
                        onToggleDesktopMode = viewModel::toggleDesktopMode,
                        onToggleBookmark = viewModel::toggleBookmarkForActiveTab,
                        onOpenTabManager = { navController.navigate(NoFenceRoutes.TABS) },
                        onOpenConnectionManager = { navController.navigate(NoFenceRoutes.CONNECTION_MANAGER) },
                        onOpenTorSettings = { navController.navigate(NoFenceRoutes.TOR_SETTINGS) },
                        onOpenProxySettings = { navController.navigate(NoFenceRoutes.PROXY_SETTINGS) },
                        onOpenBookmarks = { navController.navigate(NoFenceRoutes.BOOKMARKS) },
                        onOpenHistory = { navController.navigate(NoFenceRoutes.HISTORY) },
                        onOpenDownloads = { navController.navigate(NoFenceRoutes.DOWNLOADS) },
                        onOpenPrivacySettings = { navController.navigate(NoFenceRoutes.PRIVACY_SETTINGS) },
                        onToggleTorConnect = viewModel::toggleTorConnection,
                        onRequestNewIdentity = viewModel::requestNewTorIdentity,
                        onOpenFindInPage = viewModel::openFindInPage,
                        onCloseFindInPage = viewModel::closeFindInPage,
                        onFindInPageQuery = viewModel::findInPage,
                        onClearHostData = { _ ->
                            viewModel.clearAllBrowsingData(
                                clearHistory = false,
                                clearCookiesAndSiteData = true,
                                clearCache = true
                            )
                        },
                        onClearAllBrowsingData = {
                            viewModel.clearAllBrowsingData(
                                clearHistory = true,
                                clearCookiesAndSiteData = true,
                                clearCache = true
                            )
                        }
                    )
                }

                composable(NoFenceRoutes.TABS) {
                    TabManagerScreen(
                        tabs = tabs,
                        selectedTabId = selectedTabId,
                        onSelectTab = viewModel::selectTab,
                        onCloseTab = viewModel::closeTab,
                        onNewTab = { isPrivate -> viewModel.createNewTab("about:blank", isPrivate) },
                        onCloseAllTabs = viewModel::closeAllTabs,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(NoFenceRoutes.CONNECTION_MANAGER) {
                    ConnectionManagerScreen(
                        connectionState = connectionState,
                        settings = settings,
                        onSelectConnectionMode = viewModel::switchConnectionMode,
                        onOpenTorSettings = { navController.navigate(NoFenceRoutes.TOR_SETTINGS) },
                        onOpenProxySettings = { navController.navigate(NoFenceRoutes.PROXY_SETTINGS) },
                        onToggleTorConnect = viewModel::toggleTorConnection,
                        onRequestNewIdentity = viewModel::requestNewTorIdentity,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(NoFenceRoutes.TOR_SETTINGS) {
                    TorSettingsScreen(
                        torState = connectionState.torState,
                        settings = settings,
                        onToggleTorConnect = viewModel::toggleTorConnection,
                        onRequestNewIdentity = viewModel::requestNewTorIdentity,
                        onSaveTorSettings = { failClosed, useOrbot, host, socksPort, ctrlPort, bridgeType, lines ->
                            viewModel.updateTorSettings(
                                strictFailClosed = failClosed,
                                useExternalOrbot = useOrbot,
                                socksHost = host,
                                socksPort = socksPort,
                                controlPort = ctrlPort,
                                bridgeTransportType = bridgeType,
                                customBridgeLines = lines
                            )
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(NoFenceRoutes.PROXY_SETTINGS) {
                    ProxySettingsScreen(
                        proxyState = connectionState.proxyState,
                        profiles = proxyProfiles,
                        settings = settings,
                        onSaveProfile = viewModel::saveAndVerifyProxyProfile,
                        onSelectAndVerifyProfile = viewModel::selectAndTestProxyProfile,
                        onDeleteProfile = viewModel::deleteProxyProfile,
                        onUpdateProxyOptions = viewModel::updateProxyOptions,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(NoFenceRoutes.BOOKMARKS) {
                    BookmarksScreen(
                        bookmarks = bookmarks,
                        onSelectBookmark = viewModel::navigateToUrlOrSearch,
                        onDeleteBookmark = viewModel::deleteBookmark,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(NoFenceRoutes.HISTORY) {
                    HistoryScreen(
                        historyItems = history,
                        onSelectUrl = viewModel::navigateToUrlOrSearch,
                        onDeleteHistoryItem = viewModel::deleteHistoryItem,
                        onClearAllHistory = viewModel::clearAllHistory,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(NoFenceRoutes.DOWNLOADS) {
                    DownloadsScreen(
                        downloads = downloads,
                        onEnqueueDownloadUrl = viewModel::startManualDownload,
                        onDeleteDownload = viewModel::deleteDownload,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(NoFenceRoutes.PRIVACY_SETTINGS) {
                    PrivacySecuritySettingsScreen(
                        settings = settings,
                        onUpdatePrivacy = viewModel::updatePrivacySettings,
                        onClearAllBrowsingData = {
                            viewModel.clearAllBrowsingData(
                                clearHistory = true,
                                clearCookiesAndSiteData = true,
                                clearCache = true,
                                clearDownloads = false
                            )
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
