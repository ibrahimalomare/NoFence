package com.nofence.browser.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nofence.browser.browser.BrowserTabModel
import com.nofence.browser.browser.PendingFileUploadRequest
import com.nofence.browser.browser.PendingSitePermissionRequest
import com.nofence.browser.networking.CoordinatedConnectionState
import com.nofence.browser.settings.BridgeTransportType
import com.nofence.browser.settings.BrowserSettingsState
import com.nofence.browser.settings.ConnectionMode
import com.nofence.browser.settings.CookiePolicy
import com.nofence.browser.settings.DohMode
import com.nofence.browser.settings.SearchEngine
import com.nofence.browser.storage.BookmarkEntity
import com.nofence.browser.storage.DownloadEntity
import com.nofence.browser.storage.HistoryEntity
import com.nofence.browser.storage.ProxyProfileEntity
import com.nofence.browser.storage.ProxyProtocol
import com.nofence.browser.tor.TorConnectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoRuntime

class NoFenceViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NoFenceApplication
    private val preferencesRepo = app.preferencesRepository
    private val connectionCoordinator = app.connectionCoordinator
    private val engineProvider = app.geckoEngineProvider
    private val tabManager = app.tabManager
    private val bookmarkDao = app.database.bookmarkDao()
    private val historyDao = app.database.historyDao()
    private val downloadManager = app.downloadManager
    private val proxyManager = app.proxyManager
    private val torManager = app.torManager

    val settingsState: StateFlow<BrowserSettingsState> = preferencesRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BrowserSettingsState())

    val connectionState: StateFlow<CoordinatedConnectionState> = connectionCoordinator.state

    val tabsState: StateFlow<List<BrowserTabModel>> = tabManager.tabs
    val selectedTabId: StateFlow<String?> = tabManager.selectedTabId

    val activeTab: StateFlow<BrowserTabModel?> = combine(
        tabManager.tabs,
        tabManager.selectedTabId
    ) { tabs, selectedId ->
        tabs.find { it.id == selectedId } ?: tabs.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bookmarksState: StateFlow<List<BookmarkEntity>> = bookmarkDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val historyState: StateFlow<List<HistoryEntity>> = historyDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val downloadsState: StateFlow<List<DownloadEntity>> = downloadManager.downloadsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val proxyProfilesState: StateFlow<List<ProxyProfileEntity>> = proxyManager.allProfilesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pendingFileUpload: StateFlow<PendingFileUploadRequest?> = tabManager.pendingFileUpload
    val pendingSitePermission: StateFlow<PendingSitePermissionRequest?> = tabManager.pendingSitePermission

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var runtimeRef: GeckoRuntime? = null

    init {
        viewModelScope.launch {
            val initialSettings = preferencesRepo.settingsFlow.first()
            val initialConn = connectionCoordinator.state.value
            val runtime = engineProvider.getOrCreateRuntime(initialSettings, initialConn)
            runtimeRef = runtime
            tabManager.updateSettings(initialSettings)
            tabManager.restoreTabsOrCreateDefault(
                runtime = runtime,
                restoreEnabled = initialSettings.restoreSessionOnStartup
            )

            // Observe settings and connection changes to hot-update GeckoView routing
            combine(preferencesRepo.settingsFlow, connectionCoordinator.state) { settings, conn ->
                settings to conn
            }.collect { (settings, conn) ->
                tabManager.updateSettings(settings)
                engineProvider.applyUpdatedSettingsAndRouting(settings, conn)
            }
        }
    }

    fun getGeckoRuntime(): GeckoRuntime {
        return runtimeRef ?: engineProvider.getOrCreateRuntime(
            settingsState.value,
            connectionState.value
        ).also { runtimeRef = it }
    }

    // --- Browser Navigation & Tab Actions ---

    fun navigateToUrlOrSearch(input: String) {
        tabManager.loadUrlOrSearchInActiveTab(input)
    }

    fun goBack() = tabManager.goBack()
    fun goForward() = tabManager.goForward()
    fun reloadOrStop() = tabManager.reloadOrStop()
    fun toggleDesktopMode() = tabManager.toggleDesktopMode()

    fun createNewTab(url: String = "about:blank", isPrivate: Boolean = false) {
        tabManager.createNewTab(getGeckoRuntime(), url, isPrivate, selectTab = true)
    }

    fun selectTab(tabId: String) = tabManager.selectTab(tabId)

    fun closeTab(tabId: String) = tabManager.closeTab(tabId, getGeckoRuntime())

    fun closeAllTabs(onlyPrivate: Boolean = false) {
        tabManager.closeAllTabs(onlyPrivate, getGeckoRuntime())
        showMessage(if (onlyPrivate) "Closed all private tabs" else "Closed all tabs")
    }

    fun openFindInPage() = tabManager.openFindInPage()
    fun closeFindInPage() = tabManager.closeFindInPage()
    fun findInPage(query: String, forward: Boolean = true) = tabManager.findInPage(query, forward)

    fun handleFileUploadResult(uris: Array<Uri>?) = tabManager.completePendingFileUpload(uris)
    fun dismissFileUpload() = tabManager.dismissPendingFileUpload()
    fun respondToSitePermission(granted: Boolean) = tabManager.respondToSitePermissionRequest(granted)

    // --- Connection Mode, Tor & Proxy Controls ---

    fun switchConnectionMode(mode: ConnectionMode) {
        viewModelScope.launch {
            preferencesRepo.setConnectionMode(mode)
            when (mode) {
                ConnectionMode.TOR -> {
                    if (torManager.state.value.status == TorConnectionStatus.DISCONNECTED) {
                        torManager.startTor(settingsState.value.copy(connectionMode = ConnectionMode.TOR))
                    }
                }
                ConnectionMode.CUSTOM_PROXY -> {
                    proxyManager.connectAndVerifySelectedProxy(settingsState.value.proxyAutoFallback)
                }
                ConnectionMode.DIRECT -> {
                    showMessage("Switched to Direct Connection (Not anonymized)")
                }
            }
        }
    }

    fun toggleTorConnection() {
        val status = torManager.state.value.status
        if (status == TorConnectionStatus.CONNECTED || status == TorConnectionStatus.CONNECTING) {
            torManager.stopTor()
            showMessage("Tor disconnected. Fail-Closed protection is blocking direct traffic.")
        } else {
            viewModelScope.launch {
                preferencesRepo.setConnectionMode(ConnectionMode.TOR)
                torManager.startTor(settingsState.value)
            }
        }
    }

    fun requestNewTorIdentity() {
        viewModelScope.launch {
            val result = torManager.requestNewIdentity()
            result.onSuccess { msg ->
                // Also clear session cookies/cache on circuit rotation if desired
                showMessage(msg)
            }.onFailure { err ->
                showMessage("Failed to request new circuit: ${err.message}")
            }
        }
    }

    fun updateTorSettings(
        strictFailClosed: Boolean? = null,
        useExternalOrbot: Boolean? = null,
        socksHost: String? = null,
        socksPort: Int? = null,
        controlPort: Int? = null,
        bridgeTransportType: BridgeTransportType? = null,
        customBridgeLines: String? = null
    ) {
        viewModelScope.launch {
            preferencesRepo.updateTorSettings(
                strictFailClosed = strictFailClosed,
                useExternalOrbot = useExternalOrbot,
                socksHost = socksHost,
                socksPort = socksPort,
                controlPort = controlPort,
                bridgeTransportType = bridgeTransportType,
                customBridgeLines = customBridgeLines
            )
            showMessage("Tor configuration saved")
        }
    }

    fun saveAndVerifyProxyProfile(
        id: Long = 0,
        name: String,
        protocol: ProxyProtocol,
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDns: Boolean,
        isFallback: Boolean,
        activateNow: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedId = proxyManager.saveProfile(
                id = id,
                name = name,
                protocol = protocol,
                host = host,
                port = port,
                username = username,
                password = password,
                remoteDns = remoteDns,
                isFallback = isFallback,
                selectImmediately = activateNow
            )
            if (activateNow) {
                preferencesRepo.setConnectionMode(ConnectionMode.CUSTOM_PROXY)
                val ok = proxyManager.selectProfileAndVerify(savedId, settingsState.value.proxyAutoFallback)
                showMessage(
                    if (ok) "Proxy verified and activated: $name"
                    else "Proxy failed verification — Fail-Closed blocking active"
                )
            } else {
                showMessage("Saved proxy profile: $name")
            }
        }
    }

    fun selectAndTestProxyProfile(profileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            preferencesRepo.setConnectionMode(ConnectionMode.CUSTOM_PROXY)
            val ok = proxyManager.selectProfileAndVerify(profileId, settingsState.value.proxyAutoFallback)
            showMessage(
                if (ok) "Proxy connected and verified"
                else "Proxy unreachable — Fail-Closed protection active"
            )
        }
    }

    fun deleteProxyProfile(profile: ProxyProfileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            proxyManager.deleteProfile(profile)
            showMessage("Deleted proxy profile ${profile.name}")
        }
    }

    fun updateProxyOptions(failClosed: Boolean? = null, autoFallback: Boolean? = null) {
        viewModelScope.launch {
            preferencesRepo.updateProxySettings(failClosed, autoFallback)
        }
    }

    // --- Bookmarks, History, Downloads & Privacy ---

    fun toggleBookmarkForActiveTab() {
        val tab = activeTab.value ?: return
        if (tab.url.isBlank() || tab.url == "about:blank") return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = bookmarkDao.findByUrl(tab.url)
            if (existing != null) {
                bookmarkDao.delete(existing)
                showMessage("Removed bookmark")
            } else {
                bookmarkDao.insert(
                    BookmarkEntity(
                        title = tab.title.ifBlank { tab.url },
                        url = tab.url
                    )
                )
                showMessage("Added to bookmarks")
            }
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkDao.delete(bookmark)
        }
    }

    fun deleteHistoryItem(item: HistoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.delete(item)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.clearAll()
            showMessage("Cleared browsing history")
        }
    }

    fun startManualDownload(url: String) {
        downloadManager.enqueueManualDownload(url)
        showMessage("Enqueued download via ${connectionState.value.mode.name}")
    }

    fun deleteDownload(entity: DownloadEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadManager.deleteDownload(entity)
        }
    }

    fun updatePrivacySettings(
        javascriptEnabled: Boolean? = null,
        blockPopups: Boolean? = null,
        httpsOnlyMode: Boolean? = null,
        trackerProtectionStrict: Boolean? = null,
        disableWebRtc: Boolean? = null,
        disableIpv6: Boolean? = null,
        preventDnsLeaks: Boolean? = null,
        resistFingerprinting: Boolean? = null,
        cookiePolicy: CookiePolicy? = null,
        dohMode: DohMode? = null,
        dohServerUri: String? = null,
        searchEngine: SearchEngine? = null,
        restoreSession: Boolean? = null,
        clearOnExit: Boolean? = null
    ) {
        viewModelScope.launch {
            preferencesRepo.updatePrivacySettings(
                javascriptEnabled = javascriptEnabled,
                blockPopups = blockPopups,
                httpsOnlyMode = httpsOnlyMode,
                trackerProtectionStrict = trackerProtectionStrict,
                disableWebRtc = disableWebRtc,
                disableIpv6 = disableIpv6,
                preventDnsLeaks = preventDnsLeaks,
                resistFingerprinting = resistFingerprinting,
                cookiePolicy = cookiePolicy,
                dohMode = dohMode,
                dohServerUri = dohServerUri,
                searchEngine = searchEngine,
                restoreSession = restoreSession,
                clearOnExit = clearOnExit
            )
        }
    }

    fun clearAllBrowsingData(
        clearHistory: Boolean = true,
        clearCookiesAndSiteData: Boolean = true,
        clearCache: Boolean = true,
        clearDownloads: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (clearHistory) {
                historyDao.clearAll()
            }
            if (clearDownloads) {
                downloadManager.clearAllDownloads()
            }
            engineProvider.clearGeckoBrowsingData(
                clearCookiesAndSiteData = clearCookiesAndSiteData,
                clearCache = clearCache,
                clearSitePermissions = true
            ) {
                showMessage("Browsing data, cookies, and cache cleared")
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NoFenceViewModel(application) as T
        }
    }
}
