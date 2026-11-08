package com.nofence.browser.browser

import android.net.Uri
import org.mozilla.geckoview.GeckoSession

data class SslSecurityInfo(
    val isSecure: Boolean = false,
    val host: String = "",
    val issuerOrganization: String = "",
    val subjectCommonName: String = "",
    val protocolVersion: String = "",
    val cipherSuite: String = "",
    val isException: Boolean = false
)

data class FindInPageState(
    val isVisible: Boolean = false,
    val query: String = "",
    val currentMatch: Int = 0,
    val totalMatches: Int = 0
)

data class PendingFileUploadRequest(
    val tabId: String,
    val mimeTypes: Array<String>,
    val isMultiple: Boolean,
    val onFilesSelected: (Array<Uri>?) -> Unit
)

data class PendingSitePermissionRequest(
    val tabId: String,
    val uri: String,
    val permissionNames: List<String>,
    val onDecision: (Boolean) -> Unit
)

data class BrowserTabModel(
    val id: String,
    val url: String = "about:blank",
    val title: String = "New Tab",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isPrivate: Boolean = false,
    val isDesktopMode: Boolean = false,
    val securityInfo: SslSecurityInfo = SslSecurityInfo(),
    val blockedTrackersCount: Int = 0,
    val blockedTrackerDomains: List<String> = emptyList(),
    val findInPageState: FindInPageState = FindInPageState(),
    val failClosedBlockedUrl: String? = null,
    val pageErrorDescription: String? = null,
    val session: GeckoSession
)
