package com.browser.client.engine

import android.content.Context
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import com.browser.client.data.local.DownloadEntity
import com.browser.client.data.repository.BrowserDataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class DownloadTaskStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED;

    companion object {
        fun fromRaw(raw: String): DownloadTaskStatus {
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: COMPLETED
        }
    }
}

data class DownloadProgressSnapshot(
    val id: Long,
    val fileName: String,
    val url: String,
    val domain: String,
    val mimeType: String,
    val bytesDownloaded: Long,
    val contentLength: Long,
    val speedBytesPerSec: Long,
    val status: DownloadTaskStatus,
    val localFilePath: String,
    val isIncognito: Boolean,
    val downloadedAt: Long,
    val errorMessage: String = ""
) {
    val progressFraction: Float?
        get() = when {
            status == DownloadTaskStatus.COMPLETED -> 1f
            contentLength > 0L -> (bytesDownloaded.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
            else -> null
        }

    val progressPercent: Int?
        get() = progressFraction?.let { (it * 100f).toInt().coerceIn(0, 100) }
}

private enum class DownloadControlAction {
    PAUSE,
    CANCEL
}

class BrowserDownloadEngine(
    private val context: Context,
    private val repository: BrowserDataRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val cookieProvider: (String) -> String? = { targetUrl ->
        runCatching { CookieManager.getInstance().getCookie(targetUrl) }.getOrNull()
    }
) {
    private val _activeDownloads = MutableStateFlow<Map<Long, DownloadProgressSnapshot>>(emptyMap())
    val activeDownloads: StateFlow<Map<Long, DownloadProgressSnapshot>> = _activeDownloads.asStateFlow()

    private val _incognitoDownloads = MutableStateFlow<List<DownloadProgressSnapshot>>(emptyList())
    val incognitoDownloads: StateFlow<List<DownloadProgressSnapshot>> = _incognitoDownloads.asStateFlow()

    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val controlActions = ConcurrentHashMap<Long, DownloadControlAction>()
    private val userAgents = ConcurrentHashMap<Long, String>()
    private val incognitoIdGen = AtomicLong(-100L)

    private val normalDir: File
        get() = File(context.filesDir, "browser_downloads").apply { mkdirs() }

    private val incognitoDir: File
        get() = File(context.filesDir, "incognito_downloads").apply { mkdirs() }

    init {
        scope.launch {
            reconcileInterruptedDownloads()
        }
    }

    suspend fun reconcileInterruptedDownloads() {
        val all = runCatching { repository.getAllDownloadsOnce() }.getOrElse { emptyList() }
        all.forEach { entity ->
            val st = DownloadTaskStatus.fromRaw(entity.status)
            if ((st == DownloadTaskStatus.DOWNLOADING || st == DownloadTaskStatus.QUEUED) &&
                !activeJobs.containsKey(entity.id)
            ) {
                val partFile = if (entity.localFilePath.isNotBlank()) {
                    File("${entity.localFilePath}.part")
                } else {
                    null
                }
                val hasPartial = partFile != null && partFile.exists() && partFile.length() > 0L
                val nextStatus = if (hasPartial) DownloadTaskStatus.PAUSED else DownloadTaskStatus.FAILED
                val nextBytes = if (hasPartial) partFile!!.length() else entity.bytesDownloaded
                repository.updateDownloadState(
                    id = entity.id,
                    fileName = entity.fileName,
                    mimeType = entity.mimeType,
                    bytesDownloaded = nextBytes,
                    contentLength = entity.contentLength,
                    status = nextStatus.name,
                    localFilePath = entity.localFilePath,
                    errorMessage = if (hasPartial) "" else "Download interrupted"
                )
            }
        }
    }

    fun startDownload(
        fileName: String,
        url: String,
        userAgent: String,
        mimeType: String,
        contentLength: Long,
        isIncognito: Boolean
    ) {
        val sanitizedName = sanitizeFileName(fileName)
        val cleanMime = normalizeMimeType(mimeType, sanitizedName)
        val domain = DomainUtils.extractHost(url)
        val now = System.currentTimeMillis()

        scope.launch {
            val baseDir = if (isIncognito) incognitoDir else normalDir
            val targetFile = resolveUniqueTargetFile(baseDir, sanitizedName)
            val id = if (isIncognito) {
                incognitoIdGen.decrementAndGet()
            } else {
                repository.createDownloadRecord(
                    fileName = targetFile.name,
                    url = url,
                    mimeType = cleanMime,
                    contentLength = contentLength,
                    localFilePath = targetFile.absolutePath,
                    status = DownloadTaskStatus.QUEUED.name
                )
            }

            userAgents[id] = userAgent
            val initialSnapshot = DownloadProgressSnapshot(
                id = id,
                fileName = targetFile.name,
                url = url,
                domain = domain,
                mimeType = cleanMime,
                bytesDownloaded = 0L,
                contentLength = contentLength,
                speedBytesPerSec = 0L,
                status = DownloadTaskStatus.QUEUED,
                localFilePath = targetFile.absolutePath,
                isIncognito = isIncognito,
                downloadedAt = now
            )
            publishSnapshot(initialSnapshot)
            launchTransferJob(initialSnapshot, userAgent, resumeFromPartial = false)
        }
    }

    fun pauseDownload(id: Long) {
        if (!activeJobs.containsKey(id)) return
        controlActions[id] = DownloadControlAction.PAUSE
        activeJobs[id]?.cancel()
    }

    fun cancelDownload(id: Long) {
        if (activeJobs.containsKey(id)) {
            controlActions[id] = DownloadControlAction.CANCEL
            activeJobs[id]?.cancel()
        } else {
            scope.launch {
                val existing = findSnapshotOrEntity(id) ?: return@launch
                if (existing.localFilePath.isNotBlank()) {
                    runCatching { File("${existing.localFilePath}.part").delete() }
                }
                val updated = existing.copy(
                    status = DownloadTaskStatus.CANCELED,
                    speedBytesPerSec = 0L,
                    errorMessage = "Canceled"
                )
                publishSnapshot(updated)
                persistIfNormal(updated)
                if (!updated.isIncognito) {
                    _activeDownloads.update { it - id }
                }
            }
        }
    }

    fun resumeDownload(id: Long) {
        scope.launch {
            if (activeJobs.containsKey(id)) return@launch
            val existing = findSnapshotOrEntity(id) ?: return@launch
            if (existing.status == DownloadTaskStatus.COMPLETED) return@launch

            val ua = userAgents[id].orEmpty()
            val partFile = if (existing.localFilePath.isNotBlank()) File("${existing.localFilePath}.part") else null
            val currentBytes = if (partFile != null && partFile.exists()) partFile.length() else 0L
            val resumed = existing.copy(
                bytesDownloaded = currentBytes,
                status = DownloadTaskStatus.QUEUED,
                speedBytesPerSec = 0L,
                errorMessage = ""
            )
            publishSnapshot(resumed)
            persistIfNormal(resumed)
            launchTransferJob(resumed, ua, resumeFromPartial = currentBytes > 0L)
        }
    }

    fun retryDownload(id: Long) {
        scope.launch {
            if (activeJobs.containsKey(id)) return@launch
            val existing = findSnapshotOrEntity(id) ?: return@launch
            val ua = userAgents[id].orEmpty()
            val targetPath = existing.localFilePath.ifBlank {
                val baseDir = if (existing.isIncognito) incognitoDir else normalDir
                resolveUniqueTargetFile(baseDir, sanitizeFileName(existing.fileName)).absolutePath
            }
            val partFile = File("$targetPath.part")
            val hasPartial = existing.status != DownloadTaskStatus.CANCELED && partFile.exists() && partFile.length() > 0L
            if (!hasPartial) {
                runCatching { partFile.delete() }
            }
            val resetSnapshot = existing.copy(
                localFilePath = targetPath,
                bytesDownloaded = if (hasPartial) partFile.length() else 0L,
                status = DownloadTaskStatus.QUEUED,
                speedBytesPerSec = 0L,
                errorMessage = ""
            )
            publishSnapshot(resetSnapshot)
            persistIfNormal(resetSnapshot)
            launchTransferJob(resetSnapshot, ua, resumeFromPartial = hasPartial)
        }
    }

    fun deleteDownload(id: Long) {
        if (activeJobs.containsKey(id)) {
            controlActions[id] = DownloadControlAction.CANCEL
            activeJobs[id]?.cancel()
        }
        scope.launch {
            val existing = findSnapshotOrEntity(id)
            if (existing != null && existing.localFilePath.isNotBlank()) {
                runCatching { File(existing.localFilePath).delete() }
                runCatching { File("${existing.localFilePath}.part").delete() }
            }
            _activeDownloads.update { it - id }
            _incognitoDownloads.update { list -> list.filterNot { it.id == id } }
            if (id > 0L) {
                repository.deleteDownloadItem(id)
            }
        }
    }

    fun clearDownloadsHistory() {
        val activeNormalIds = _activeDownloads.value.values
            .filter { !it.isIncognito && (it.status == DownloadTaskStatus.DOWNLOADING || it.status == DownloadTaskStatus.QUEUED) }
            .map { it.id }
            .toSet()

        activeNormalIds.forEach { id ->
            controlActions[id] = DownloadControlAction.CANCEL
            activeJobs[id]?.cancel()
        }

        scope.launch {
            val all = runCatching { repository.getAllDownloadsOnce() }.getOrElse { emptyList() }
            all.forEach { entity ->
                if (entity.localFilePath.isNotBlank()) {
                    runCatching { File(entity.localFilePath).delete() }
                    runCatching { File("${entity.localFilePath}.part").delete() }
                }
            }
            _activeDownloads.update { current ->
                current.filterValues { it.isIncognito }
            }
            repository.clearDownloadsHistory()
        }
    }

    fun shredIncognitoDownloads() {
        val incSnapshots = _incognitoDownloads.value
        incSnapshots.forEach { snap ->
            if (activeJobs.containsKey(snap.id)) {
                controlActions[snap.id] = DownloadControlAction.CANCEL
                activeJobs[snap.id]?.cancel()
            }
        }
        _incognitoDownloads.value = emptyList()
        _activeDownloads.update { current -> current.filterValues { !it.isIncognito } }
        scope.launch {
            runCatching {
                val dir = File(context.filesDir, "incognito_downloads")
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }
        }
    }

    private fun launchTransferJob(
        initial: DownloadProgressSnapshot,
        userAgent: String,
        resumeFromPartial: Boolean
    ) {
        val job = scope.launch {
            val id = initial.id
            var latest = initial.copy(status = DownloadTaskStatus.DOWNLOADING, errorMessage = "")
            val targetFile = File(latest.localFilePath)
            targetFile.parentFile?.mkdirs()
            val partFile = File("${latest.localFilePath}.part")
            var existingBytes = if (resumeFromPartial && partFile.exists()) partFile.length() else 0L
            var connection: HttpURLConnection? = null

            try {
                publishSnapshot(latest)
                persistIfNormal(latest)

                val opened = openConnectionFollowingRedirects(
                    initialUrl = latest.url,
                    userAgent = userAgent,
                    rangeStart = existingBytes,
                    isIncognito = latest.isIncognito
                )
                connection = opened

                val responseCode = opened.responseCode
                if (responseCode !in 200..299) {
                    throw IOException("HTTP $responseCode ${opened.responseMessage.orEmpty()}".trim())
                }

                val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL && existingBytes > 0L
                if (!isPartial) {
                    existingBytes = 0L
                }

                val headerLength = opened.contentLengthLongCompat()
                val totalLength = when {
                    isPartial && headerLength > 0L -> existingBytes + headerLength
                    headerLength > 0L -> headerLength
                    else -> latest.contentLength
                }

                val serverMime = opened.contentType
                    ?.substringBefore(';')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                val finalMime = serverMime ?: latest.mimeType

                latest = latest.copy(
                    mimeType = finalMime,
                    bytesDownloaded = existingBytes,
                    contentLength = totalLength,
                    status = DownloadTaskStatus.DOWNLOADING
                )
                publishSnapshot(latest)
                persistIfNormal(latest)

                var totalWritten = existingBytes
                var windowBytes = 0L
                var windowStartNs = System.nanoTime()
                var lastEmitMs = System.currentTimeMillis()
                var currentSpeed = 0L

                opened.inputStream.use { input ->
                    FileOutputStream(partFile, isPartial).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            totalWritten += read
                            windowBytes += read

                            val nowNs = System.nanoTime()
                            val elapsedNs = nowNs - windowStartNs
                            if (elapsedNs >= 450_000_000L) {
                                currentSpeed = ((windowBytes * 1_000_000_000L) / elapsedNs).coerceAtLeast(0L)
                                windowBytes = 0L
                                windowStartNs = nowNs
                            }

                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastEmitMs >= 150L) {
                                lastEmitMs = nowMs
                                latest = latest.copy(
                                    bytesDownloaded = totalWritten,
                                    contentLength = totalLength,
                                    speedBytesPerSec = currentSpeed
                                )
                                publishSnapshot(latest)
                            }
                        }
                        output.flush()
                    }
                }

                if (!isActive) {
                    throw CancellationException("Download job canceled")
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!partFile.renameTo(targetFile)) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }

                val finalLength = if (totalLength > 0L) totalLength else totalWritten
                val completed = latest.copy(
                    bytesDownloaded = totalWritten,
                    contentLength = finalLength,
                    speedBytesPerSec = 0L,
                    status = DownloadTaskStatus.COMPLETED,
                    errorMessage = ""
                )
                publishSnapshot(completed)
                persistIfNormal(completed)
                if (!completed.isIncognito) {
                    _activeDownloads.update { it - id }
                }
            } catch (ce: CancellationException) {
                val action = controlActions.remove(id)
                when (action) {
                    DownloadControlAction.PAUSE -> {
                        val pausedBytes = if (partFile.exists()) partFile.length() else latest.bytesDownloaded
                        val paused = latest.copy(
                            bytesDownloaded = pausedBytes,
                            speedBytesPerSec = 0L,
                            status = DownloadTaskStatus.PAUSED,
                            errorMessage = ""
                        )
                        publishSnapshot(paused)
                        persistIfNormal(paused)
                        if (!paused.isIncognito) {
                            _activeDownloads.update { it - id }
                        }
                    }
                    DownloadControlAction.CANCEL -> {
                        runCatching { partFile.delete() }
                        val canceled = latest.copy(
                            speedBytesPerSec = 0L,
                            status = DownloadTaskStatus.CANCELED,
                            errorMessage = "Canceled"
                        )
                        publishSnapshot(canceled)
                        persistIfNormal(canceled)
                        if (!canceled.isIncognito) {
                            _activeDownloads.update { it - id }
                        }
                    }
                    null -> throw ce
                }
            } catch (ex: Exception) {
                val failedBytes = if (partFile.exists()) partFile.length() else latest.bytesDownloaded
                val failed = latest.copy(
                    bytesDownloaded = failedBytes,
                    speedBytesPerSec = 0L,
                    status = DownloadTaskStatus.FAILED,
                    errorMessage = ex.message?.takeIf { it.isNotBlank() } ?: "Download failed"
                )
                publishSnapshot(failed)
                persistIfNormal(failed)
                if (!failed.isIncognito) {
                    _activeDownloads.update { it - id }
                }
            } finally {
                runCatching { connection?.disconnect() }
                activeJobs.remove(id)
                controlActions.remove(id)
            }
        }
        activeJobs[initial.id] = job
    }

    private fun openConnectionFollowingRedirects(
        initialUrl: String,
        userAgent: String,
        rangeStart: Long,
        isIncognito: Boolean
    ): HttpURLConnection {
        var currentUrl = initialUrl
        repeat(MAX_REDIRECTS + 1) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
                if (userAgent.isNotBlank()) {
                    setRequestProperty("User-Agent", userAgent)
                }
                if (rangeStart > 0L) {
                    setRequestProperty("Range", "bytes=$rangeStart-")
                }
                if (isIncognito) {
                    setRequestProperty("DNT", "1")
                    setRequestProperty("Sec-GPC", "1")
                }
                val cookies = cookieProvider(currentUrl)
                if (!cookies.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookies)
                }
            }

            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("Redirect without Location header")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
            } else {
                return conn
            }
        }
        throw IOException("Too many redirects")
    }

    private suspend fun persistIfNormal(snapshot: DownloadProgressSnapshot) {
        if (snapshot.isIncognito || snapshot.id <= 0L) return
        repository.updateDownloadState(
            id = snapshot.id,
            fileName = snapshot.fileName,
            mimeType = snapshot.mimeType,
            bytesDownloaded = snapshot.bytesDownloaded,
            contentLength = snapshot.contentLength,
            status = snapshot.status.name,
            localFilePath = snapshot.localFilePath,
            errorMessage = snapshot.errorMessage
        )
    }

    private fun publishSnapshot(snapshot: DownloadProgressSnapshot) {
        _activeDownloads.update { current -> current + (snapshot.id to snapshot) }
        if (snapshot.isIncognito) {
            _incognitoDownloads.update { list ->
                val without = list.filterNot { it.id == snapshot.id }
                listOf(snapshot) + without
            }
        }
    }

    private suspend fun findSnapshotOrEntity(id: Long): DownloadProgressSnapshot? {
        _activeDownloads.value[id]?.let { return it }
        _incognitoDownloads.value.firstOrNull { it.id == id }?.let { return it }
        if (id > 0L) {
            return repository.getDownloadById(id)?.toSnapshot()
        }
        return null
    }

    companion object {
        private const val MAX_REDIRECTS = 5

        fun sanitizeFileName(raw: String): String {
            val cleaned = raw
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("[^a-zA-Z0-9._\\-() ]"), "_")
                .trim()
                .trimStart('.')
            return cleaned.ifBlank { "download_${System.currentTimeMillis()}" }
        }

        fun normalizeMimeType(rawMime: String, fileName: String): String {
            val trimmed = rawMime.substringBefore(';').trim()
            if (trimmed.isNotBlank() && trimmed != "application/octet-stream") {
                return trimmed
            }
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext.isNotBlank()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
            }
            return trimmed.ifBlank { "application/octet-stream" }
        }

        fun resolveUniqueTargetFile(directory: File, fileName: String): File {
            directory.mkdirs()
            val candidate = File(directory, fileName)
            if (!candidate.exists() && !File("${candidate.absolutePath}.part").exists()) {
                return candidate
            }
            val dotIndex = fileName.lastIndexOf('.')
            val base = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
            val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""
            var counter = 1
            while (counter < 1000) {
                val next = File(directory, "$base ($counter)$ext")
                if (!next.exists() && !File("${next.absolutePath}.part").exists()) {
                    return next
                }
                counter++
            }
            return File(directory, "${base}_${System.currentTimeMillis()}$ext")
        }
    }
}

private fun HttpURLConnection.contentLengthLongCompat(): Long {
    val header = getHeaderField("Content-Length")?.trim()?.toLongOrNull()
    if (header != null && header >= 0L) return header
    return contentLength.toLong()
}

fun DownloadEntity.toSnapshot(): DownloadProgressSnapshot {
    val parsedStatus = DownloadTaskStatus.fromRaw(status)
    val resolvedBytes = when {
        bytesDownloaded > 0L -> bytesDownloaded
        parsedStatus == DownloadTaskStatus.COMPLETED && contentLength > 0L -> contentLength
        else -> 0L
    }
    return DownloadProgressSnapshot(
        id = id,
        fileName = fileName,
        url = url,
        domain = domain,
        mimeType = mimeType,
        bytesDownloaded = resolvedBytes,
        contentLength = contentLength,
        speedBytesPerSec = 0L,
        status = parsedStatus,
        localFilePath = localFilePath,
        isIncognito = false,
        downloadedAt = downloadedAt,
        errorMessage = errorMessage
    )
}
