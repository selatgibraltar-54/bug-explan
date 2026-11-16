package com.browser.client.ui.screens

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.browser.client.R
import com.browser.client.engine.DomainUtils
import com.browser.client.engine.DownloadProgressSnapshot
import com.browser.client.engine.DownloadTaskStatus
import com.browser.client.ui.components.WinXpProgressBar
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloads: List<DownloadProgressSnapshot>,
    onBack: () -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onCancelDownload: (Long) -> Unit,
    onRetryDownload: (Long) -> Unit,
    onDeleteDownload: (Long) -> Unit,
    onClearAll: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(downloads, searchQuery) {
        if (searchQuery.isBlank()) {
            downloads
        } else {
            downloads.filter {
                it.fileName.contains(searchQuery.trim(), ignoreCase = true) ||
                    it.domain.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
    }

    val activeDownloads = remember(downloads) {
        downloads.filter {
            it.status == DownloadTaskStatus.DOWNLOADING || it.status == DownloadTaskStatus.QUEUED
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("downloads_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    if (downloads.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAll,
                            modifier = Modifier.testTag("downloads_clear_all_button")
                        ) {
                            Text("Clear History", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            if (activeDownloads.isNotEmpty()) {
                ActiveDownloadsSummaryBanner(activeDownloads = activeDownloads)
            }

            if (downloads.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search downloaded files...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        Text(
                            text = if (downloads.isEmpty()) "No Browser Downloads Yet" else "No Matching Downloads",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Files downloaded with Vortex Download Engine appear here with live progress and pause/resume controls.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { item ->
                        DownloadHistoryRow(
                            item = item,
                            onClick = {
                                if (item.status == DownloadTaskStatus.COMPLETED) {
                                    openDownloadedFile(context, item, onShowMessage)
                                } else {
                                    onShowMessage(
                                        when (item.status) {
                                            DownloadTaskStatus.DOWNLOADING,
                                            DownloadTaskStatus.QUEUED -> "Still downloading ${item.fileName}..."
                                            DownloadTaskStatus.PAUSED -> "Download paused — tap Play to resume"
                                            DownloadTaskStatus.FAILED -> "Download failed — tap Retry"
                                            DownloadTaskStatus.CANCELED -> "Download canceled — tap Retry"
                                            DownloadTaskStatus.COMPLETED -> ""
                                        }
                                    )
                                }
                            },
                            onPause = { onPauseDownload(item.id) },
                            onResume = { onResumeDownload(item.id) },
                            onCancel = { onCancelDownload(item.id) },
                            onRetry = { onRetryDownload(item.id) },
                            onDelete = { onDeleteDownload(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadsSummaryBanner(
    activeDownloads: List<DownloadProgressSnapshot>
) {
    val totalKnownBytes = activeDownloads.sumOf { it.contentLength.coerceAtLeast(0L) }
    val totalDownloadedBytes = activeDownloads.sumOf { it.bytesDownloaded.coerceAtLeast(0L) }
    val allKnown = activeDownloads.all { it.contentLength > 0L } && totalKnownBytes > 0L
    val overallProgress = if (allKnown) {
        (totalDownloadedBytes.toFloat() / totalKnownBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    val totalSpeed = activeDownloads.sumOf { it.speedBytesPerSec.coerceAtLeast(0L) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("active_downloads_xp_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (activeDownloads.size == 1) {
                        "Downloading 1 file..."
                    } else {
                        "Downloading ${activeDownloads.size} files..."
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                val rightLabel = buildString {
                    if (totalSpeed > 0L) {
                        append(formatSpeed(totalSpeed))
                    }
                    if (overallProgress != null) {
                        if (isNotEmpty()) append(" • ")
                        append("${(overallProgress * 100f).toInt()}%")
                    }
                }
                if (rightLabel.isNotBlank()) {
                    Text(
                        text = rightLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            WinXpProgressBar(
                progress = overallProgress,
                height = 14.dp
            )
        }
    }
}

@Composable
private fun DownloadHistoryRow(
    item: DownloadProgressSnapshot,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val isActive = item.status == DownloadTaskStatus.DOWNLOADING || item.status == DownloadTaskStatus.QUEUED
    val isPaused = item.status == DownloadTaskStatus.PAUSED
    val isFailed = item.status == DownloadTaskStatus.FAILED || item.status == DownloadTaskStatus.CANCELED
    val isCompleted = item.status == DownloadTaskStatus.COMPLETED

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("download_item_${item.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isFailed -> MaterialTheme.colorScheme.errorContainer
                        item.isIncognito -> MaterialTheme.colorScheme.tertiaryContainer
                        isCompleted -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isFailed -> Icons.Rounded.ErrorOutline
                            item.isIncognito -> Icons.Rounded.VisibilityOff
                            isCompleted -> Icons.Rounded.CheckCircle
                            else -> Icons.Rounded.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = when {
                            isFailed -> MaterialTheme.colorScheme.error
                            item.isIncognito -> MaterialTheme.colorScheme.onTertiaryContainer
                            isCompleted -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(9.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.isIncognito) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "Private",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    val meta = buildDownloadMetaText(item)
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (item.status) {
                        DownloadTaskStatus.DOWNLOADING,
                        DownloadTaskStatus.QUEUED -> {
                            IconButton(
                                onClick = onPause,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("download_pause_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = "Pause download",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("download_cancel_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Cancel download",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        DownloadTaskStatus.PAUSED -> {
                            IconButton(
                                onClick = onResume,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("download_resume_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Resume download",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("download_cancel_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Cancel download",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        DownloadTaskStatus.FAILED,
                        DownloadTaskStatus.CANCELED -> {
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("download_retry_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Retry download",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("download_delete_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Remove from download history",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        DownloadTaskStatus.COMPLETED -> {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("download_delete_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Remove from download history",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (isActive || isPaused || isFailed) {
                WinXpProgressBar(
                    progress = item.progressFraction,
                    height = 14.dp,
                    isPaused = isPaused,
                    isFailed = isFailed,
                    modifier = Modifier.testTag("download_xp_progress_${item.id}")
                )
            } else if (isCompleted) {
                WinXpProgressBar(
                    progress = 1f,
                    height = 9.dp,
                    modifier = Modifier.testTag("download_xp_progress_${item.id}")
                )
            }
        }
    }
}

private fun buildDownloadMetaText(item: DownloadProgressSnapshot): String {
    return when (item.status) {
        DownloadTaskStatus.QUEUED -> {
            if (item.domain.isNotBlank()) "${item.domain} • Connecting..." else "Connecting..."
        }
        DownloadTaskStatus.DOWNLOADING -> {
            val downloaded = formatBytes(item.bytesDownloaded).ifBlank { "0 KB" }
            val total = formatBytes(item.contentLength)
            buildString {
                if (total.isNotBlank()) {
                    append("$downloaded / $total")
                } else {
                    append(downloaded)
                }
                if (item.speedBytesPerSec > 0L) {
                    append(" • ")
                    append(formatSpeed(item.speedBytesPerSec))
                }
                item.progressPercent?.let { pct ->
                    append(" • $pct%")
                }
            }
        }
        DownloadTaskStatus.PAUSED -> {
            val downloaded = formatBytes(item.bytesDownloaded).ifBlank { "0 KB" }
            val total = formatBytes(item.contentLength)
            buildString {
                append("Paused • ")
                if (total.isNotBlank()) append("$downloaded / $total") else append(downloaded)
                item.progressPercent?.let { pct -> append(" ($pct%)") }
            }
        }
        DownloadTaskStatus.FAILED -> {
            val reason = item.errorMessage.ifBlank { "Failed" }
            if (item.domain.isNotBlank()) "${item.domain} • $reason" else reason
        }
        DownloadTaskStatus.CANCELED -> {
            if (item.domain.isNotBlank()) "${item.domain} • Canceled" else "Canceled"
        }
        DownloadTaskStatus.COMPLETED -> {
            val sizeLabel = formatBytes(
                if (item.contentLength > 0L) item.contentLength else item.bytesDownloaded
            )
            buildString {
                if (item.domain.isNotBlank()) append(item.domain)
                if (sizeLabel.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(sizeLabel)
                }
                if (isNotEmpty()) append(" • ")
                append(DomainUtils.formatTimestamp(item.downloadedAt))
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return ""
    val kb = bytesPerSec / 1024.0
    if (kb < 1024.0) return String.format("%.0f KB/s", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB/s", mb)
}

private fun openDownloadedFile(
    context: Context,
    item: DownloadProgressSnapshot,
    onShowMessage: (String) -> Unit
) {
    val path = item.localFilePath
    if (path.isBlank()) {
        onShowMessage("Downloaded from ${item.domain}")
        return
    }
    val file = File(path)
    if (!file.exists()) {
        onShowMessage("File was deleted or moved from storage")
        return
    }
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, item.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        onShowMessage("No app available to open ${item.fileName}")
    }
}
