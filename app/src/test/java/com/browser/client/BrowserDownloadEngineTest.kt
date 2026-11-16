package com.browser.client

import android.app.Application
import android.content.Context
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browser.client.data.local.ClearPassDatabase
import com.browser.client.data.local.DownloadEntity
import com.browser.client.data.repository.BrowserDataRepository
import com.browser.client.engine.BrowserDownloadEngine
import com.browser.client.engine.DownloadProgressSnapshot
import com.browser.client.engine.DownloadTaskStatus
import com.browser.client.engine.toSnapshot
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrowserDownloadEngineTest {

    private lateinit var context: Context
    private lateinit var db: ClearPassDatabase
    private lateinit var repository: BrowserDataRepository
    private lateinit var engineScope: CoroutineScope
    private lateinit var engine: BrowserDownloadEngine
    private var server: HttpServer? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, ClearPassDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BrowserDataRepository(
            bookmarkDao = db.bookmarkDao(),
            historyDao = db.historyDao(),
            downloadDao = db.downloadDao(),
            searchHistoryDao = db.searchHistoryDao()
        )
        engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        engine = BrowserDownloadEngine(
            context = context,
            repository = repository,
            scope = engineScope,
            cookieProvider = { null }
        )
    }

    @After
    fun tearDown() {
        server?.stop(0)
        engineScope.cancel()
        db.close()
        File(context.filesDir, "browser_downloads").deleteRecursively()
        File(context.filesDir, "incognito_downloads").deleteRecursively()
    }

    @Test
    fun `sanitizeFileName strips traversal and invalid characters`() {
        assertEquals("report.pdf", BrowserDownloadEngine.sanitizeFileName("../../secret/report.pdf"))
        val fallback = BrowserDownloadEngine.sanitizeFileName("///..")
        assertTrue(fallback.startsWith("download_"))
    }

    @Test
    fun `resolveUniqueTargetFile appends counter on collision`() {
        val dir = File(context.filesDir, "browser_downloads").apply { mkdirs() }
        val first = BrowserDownloadEngine.resolveUniqueTargetFile(dir, "archive.zip")
        assertEquals("archive.zip", first.name)
        first.writeText("one")

        val second = BrowserDownloadEngine.resolveUniqueTargetFile(dir, "archive.zip")
        assertEquals("archive (1).zip", second.name)
    }

    @Test
    fun `streaming download completes, persists to Room, and resolves via FileProvider`() = runBlocking {
        val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/sample.bin") { exchange ->
                exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            }
            start()
        }
        server = httpServer
        val url = "http://127.0.0.1:${httpServer.address.port}/sample.bin"

        engine.startDownload(
            fileName = "sample.bin",
            url = url,
            userAgent = "ClearPassTest/1.0",
            mimeType = "application/octet-stream",
            contentLength = payload.size.toLong(),
            isIncognito = false
        )

        val entity = waitUntil(timeoutMs = 5000) {
            repository.getAllDownloadsOnce()
                .firstOrNull { it.status == DownloadTaskStatus.COMPLETED.name }
        }
        assertNotNull(entity)
        val saved = entity!!
        assertEquals("sample.bin", saved.fileName)
        assertEquals(payload.size.toLong(), saved.bytesDownloaded)
        assertEquals(payload.size.toLong(), saved.contentLength)
        val localFile = File(saved.localFilePath)
        assertTrue(localFile.exists())
        assertEquals(payload.size, localFile.readBytes().size)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            localFile
        )
        assertTrue(uri.toString().contains("sample.bin"))
    }

    @Test
    fun `incognito download stays out of Room and shreds cleanly`() = runBlocking {
        val payload = "private-secret-payload".toByteArray()
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/secret.txt") { exchange ->
                exchange.responseHeaders.add("Content-Type", "text/plain")
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            }
            start()
        }
        server = httpServer
        val url = "http://127.0.0.1:${httpServer.address.port}/secret.txt"

        engine.startDownload(
            fileName = "secret.txt",
            url = url,
            userAgent = "ClearPassTest/1.0",
            mimeType = "text/plain",
            contentLength = payload.size.toLong(),
            isIncognito = true
        )

        val snapshot = waitUntil(timeoutMs = 5000) {
            engine.incognitoDownloads.value.firstOrNull {
                it.status == DownloadTaskStatus.COMPLETED
            }
        }
        assertNotNull(snapshot)
        assertTrue(snapshot!!.isIncognito)
        assertTrue(snapshot.localFilePath.contains("incognito_downloads"))
        val incFile = File(snapshot.localFilePath)
        assertTrue(incFile.exists())
        assertTrue(repository.getAllDownloadsOnce().isEmpty())

        engine.shredIncognitoDownloads()
        val cleared = waitUntil(timeoutMs = 3000) {
            if (engine.incognitoDownloads.value.isEmpty() && !incFile.exists()) true else null
        }
        assertTrue(cleared == true)
    }

    @Test
    fun `progressFraction and toSnapshot handle determinate and unknown content lengths`() {
        val indeterminate = DownloadProgressSnapshot(
            id = 1L,
            fileName = "stream.iso",
            url = "https://example.com/stream.iso",
            domain = "example.com",
            mimeType = "application/octet-stream",
            bytesDownloaded = 2048L,
            contentLength = -1L,
            speedBytesPerSec = 512L,
            status = DownloadTaskStatus.DOWNLOADING,
            localFilePath = "",
            isIncognito = false,
            downloadedAt = 1000L
        )
        assertNull(indeterminate.progressFraction)
        assertNull(indeterminate.progressPercent)

        val entity = DownloadEntity(
            id = 2L,
            fileName = "doc.pdf",
            url = "https://example.com/doc.pdf",
            domain = "example.com",
            mimeType = "application/pdf",
            contentLength = 1000L,
            bytesDownloaded = 420L,
            status = "PAUSED"
        )
        val snap = entity.toSnapshot()
        assertEquals(DownloadTaskStatus.PAUSED, snap.status)
        assertEquals(42, snap.progressPercent)
        assertFalse(snap.isIncognito)
    }

    private suspend fun <T> waitUntil(timeoutMs: Long, block: suspend () -> T?): T? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val result = block()
            if (result != null) return result
            kotlinx.coroutines.delay(50)
        }
        return null
    }
}
