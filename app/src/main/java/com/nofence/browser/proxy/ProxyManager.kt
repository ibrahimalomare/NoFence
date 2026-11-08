package com.nofence.browser.proxy

import com.nofence.browser.privacy.EncryptedCredentialStore
import com.nofence.browser.privacy.ProxyCredentials
import com.nofence.browser.storage.ProxyProfileDao
import com.nofence.browser.storage.ProxyProfileEntity
import com.nofence.browser.storage.ProxyProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ProxyConnectionStatus {
    DISCONNECTED,
    VERIFYING,
    CONNECTED,
    ERROR
}

data class ProxyManagerState(
    val status: ProxyConnectionStatus = ProxyConnectionStatus.DISCONNECTED,
    val activeProfile: ProxyProfileEntity? = null,
    val lastDiagnostic: ProxyDiagnosticResult? = null,
    val statusMessage: String = "No active custom proxy selected",
    val failedOverFrom: String? = null
)

/**
 * Manages saved SOCKS5, HTTP, and HTTPS CONNECT proxy profiles, encrypted credentials,
 * live socket diagnostics, and automatic fallback to backup proxy profiles without ever
 * downgrading to a direct connection.
 */
class ProxyManager(
    private val proxyProfileDao: ProxyProfileDao,
    private val credentialStore: EncryptedCredentialStore,
    private val scope: CoroutineScope
) {

    val allProfilesFlow: Flow<List<ProxyProfileEntity>> = proxyProfileDao.observeAll()

    private val _state = MutableStateFlow(ProxyManagerState())
    val state: StateFlow<ProxyManagerState> = _state.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            proxyProfileDao.observeSelectedProfile().collect { selected ->
                val current = _state.value
                if (selected == null) {
                    _state.value = ProxyManagerState(
                        status = ProxyConnectionStatus.DISCONNECTED,
                        activeProfile = null,
                        statusMessage = "No proxy profile selected"
                    )
                } else if (current.activeProfile?.id != selected.id) {
                    _state.value = current.copy(
                        activeProfile = selected,
                        statusMessage = "Selected ${selected.name} (${selected.protocol.name} ${selected.host}:${selected.port})"
                    )
                }
            }
        }
    }

    suspend fun saveProfile(
        id: Long = 0,
        name: String,
        protocol: ProxyProtocol,
        host: String,
        port: Int,
        username: String,
        password: String,
        remoteDns: Boolean,
        isFallback: Boolean,
        selectImmediately: Boolean
    ): Long {
        val hasAuth = username.isNotBlank()
        val entity = ProxyProfileEntity(
            id = id,
            name = name.trim().ifBlank { "$protocol $host:$port" },
            protocol = protocol,
            host = host.trim(),
            port = port.coerceIn(1, 65535),
            hasAuth = hasAuth,
            remoteDns = remoteDns,
            isFallback = isFallback,
            isSelected = selectImmediately
        )
        val savedId = proxyProfileDao.insert(entity)
        if (hasAuth) {
            credentialStore.saveProxyCredentials(savedId, username.trim(), password)
        } else {
            credentialStore.deleteProxyCredentials(savedId)
        }
        if (selectImmediately) {
            proxyProfileDao.selectExclusive(savedId)
        }
        return savedId
    }

    suspend fun deleteProfile(profile: ProxyProfileEntity) {
        credentialStore.deleteProxyCredentials(profile.id)
        proxyProfileDao.delete(profile)
    }

    fun getCredentialsForProfile(profileId: Long): ProxyCredentials? {
        return credentialStore.getProxyCredentials(profileId)
    }

    /**
     * Activates and verifies the selected proxy profile. If verification fails and `allowFallback`
     * is enabled, attempts each configured fallback proxy profile in order. Never downgrades to Direct.
     */
    suspend fun connectAndVerifySelectedProxy(allowFallback: Boolean = true): Boolean {
        val primary = proxyProfileDao.getSelectedProfile() ?: run {
            _state.value = ProxyManagerState(
                status = ProxyConnectionStatus.ERROR,
                activeProfile = null,
                statusMessage = "Fail-Closed: No custom proxy profile selected."
            )
            return false
        }

        _state.value = _state.value.copy(
            status = ProxyConnectionStatus.VERIFYING,
            activeProfile = primary,
            statusMessage = "Verifying ${primary.name} (${primary.host}:${primary.port})..."
        )

        val credentials = credentialStore.getProxyCredentials(primary.id)
        val primaryDiag = ProxyTester.testProxyProfile(primary, credentials)

        if (primaryDiag.success) {
            val updated = primary.copy(
                lastLatencyMs = primaryDiag.latencyMs,
                lastVerifiedAt = System.currentTimeMillis()
            )
            proxyProfileDao.update(updated)
            _state.value = ProxyManagerState(
                status = ProxyConnectionStatus.CONNECTED,
                activeProfile = updated,
                lastDiagnostic = primaryDiag,
                statusMessage = primaryDiag.message,
                failedOverFrom = null
            )
            return true
        }

        // Primary failed: try fallback profiles if enabled (still proxy-routed, NEVER direct)
        if (allowFallback) {
            val fallbacks = proxyProfileDao.getFallbackProfiles()
            for (candidate in fallbacks) {
                _state.value = _state.value.copy(
                    status = ProxyConnectionStatus.VERIFYING,
                    statusMessage = "Primary ${primary.name} unreachable; testing fallback ${candidate.name}..."
                )
                val fallbackCreds = credentialStore.getProxyCredentials(candidate.id)
                val diag = ProxyTester.testProxyProfile(candidate, fallbackCreds)
                if (diag.success) {
                    proxyProfileDao.selectExclusive(candidate.id)
                    val updated = candidate.copy(
                        isSelected = true,
                        lastLatencyMs = diag.latencyMs,
                        lastVerifiedAt = System.currentTimeMillis()
                    )
                    proxyProfileDao.update(updated)
                    _state.value = ProxyManagerState(
                        status = ProxyConnectionStatus.CONNECTED,
                        activeProfile = updated,
                        lastDiagnostic = diag,
                        statusMessage = "Switched to fallback proxy ${candidate.name} (${diag.latencyMs}ms)",
                        failedOverFrom = primary.name
                    )
                    return true
                }
            }
        }

        _state.value = ProxyManagerState(
            status = ProxyConnectionStatus.ERROR,
            activeProfile = primary,
            lastDiagnostic = primaryDiag,
            statusMessage = "Fail-Closed Active: ${primaryDiag.message}"
        )
        return false
    }

    suspend fun selectProfileAndVerify(profileId: Long, allowFallback: Boolean = true): Boolean {
        proxyProfileDao.selectExclusive(profileId)
        return connectAndVerifySelectedProxy(allowFallback)
    }

    fun markDisconnected() {
        _state.value = _state.value.copy(
            status = ProxyConnectionStatus.DISCONNECTED,
            statusMessage = "Custom proxy disconnected"
        )
    }
}
