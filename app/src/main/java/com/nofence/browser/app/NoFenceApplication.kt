package com.nofence.browser.app

import android.app.Application
import com.nofence.browser.browser.BrowserTabManager
import com.nofence.browser.downloads.BrowserDownloadManager
import com.nofence.browser.geckoview.GeckoEngineProvider
import com.nofence.browser.networking.ConnectionCoordinator
import com.nofence.browser.networking.NetworkMonitor
import com.nofence.browser.privacy.EncryptedCredentialStore
import com.nofence.browser.proxy.ProxyManager
import com.nofence.browser.settings.BrowserPreferencesRepository
import com.nofence.browser.storage.NoFenceDatabase
import com.nofence.browser.tor.TorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NoFenceApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: NoFenceDatabase by lazy {
        NoFenceDatabase.getInstance(this)
    }

    val credentialStore: EncryptedCredentialStore by lazy {
        EncryptedCredentialStore(this)
    }

    val preferencesRepository: BrowserPreferencesRepository by lazy {
        BrowserPreferencesRepository(this)
    }

    val torManager: TorManager by lazy {
        TorManager(this, applicationScope)
    }

    val proxyManager: ProxyManager by lazy {
        ProxyManager(
            proxyProfileDao = database.proxyProfileDao(),
            credentialStore = credentialStore,
            scope = applicationScope
        )
    }

    val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(this)
    }

    val connectionCoordinator: ConnectionCoordinator by lazy {
        ConnectionCoordinator(
            preferencesRepository = preferencesRepository,
            torManager = torManager,
            proxyManager = proxyManager,
            networkMonitor = networkMonitor,
            scope = applicationScope
        )
    }

    val geckoEngineProvider: GeckoEngineProvider by lazy {
        GeckoEngineProvider(this, credentialStore)
    }

    val downloadManager: BrowserDownloadManager by lazy {
        BrowserDownloadManager(
            context = this,
            downloadDao = database.downloadDao(),
            connectionCoordinator = connectionCoordinator,
            scope = applicationScope
        )
    }

    val tabManager: BrowserTabManager by lazy {
        BrowserTabManager(
            engineProvider = geckoEngineProvider,
            connectionCoordinator = connectionCoordinator,
            historyDao = database.historyDao(),
            tabStateDao = database.tabStateDao(),
            downloadManager = downloadManager,
            scope = applicationScope
        ).apply {
            attachAppContext(this@NoFenceApplication)
        }
    }
}
