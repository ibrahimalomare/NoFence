package com.nofence.browser.browser

import android.net.Uri
import android.util.Patterns
import com.nofence.browser.downloads.BrowserDownloadManager
import com.nofence.browser.geckoview.GeckoEngineProvider
import com.nofence.browser.networking.ConnectionCoordinator
import com.nofence.browser.settings.BrowserSettingsState
import com.nofence.browser.settings.SearchEngine
import com.nofence.browser.storage.HistoryDao
import com.nofence.browser.storage.HistoryEntity
import com.nofence.browser.storage.TabStateDao
import com.nofence.browser.storage.TabStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebRequestError
import org.mozilla.geckoview.WebResponse
import java.net.URLEncoder
import java.util.UUID

/**
 * Manages the lifecycle of all open normal and private `GeckoSession` tabs, wires Mozilla
 * GeckoView delegates (`NavigationDelegate`, `ProgressDelegate`, `ContentDelegate`,
 * `ContentBlocking.Delegate`, `PermissionDelegate`, `PromptDelegate`, `HistoryDelegate`),
 * enforces Fail-Closed navigation blocking when Tor or Custom Proxy is unavailable,
 * and persists normal tab state for session restoration.
 */
class BrowserTabManager(
    private val engineProvider: GeckoEngineProvider,
    private val connectionCoordinator: ConnectionCoordinator,
    private val historyDao: HistoryDao,
    private val tabStateDao: TabStateDao,
    private val downloadManager: BrowserDownloadManager,
    private val scope: CoroutineScope
) {

    private val _tabs = MutableStateFlow<List<BrowserTabModel>>(emptyList())
    val tabs: StateFlow<List<BrowserTabModel>> = _tabs.asStateFlow()

    private val _selectedTabId = MutableStateFlow<String?>(null)
    val selectedTabId: StateFlow<String?> = _selectedTabId.asStateFlow()

    private val _pendingFileUpload = MutableStateFlow<PendingFileUploadRequest?>(null)
    val pendingFileUpload: StateFlow<PendingFileUploadRequest?> = _pendingFileUpload.asStateFlow()

    private val _pendingSitePermission = MutableStateFlow<PendingSitePermissionRequest?>(null)
    val pendingSitePermission: StateFlow<PendingSitePermissionRequest?> = _pendingSitePermission.asStateFlow()

    @Volatile
    private var currentSettings: BrowserSettingsState = BrowserSettingsState()

    fun updateSettings(settings: BrowserSettingsState) {
        currentSettings = settings
    }

    /**
     * Restores persisted non-private tabs from Room on startup, or creates a fresh Home tab.
     */
    suspend fun restoreTabsOrCreateDefault(
        runtime: GeckoRuntime,
        restoreEnabled: Boolean
    ) {
        if (restoreEnabled) {
            val saved = tabStateDao.getAllSavedTabs()
            if (saved.isNotEmpty()) {
                val restoredModels = saved.map { entity ->
                    createGeckoTabInternal(
                        runtime = runtime,
                        tabId = entity.tabId,
                        initialUrl = entity.url,
                        initialTitle = entity.title,
                        isPrivate = false,
                        isDesktopMode = entity.isDesktopMode,
                        loadImmediately = entity.url != "about:blank"
                    )
                }
                _tabs.value = restoredModels
                val selected = saved.firstOrNull { it.isSelected }?.tabId ?: restoredModels.first().id
                _selectedTabId.value = selected
                return
            }
        }
        createNewTab(runtime = runtime, url = "about:blank", isPrivate = false, selectTab = true)
    }

    fun createNewTab(
        runtime: GeckoRuntime,
        url: String = "about:blank",
        isPrivate: Boolean = false,
        selectTab: Boolean = true
    ): BrowserTabModel {
        val tabId = UUID.randomUUID().toString()
        val normalizedUrl = if (url.isBlank() || url == "about:blank") {
            "about:blank"
        } else {
            resolveAddressOrSearchQuery(url, currentSettings.searchEngine, currentSettings.httpsOnlyMode)
        }

        val tab = createGeckoTabInternal(
            runtime = runtime,
            tabId = tabId,
            initialUrl = normalizedUrl,
            initialTitle = if (normalizedUrl == "about:blank") "New Tab" else normalizedUrl,
            isPrivate = isPrivate,
            isDesktopMode = false,
            loadImmediately = normalizedUrl != "about:blank"
        )

        _tabs.value = _tabs.value + tab
        if (selectTab || _selectedTabId.value == null) {
            _selectedTabId.value = tab.id
        }
        persistNormalTabsAsync()
        return tab
    }

    fun selectTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _selectedTabId.value = tabId
            persistNormalTabsAsync()
        }
    }

    fun closeTab(tabId: String, runtime: GeckoRuntime) {
        val target = _tabs.value.find { it.id == tabId } ?: return
        runCatching { target.session.close() }
        val remaining = _tabs.value.filterNot { it.id == tabId }
        _tabs.value = remaining

        if (remaining.isEmpty()) {
            createNewTab(runtime = runtime, url = "about:blank", isPrivate = false, selectTab = true)
        } else if (_selectedTabId.value == tabId) {
            _selectedTabId.value = remaining.last().id
        }
        persistNormalTabsAsync()
    }

    fun closeAllTabs(onlyPrivate: Boolean = false, runtime: GeckoRuntime) {
        val toClose = if (onlyPrivate) _tabs.value.filter { it.isPrivate } else _tabs.value
        toClose.forEach { runCatching { it.session.close() } }

        val remaining = if (onlyPrivate) _tabs.value.filterNot { it.isPrivate } else emptyList()
        _tabs.value = remaining

        if (remaining.isEmpty()) {
            createNewTab(runtime = runtime, url = "about:blank", isPrivate = false, selectTab = true)
        } else if (_selectedTabId.value !in remaining.map { it.id }) {
            _selectedTabId.value = remaining.last().id
        }
        persistNormalTabsAsync()
    }

    /**
     * Navigates the active tab to a URL or search query.
     * Strictly checks `ConnectionCoordinator.state.value.isBrowsingAllowed` before loading:
     * if Tor or Custom Proxy fail-closed protection is blocking traffic, refuses to initiate network
     * loading and displays a clear fail-closed diagnostic banner on the tab.
     */
    fun loadUrlOrSearchInActiveTab(input: String) {
        val activeTab = getActiveTab() ?: return
        val resolvedUrl = resolveAddressOrSearchQuery(
            rawInput = input,
            searchEngine = currentSettings.searchEngine,
            httpsFirst = currentSettings.httpsOnlyMode
        )

        val coord = connectionCoordinator.state.value
        if (!coord.isBrowsingAllowed && !resolvedUrl.startsWith("about:")) {
            updateTab(activeTab.id) {
                it.copy(
                    url = resolvedUrl,
                    isLoading = false,
                    failClosedBlockedUrl = resolvedUrl,
                    pageErrorDescription = "Blocked by NoFence Fail-Closed Protection: ${coord.bannerTitle}. ${coord.bannerSubtitle}"
                )
            }
            return
        }

        updateTab(activeTab.id) {
            it.copy(
                url = resolvedUrl,
                failClosedBlockedUrl = null,
                pageErrorDescription = null
            )
        }
        activeTab.session.loadUri(resolvedUrl)
        persistNormalTabsAsync()
    }

    fun goBack() {
        getActiveTab()?.takeIf { it.canGoBack }?.session?.goBack()
    }

    fun goForward() {
        getActiveTab()?.takeIf { it.canGoForward }?.session?.goForward()
    }

    fun reloadOrStop() {
        val tab = getActiveTab() ?: return
        if (tab.isLoading) {
            tab.session.stop()
        } else {
            val coord = connectionCoordinator.state.value
            if (!coord.isBrowsingAllowed && !tab.url.startsWith("about:")) {
                updateTab(tab.id) {
                    it.copy(
                        failClosedBlockedUrl = tab.url,
                        pageErrorDescription = "Blocked by Fail-Closed Protection: ${coord.bannerTitle}"
                    )
                }
                return
            }
            updateTab(tab.id) { it.copy(failClosedBlockedUrl = null, pageErrorDescription = null) }
            tab.session.reload()
        }
    }

    fun toggleDesktopMode() {
        val tab = getActiveTab() ?: return
        val nextDesktop = !tab.isDesktopMode
        tab.session.settings.userAgentMode = if (nextDesktop) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        tab.session.settings.viewportMode = if (nextDesktop) {
            GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        }
        updateTab(tab.id) { it.copy(isDesktopMode = nextDesktop) }
        tab.session.reload()
        persistNormalTabsAsync()
    }

    /**
     * Find in page powered by `GeckoSession.Finder`.
     */
    fun openFindInPage() {
        val tab = getActiveTab() ?: return
        updateTab(tab.id) {
            it.copy(findInPageState = it.findInPageState.copy(isVisible = true))
        }
    }

    fun closeFindInPage() {
        val tab = getActiveTab() ?: return
        runCatching { tab.session.finder.clear() }
        updateTab(tab.id) {
            it.copy(findInPageState = FindInPageState(isVisible = false))
        }
    }

    fun findInPage(query: String, forward: Boolean = true) {
        val tab = getActiveTab() ?: return
        if (query.isBlank()) {
            runCatching { tab.session.finder.clear() }
            updateTab(tab.id) {
                it.copy(findInPageState = it.findInPageState.copy(query = "", currentMatch = 0, totalMatches = 0))
            }
            return
        }
        val flags = if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS
        tab.session.finder.find(query, flags).accept({ result ->
            if (result != null) {
                updateTab(tab.id) {
                    it.copy(
                        findInPageState = it.findInPageState.copy(
                            isVisible = true,
                            query = query,
                            currentMatch = result.current,
                            totalMatches = result.total
                        )
                    )
                }
            }
        }, { })
    }

    fun dismissPendingFileUpload() {
        _pendingFileUpload.value?.onFilesSelected?.invoke(null)
        _pendingFileUpload.value = null
    }

    fun completePendingFileUpload(uris: Array<Uri>?) {
        _pendingFileUpload.value?.onFilesSelected?.invoke(uris)
        _pendingFileUpload.value = null
    }

    fun respondToSitePermissionRequest(granted: Boolean) {
        _pendingSitePermission.value?.onDecision?.invoke(granted)
        _pendingSitePermission.value = null
    }

    fun getActiveTab(): BrowserTabModel? {
        val id = _selectedTabId.value ?: return _tabs.value.firstOrNull()
        return _tabs.value.find { it.id == id } ?: _tabs.value.firstOrNull()
    }

    /**
     * Converts raw address bar text into a normalized URL or search engine query,
     * applying HTTPS-First upgrading to bare domains when enabled.
     */
    fun resolveAddressOrSearchQuery(
        rawInput: String,
        searchEngine: SearchEngine,
        httpsFirst: Boolean
    ): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || trimmed.equals("about:blank", ignoreCase = true)) {
            return "about:blank"
        }
        if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true)
        ) {
            return if (httpsFirst && trimmed.startsWith("http://", ignoreCase = true) && !trimmed.endsWith(".onion") && !trimmed.contains(".onion/")) {
                "https://" + trimmed.removePrefix("http://").removePrefix("HTTP://")
            } else {
                trimmed
            }
        }

        // Support .onion v3 domains directly over HTTP or HTTPS
        if (trimmed.endsWith(".onion") || trimmed.contains(".onion/")) {
            return "http://$trimmed"
        }

        val looksLikeDomain = !trimmed.contains(" ") &&
            (trimmed.contains(".") || Patterns.WEB_URL.matcher(trimmed).matches())

        return if (looksLikeDomain) {
            val scheme = if (httpsFirst) "https://" else "http://"
            "$scheme$trimmed"
        } else {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            searchEngine.queryTemplate.replace("%s", encoded)
        }
    }

    private fun createGeckoTabInternal(
        runtime: GeckoRuntime,
        tabId: String,
        initialUrl: String,
        initialTitle: String,
        isPrivate: Boolean,
        isDesktopMode: Boolean,
        loadImmediately: Boolean
    ): BrowserTabModel {
        val sessionSettings = GeckoSessionSettings.Builder()
            .usePrivateMode(isPrivate)
            .useTrackingProtection(true)
            .userAgentMode(
                if (isDesktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            )
            .viewportMode(
                if (isDesktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            )
            .build()

        val session = GeckoSession(sessionSettings)
        session.open(runtime)

        wireGeckoSessionDelegates(tabId, session, isPrivate, runtime)

        val coord = connectionCoordinator.state.value
        var failClosedUrl: String? = null
        var errorDesc: String? = null

        if (loadImmediately && initialUrl != "about:blank") {
            if (!coord.isBrowsingAllowed && !initialUrl.startsWith("about:")) {
                failClosedUrl = initialUrl
                errorDesc = "Blocked by Fail-Closed Protection (${coord.bannerTitle})"
            } else {
                session.loadUri(initialUrl)
            }
        }

        return BrowserTabModel(
            id = tabId,
            url = initialUrl,
            title = initialTitle,
            isPrivate = isPrivate,
            isDesktopMode = isDesktopMode,
            failClosedBlockedUrl = failClosedUrl,
            pageErrorDescription = errorDesc,
            session = session
        )
    }

    private fun wireGeckoSessionDelegates(
        tabId: String,
        session: GeckoSession,
        isPrivate: Boolean,
        runtime: GeckoRuntime
    ) {
        // 1. NavigationDelegate — Enforces Fail-Closed before every top-level loadRequest!
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                val coord = connectionCoordinator.state.value

                // Strict fail-closed enforcement at the GeckoSession navigation layer
                if (!coord.isBrowsingAllowed && !uri.startsWith("about:") && !uri.startsWith("moz-extension:")) {
                    updateTab(tabId) {
                        it.copy(
                            isLoading = false,
                            failClosedBlockedUrl = uri,
                            pageErrorDescription = "Blocked by Fail-Closed Protection (${coord.bannerTitle}): Direct connections are prohibited in ${coord.mode.name} mode."
                        )
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                // HTTPS-First upgrade for cleartext http:// requests (excluding .onion hidden services)
                if (currentSettings.httpsOnlyMode &&
                    uri.startsWith("http://", ignoreCase = true) &&
                    !uri.contains(".onion")
                ) {
                    val upgraded = "https://" + uri.substring(7)
                    updateTab(tabId) { it.copy(url = upgraded, failClosedBlockedUrl = null, pageErrorDescription = null) }
                    session.loadUri(upgraded)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                    if (currentSettings.blockPopups && !request.hasUserGesture) {
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    createNewTab(runtime = runtime, url = uri, isPrivate = isPrivate, selectTab = true)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                val validUrl = url ?: return
                updateTab(tabId) {
                    it.copy(
                        url = validUrl,
                        failClosedBlockedUrl = null,
                        pageErrorDescription = null
                    )
                }
                persistNormalTabsAsync()
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                updateTab(tabId) { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                updateTab(tabId) { it.copy(canGoForward = canGoForward) }
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError
            ): GeckoResult<String>? {
                val coord = connectionCoordinator.state.value
                val desc = if (!coord.isBrowsingAllowed) {
                    "Fail-Closed Active: ${coord.bannerTitle}. ${coord.bannerSubtitle}"
                } else {
                    "Connection Error (Code ${error.code} / Category ${error.category}) loading ${uri ?: "page"}."
                }
                updateTab(tabId) {
                    it.copy(
                        isLoading = false,
                        pageErrorDescription = desc
                    )
                }
                return null
            }
        }

        // 2. ProgressDelegate — Loading progress bar & TLS/HTTPS security state
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                updateTab(tabId) {
                    it.copy(
                        url = url,
                        isLoading = true,
                        progress = 10,
                        blockedTrackersCount = 0,
                        blockedTrackerDomains = emptyList(),
                        pageErrorDescription = null
                    )
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                updateTab(tabId) { it.copy(progress = progress.coerceIn(0, 100)) }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                updateTab(tabId) {
                    it.copy(
                        isLoading = false,
                        progress = if (success) 100 else 0
                    )
                }
                if (success && !isPrivate) {
                    val tab = _tabs.value.find { it.id == tabId }
                    if (tab != null && tab.url.startsWith("http")) {
                        scope.launch(Dispatchers.IO) {
                            historyDao.insert(
                                HistoryEntity(
                                    title = tab.title.ifBlank { tab.url },
                                    url = tab.url
                                )
                            )
                        }
                    }
                }
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
            ) {
                val cert = securityInfo.certificate
                val sslModel = SslSecurityInfo(
                    isSecure = securityInfo.isSecure,
                    host = securityInfo.host ?: "",
                    issuerOrganization = cert?.issuerDN?.name ?: securityInfo.issuerOrganization ?: "",
                    subjectCommonName = cert?.subjectDN?.name ?: "",
                    protocolVersion = securityInfo.protocolVersion ?: "TLS",
                    cipherSuite = "",
                    isException = securityInfo.isException
                )
                updateTab(tabId) { it.copy(securityInfo = sslModel) }
            }
        }

        // 3. ContentDelegate — Page title updates and external file downloads
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (!title.isNullOrBlank()) {
                    updateTab(tabId) { it.copy(title = title) }
                    persistNormalTabsAsync()
                }
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                downloadManager.handleGeckoExternalResponse(response)
            }
        }

        // 4. ContentBlocking.Delegate — Tracker blocking counter per tab
        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                val host = runCatching { Uri.parse(event.uri).host ?: event.uri }.getOrDefault(event.uri)
                updateTab(tabId) { tab ->
                    val updatedList = (tab.blockedTrackerDomains + host).distinct().take(50)
                    tab.copy(
                        blockedTrackersCount = tab.blockedTrackersCount + 1,
                        blockedTrackerDomains = updatedList
                    )
                }
            }
        }

        // 5. PermissionDelegate — Secure site permission prompts (Deny by default unless user approves)
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int>? {
                val result = GeckoResult<Int>()
                val permLabel = when (perm.permission) {
                    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "Precise Location (Geolocation)"
                    GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "Notifications"
                    GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "DRM Media Access"
                    GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS -> "Cross-Site Cookie Storage Access"
                    else -> "Site Permission (${perm.permission})"
                }
                _pendingSitePermission.value = PendingSitePermissionRequest(
                    tabId = tabId,
                    uri = perm.uri,
                    permissionNames = listOf(permLabel),
                    onDecision = { granted ->
                        result.complete(
                            if (granted) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                            else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                        )
                    }
                )
                return result
            }
        }

        // 6. PromptDelegate — File Uploads (`FilePrompt`) and Pop-up/Alert handling
        session.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                val mimeTypes = prompt.mimeTypes ?: arrayOf("*/*")
                val isMultiple = prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE

                _pendingFileUpload.value = PendingFileUploadRequest(
                    tabId = tabId,
                    mimeTypes = if (mimeTypes.isEmpty()) arrayOf("*/*") else mimeTypes,
                    isMultiple = isMultiple,
                    onFilesSelected = { uris ->
                        if (uris.isNullOrEmpty()) {
                            result.complete(prompt.dismiss())
                        } else if (isMultiple) {
                            result.complete(prompt.confirm(engineProviderContext(), uris))
                        } else {
                            result.complete(prompt.confirm(engineProviderContext(), uris.first()))
                        }
                    }
                )
                return result
            }
        }
    }

    private var appContextRef: android.content.Context? = null

    fun attachAppContext(context: android.content.Context) {
        appContextRef = context.applicationContext
    }

    private fun engineProviderContext(): android.content.Context {
        return requireNotNull(appContextRef) { "Application context not attached to BrowserTabManager" }
    }

    private fun updateTab(tabId: String, transform: (BrowserTabModel) -> BrowserTabModel) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) transform(tab) else tab
        }
    }

    private fun persistNormalTabsAsync() {
        val currentTabs = _tabs.value.filterNot { it.isPrivate }
        val selectedId = _selectedTabId.value
        scope.launch(Dispatchers.IO) {
            tabStateDao.clearAll()
            val entities = currentTabs.mapIndexed { index, tab ->
                TabStateEntity(
                    tabId = tab.id,
                    title = tab.title,
                    url = tab.url,
                    isDesktopMode = tab.isDesktopMode,
                    isSelected = tab.id == selectedId,
                    positionIndex = index
                )
            }
            tabStateDao.upsertAll(entities)
        }
    }
}
