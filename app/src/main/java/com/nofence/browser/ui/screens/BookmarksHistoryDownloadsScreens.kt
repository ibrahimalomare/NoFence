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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nofence.browser.storage.BookmarkEntity
import com.nofence.browser.storage.DownloadEntity
import com.nofence.browser.storage.DownloadStatus
import com.nofence.browser.storage.HistoryEntity
import com.nofence.browser.ui.theme.CharcoalSurface
import com.nofence.browser.ui.theme.CyberCyan
import com.nofence.browser.ui.theme.ObsidianBlack
import com.nofence.browser.ui.theme.StatusFailClosedError
import com.nofence.browser.ui.theme.StatusTorProtected
import com.nofence.browser.ui.theme.SubtleBorder
import com.nofence.browser.ui.theme.TextPrimary
import com.nofence.browser.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkEntity>,
    onSelectBookmark: (String) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks (${bookmarks.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (bookmarks.isEmpty()) {
                item {
                    Text(
                        text = "No bookmarks saved yet. Tap the bookmark icon in the bottom toolbar to save pages.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            items(bookmarks, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalSurface)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            onSelectBookmark(item.url)
                            onBack()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            item.url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onDeleteBookmark(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Bookmark", tint = StatusFailClosedError)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyItems: List<HistoryEntity>,
    onSelectUrl: (String) -> Unit,
    onDeleteHistoryItem: (HistoryEntity) -> Unit,
    onClearAllHistory: () -> Unit,
    onBack: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                title = { Text("Browsing History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onClearAllHistory) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All History")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (historyItems.isEmpty()) {
                item {
                    Text(
                        text = "Browsing history is empty. Private tabs never record history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            items(historyItems, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalSurface)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(12.dp))
                        .clickable {
                            onSelectUrl(item.url)
                            onBack()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.url,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatter.format(Date(item.visitedAt)),
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberCyan
                        )
                    }
                    IconButton(onClick = { onDeleteHistoryItem(item) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusFailClosedError)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntity>,
    onEnqueueDownloadUrl: (String) -> Unit,
    onDeleteDownload: (DownloadEntity) -> Unit,
    onBack: () -> Unit
) {
    var manualUrl by remember { mutableStateOf("") }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                title = { Text("Proxy-Routed Downloads") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "All downloads are routed through your active NoFence connection mode (Tor SOCKS5 or Custom Proxy) and blocked if Fail-Closed is active.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    label = { Text("Direct URL to download over active tunnel") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (manualUrl.isNotBlank()) {
                            onEnqueueDownloadUrl(manualUrl.trim())
                            manualUrl = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(downloads, key = { it.id }) { item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CharcoalSurface)
                            .border(1.dp, SubtleBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.fileName, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(
                                    text = "${item.status.name} • Routed via ${item.routedVia} • ${item.downloadedBytes / 1024} KB",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (item.status == DownloadStatus.COMPLETED) StatusTorProtected else CyberCyan
                                )
                                item.errorMessage?.let { err ->
                                    Text(err, style = MaterialTheme.typography.bodyMedium, color = StatusFailClosedError)
                                }
                            }
                            IconButton(onClick = { onDeleteDownload(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Download", tint = StatusFailClosedError)
                            }
                        }
                        if (item.status == DownloadStatus.DOWNLOADING) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val progress = if (item.totalBytes > 0) {
                                (item.downloadedBytes.toFloat() / item.totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else 0.5f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = CyberCyan
                            )
                        }
                    }
                }
            }
        }
    }
}
