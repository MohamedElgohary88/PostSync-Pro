package com.postsyncpro.data.network

import com.postsyncpro.domain.model.DownloadStatus
import com.postsyncpro.domain.model.DownloadableModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelDownloaderSecurityTest {

    @Test
    fun `verified download is atomically published with checksum marker`() = runBlocking {
        val bytes = "verified-model-content".toByteArray()
        val directory = createTempDirectory("postsync-model-").toFile()
        val client = HttpClient(MockEngine {
            respond(
                content = ByteReadChannel(bytes),
                status = HttpStatusCode.OK,
                headers = Headers.build { append(HttpHeaders.ContentLength, bytes.size.toString()) }
            )
        })
        try {
            val model = model(bytes)
            val downloader = ModelDownloader(client, directory)
            val progress = downloader.downloadModel(model).toList()

            assertEquals(DownloadStatus.COMPLETED, progress.last().status)
            assertContentEquals(bytes, directory.resolve(model.fileName).readBytes())
            assertEquals(model.sha256, directory.resolve("${model.fileName}.sha256").readText())
            assertFalse(directory.resolve("${model.fileName}.part").exists())
            assertTrue(downloader.isModelDownloaded(model))
        } finally {
            client.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `resume requires matching content range and appends remaining bytes`() = runBlocking {
        val bytes = "0123456789abcdef".toByteArray()
        val offset = 6
        val directory = createTempDirectory("postsync-model-resume-").toFile()
        val model = model(bytes)
        directory.resolve("${model.fileName}.part").writeBytes(bytes.copyOfRange(0, offset))
        val client = HttpClient(MockEngine { request ->
            assertEquals("bytes=$offset-", request.headers[HttpHeaders.Range])
            respond(
                content = ByteReadChannel(bytes.copyOfRange(offset, bytes.size)),
                status = HttpStatusCode.PartialContent,
                headers = Headers.build {
                    append(HttpHeaders.ContentLength, (bytes.size - offset).toString())
                    append(HttpHeaders.ContentRange, "bytes $offset-${bytes.lastIndex}/${bytes.size}")
                }
            )
        })
        try {
            val downloader = ModelDownloader(client, directory)
            val progress = downloader.downloadModel(model).toList()

            assertEquals(DownloadStatus.COMPLETED, progress.last().status)
            assertContentEquals(bytes, directory.resolve(model.fileName).readBytes())
        } finally {
            client.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `checksum mismatch never publishes final file and removes poisoned partial`() = runBlocking {
        val bytes = "tampered-model".toByteArray()
        val directory = createTempDirectory("postsync-model-bad-").toFile()
        val client = HttpClient(MockEngine {
            respond(
                content = ByteReadChannel(bytes),
                status = HttpStatusCode.OK,
                headers = Headers.build { append(HttpHeaders.ContentLength, bytes.size.toString()) }
            )
        })
        try {
            val model = model(bytes).copy(sha256 = "0".repeat(64))
            val downloader = ModelDownloader(client, directory)
            val progress = downloader.downloadModel(model).toList()

            assertEquals(DownloadStatus.FAILED, progress.last().status)
            assertFalse(directory.resolve(model.fileName).exists())
            assertFalse(directory.resolve("${model.fileName}.part").exists())
        } finally {
            client.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `cancelDownload cancels the active network operation`() = runBlocking {
        val bytes = "slow-model".toByteArray()
        val directory = createTempDirectory("postsync-model-cancel-").toFile()
        val requestStarted = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            requestStarted.complete(Unit)
            delay(60_000)
            respond(ByteReadChannel(bytes), HttpStatusCode.OK)
        })
        try {
            val downloader = ModelDownloader(client, directory)
            val job = launch { downloader.downloadModel(model(bytes)).collect() }
            requestStarted.await()

            downloader.cancelDownload()
            job.join()

            assertTrue(job.isCancelled)
            assertFalse(directory.resolve("model.gguf").exists())
        } finally {
            client.close()
            directory.deleteRecursively()
        }
    }

    private fun model(bytes: ByteArray): DownloadableModel = DownloadableModel(
        id = "test-model",
        name = "Test model",
        description = "test",
        fileName = "model.gguf",
        downloadUrl = "https://example.test/model.gguf",
        sizeBytes = bytes.size.toLong(),
        sizeDisplay = "${bytes.size} B",
        minRamGb = 1,
        quantization = "test",
        parameters = "test",
        contextLength = 128,
        sha256 = sha256(bytes)
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
