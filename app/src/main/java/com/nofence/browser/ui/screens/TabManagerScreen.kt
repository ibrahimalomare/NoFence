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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.nofence.browser.browser.BrowserTabModel
import com.nofence.browser.ui.theme.CharcoalSurface
import com.nofence.browser.ui.theme.CyberCyan
import com.nofence.browser.ui.theme.ObsidianBlack
import com.nofence.browser.ui.theme.PrivatePurple
import com.nofence.browser.ui.theme.SubtleBorder
import com.nofence.browser.ui.theme.TextPrimary
import com.nofence.browser.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabManagerScreen(
    tabs: List<BrowserTabModel>,
    selectedTabId: String?,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onCloseAllTabs: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var showPrivateOnly by remember { mutableStateOf(false) }
    val filteredTabs = tabs.filter { it.isPrivate == showPrivateOnly }

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                title = { Text("Tab Manager (${tabs.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onCloseAllTabs(showPrivateOnly) }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Close Tabs")
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = !showPrivateOnly,
                    onClick = { showPrivateOnly = false },
                    label = { Text("Standard Tabs (${tabs.count { !it.isPrivate }})") }
                )
                FilterChip(
                    selected = showPrivateOnly,
                    onClick = { showPrivateOnly = true },
                    label = { Text("Private Tabs (${tabs.count { it.isPrivate }})") },
                    leadingIcon = { Icon(Icons.Default.Policy, contentDescription = null) }
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        onNewTab(false)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Tab")
                }

                OutlinedButton(
                    onClick = {
                        onNewTab(true)
                        onBack()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Policy, contentDescription = null, tint = PrivatePurple)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Private Tab")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredTabs, key = { it.id }) { tab ->
                    val isSelected = tab.id == selectedTabId
                    val borderColor = when {
                        isSelected && tab.isPrivate -> PrivatePurple
                        isSelected -> CyberCyan
                        else -> SubtleBorder
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CharcoalSurface)
                            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
                            .clickable {
                                onSelectTab(tab.id)
                                onBack()
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tab.title.ifBlank { "New Tab" },
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = tab.url,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (tab.blockedTrackersCount > 0) {
                                Text(
                                    text = "${tab.blockedTrackersCount} trackers blocked",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CyberCyan
                                )
                            }
                        }
                        IconButton(onClick = { onCloseTab(tab.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Tab")
                        }
                    }
                }
            }
        }
    }
}
