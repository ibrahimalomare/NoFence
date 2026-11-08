package com.nofence.browser.downloads

import android.content.Context
import android.webkit.URLUtil
import com.nofence.browser.networking.ConnectionCoordinator
import com.nofence.browser.storage.DownloadDao
import com.nofence.browser.storage.DownloadEntity
import com.nofence.browser.storage.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles downloads triggered by GeckoView's `ContentDelegate.onExternalResponse(session, response)`
 * or explicit user download actions.
 *
 * PRIVACY GUARANTEE:
 * - First attempts to consume `WebResponse.body` directly from GeckoView's already-proxied stream.
 * - If a standalone download fetch is requested, uses `ConnectionCoordinator.getJavaNetProxyForDownloads()`
 *   so the download strictly traverses the active Tor SOCKS5 or Custom Proxy tunnel and is blocked
 *   if Fail-Closed is active.
 */
class BrowserDownloadManager(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val connectionCoordinator: ConnectionCoordinator,
    private val scope: CoroutineScope
) {

    val downloadsFlow: Flow<List<DownloadEntity>> = downloadDao.observeAll()

    private val downloadDir: File by lazy {
        File(context.filesDir, "downloads").apply { mkdirs() }
    }

    fun handleGeckoExternalResponse(response: WebResponse) {
        scope.launch(Dispatchers.IO) {
            val url = response.uri
            val contentType = response.headers["Content-Type"]
                ?: response.headers["content-type"]
                ?: "application/octet-stream"
            val contentDisposition = response.headers["Content-Disposition"]
                ?: response.headers["content-disposition"]
            val contentLength = (response.headers["Content-Length"]
                ?: response.headers["content-length"])?.toLongOrNull() ?: -1L

            val guessedName = URLUtil.guessFileName(url, contentDisposition, contentType)
                .ifBlank { "download_${System.currentTimeMillis()}.bin" }
            val targetFile = uniqueDestinationFile(guessedName)

            val routedViaLabel = connectionCoordinator.state.value.mode.name
            val initialEntity = DownloadEntity(
                fileName = targetFile.name,
                sourceUrl = url,
                mimeType = contentType,
                destinationPath = targetFile.absolutePath,
                totalBytes = contentLength,
                downloadedBytes = 0L,
                status = DownloadStatus.DOWNLOADING,
                routedVia = routedViaLabel
            )
            val downloadId = downloadDao.insert(initialEntity)

            val bodyStream: InputStream? = response.body
            if (bodyStream != null) {
                streamToDisk(
                    downloadId = downloadId,
                    initialEntity = initialEntity.copy(id = downloadId),
                    inputStream = bodyStream,
                    targetFile = targetFile
                )
            } else {
                downloadViaProxiedConnection(
                    downloadId = downloadId,
                    initialEntity = initialEntity.copy(id = downloadId),
                    targetFile = targetFile
                )
            }
        }
    }

    fun enqueueManualDownload(url: String, suggestedFileName: String? = null) {
        scope.launch(Dispatchers.IO) {
            val fileName = suggestedFileName
                ?: URLUtil.guessFileName(url, null, null).ifBlank { "download_${System.currentTimeMillis()}" }
            val targetFile = uniqueDestinationFile(fileName)
            val routedViaLabel = connectionCoordinator.state.value.mode.name

            val initialEntity = DownloadEntity(
                fileName = targetFile.name,
                sourceUrl = url,
                mimeType = "application/octet-stream",
                destinationPath = targetFile.absolutePath,
                totalBytes = -1L,
                downloadedBytes = 0L,
                status = DownloadStatus.QUEUED,
                routedVia = routedViaLabel
            )
            val id = downloadDao.insert(initialEntity)
            downloadViaProxiedConnection(id, initialEntity.copy(id = id), targetFile)
        }
    }

    private suspend fun streamToDisk(
        downloadId: Long,
        initialEntity: DownloadEntity,
        inputStream: InputStream,
        targetFile: File
    ) {
        try {
            var totalRead = 0L
            var lastUpdateMs = System.currentTimeMillis()
            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        totalRead += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateMs > 400) {
                            lastUpdateMs = now
                            downloadDao.update(
                                initialEntity.copy(
                                    id = downloadId,
                                    downloadedBytes = totalRead,
                                    status = DownloadStatus.DOWNLOADING
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }
            downloadDao.update(
                initialEntity.copy(
                    id = downloadId,
                    downloadedBytes = totalRead,
                    totalBytes = if (initialEntity.totalBytes <= 0) totalRead else initialEntity.totalBytes,
                    status = DownloadStatus.COMPLETED
                )
            )
        } catch (e: Exception) {
            downloadDao.update(
                initialEntity.copy(
                    id = downloadId,
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Stream interrupted"
                )
            )
        }
    }

    private suspend fun downloadViaProxiedConnection(
        downloadId: Long,
        initialEntity: DownloadEntity,
        targetFile: File
    ) {
        try {
            val proxy = connectionCoordinator.getJavaNetProxyForDownloads()
            val conn = (URL(initialEntity.sourceUrl).openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:133.0) Gecko/133.0 Firefox/133.0")
            }
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code from server")
            }
            val length = conn.contentLengthLong
            val mime = conn.contentType ?: initialEntity.mimeType
            val updatedEntity = initialEntity.copy(
                id = downloadId,
                totalBytes = length,
                mimeType = mime,
                status = DownloadStatus.DOWNLOADING
            )
            downloadDao.update(updatedEntity)
            streamToDisk(downloadId, updatedEntity, conn.inputStream, targetFile)
        } catch (e: Exception) {
            downloadDao.update(
                initialEntity.copy(
                    id = downloadId,
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Download failed"
                )
            )
        }
    }

    suspend fun deleteDownload(entity: DownloadEntity, deleteFileOnDisk: Boolean = true) {
        if (deleteFileOnDisk) {
            runCatching { File(entity.destinationPath).delete() }
        }
        downloadDao.delete(entity)
    }

    suspend fun clearAllDownloads() {
        downloadDir.listFiles()?.forEach { runCatching { it.delete() } }
        downloadDao.clearAll()
    }

    private fun uniqueDestinationFile(baseName: String): File {
        val sanitized = baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        var candidate = File(downloadDir, sanitized)
        var counter = 1
        val nameWithoutExt = sanitized.substringBeforeLast(".")
        val ext = sanitized.substringAfterLast(".", "")
        while (candidate.exists()) {
            val nextName = if (ext.isNotEmpty()) "${nameWithoutExt}_$counter.$ext" else "${nameWithoutExt}_$counter"
            candidate = File(downloadDir, nextName)
            counter++
        }
        return candidate
    }
}
