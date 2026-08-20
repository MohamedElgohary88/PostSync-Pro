package com.postsyncpro.data.network

import com.postsyncpro.domain.model.DownloadStatus
import com.postsyncpro.domain.model.DownloadableModel
import com.postsyncpro.domain.model.ModelDownloadProgress
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads verified GGUF model files with resumable `.part` files.
 * A completed filename is only published after size and checksum validation.
 */
class ModelDownloader internal constructor(
    private val client: HttpClient,
    modelsDirectory: File,
    private val ownsClient: Boolean
) : AutoCloseable {

    constructor() : this(createHttpClient(), defaultModelsDirectory(), true)

    internal constructor(client: HttpClient, modelsDirectory: File) :
        this(client, modelsDirectory, false)

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_NANOS = 100_000_000L
        const val PROGRESS_BYTE_INTERVAL = 1L * 1024 * 1024
        val CONTENT_RANGE_PATTERN = Regex("""bytes\s+(\d+)-(\d+)/(\d+)""", RegexOption.IGNORE_CASE)

        fun createHttpClient(): HttpClient = HttpClient(CIO) {
            engine {
                requestTimeout = 0
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = 60_000
            }
        }

        fun defaultModelsDirectory(): File =
            File(System.getProperty("user.home"), ".postsyncpro/models")
    }

    private val modelsDir = modelsDirectory.canonicalFile.apply {
        if (!exists() && !mkdirs()) {
            throw IOException("Unable to create the model directory")
        }
        if (!isDirectory) {
            throw IOException("The configured model path is not a directory")
        }
    }
    private val activeDownloads = ConcurrentHashMap<Job, String>()
    private val activeFiles = ConcurrentHashMap.newKeySet<String>()

    fun getModelPath(model: DownloadableModel): String = modelFile(model).absolutePath

    fun isModelDownloaded(model: DownloadableModel): Boolean {
        val file = modelFile(model)
        if (!file.isFile || file.length() != model.sizeBytes) return false
        if (model.sha256.isBlank()) return true

        val marker = checksumMarker(model)
        return marker.isFile && runCatching {
            marker.readText(StandardCharsets.US_ASCII).trim()
                .equals(model.sha256, ignoreCase = true)
        }.getOrDefault(false)
    }

    fun downloadModel(model: DownloadableModel): Flow<ModelDownloadProgress> = flow {
        require(model.sizeBytes > 0) { "Expected model size must be positive" }
        require(model.sha256.isBlank() || model.sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            "Invalid model checksum metadata"
        }

        val outputFile = modelFile(model)
        val partialFile = partialFile(model)
        val job = currentCoroutineContext()[Job]
            ?: throw IllegalStateException("Model download requires a coroutine Job")
        activeDownloads[job] = model.id
        if (!activeFiles.add(outputFile.absolutePath)) {
            activeDownloads.remove(job)
            emit(
                ModelDownloadProgress(
                    modelId = model.id,
                    totalBytes = model.sizeBytes,
                    status = DownloadStatus.FAILED,
                    error = "A download for this model is already running"
                )
            )
            return@flow
        }
        var bytesDownloaded = 0L

        try {
            if (isModelDownloaded(model)) {
                emit(completedProgress(model))
                return@flow
            }

            // Validate a legacy completed file once, then create the marker.
            if (outputFile.isFile && outputFile.length() == model.sizeBytes && verifyChecksum(outputFile, model)) {
                writeChecksumMarker(model)
                emit(completedProgress(model))
                return@flow
            }

            migrateIncompleteOutput(outputFile, partialFile, model.sizeBytes)
            bytesDownloaded = partialFile.takeIf { it.isFile }?.length() ?: 0L
            if (bytesDownloaded > model.sizeBytes) {
                Files.deleteIfExists(partialFile.toPath())
                bytesDownloaded = 0L
            }

            emit(
                ModelDownloadProgress(
                    modelId = model.id,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = model.sizeBytes,
                    progress = progressOf(bytesDownloaded, model.sizeBytes),
                    status = DownloadStatus.DOWNLOADING
                )
            )

            if (bytesDownloaded < model.sizeBytes) {
                val requestedOffset = bytesDownloaded
                client.prepareGet(model.downloadUrl) {
                    header(HttpHeaders.AcceptEncoding, "identity")
                    if (requestedOffset > 0) {
                        header(HttpHeaders.Range, "bytes=$requestedOffset-")
                    }
                }.execute { response ->
                    val append = when {
                        response.status == HttpStatusCode.PartialContent -> {
                            val range = parseAndValidateContentRange(
                                response.headers[HttpHeaders.ContentRange],
                                requestedOffset,
                                model.sizeBytes
                            )
                            bytesDownloaded = range.start
                            range.start > 0
                        }
                        response.status.isSuccess() -> {
                            // A server that ignores Range must restart from byte zero.
                            bytesDownloaded = 0L
                            false
                        }
                        else -> throw IOException("Model server returned HTTP ${response.status.value}")
                    }

                    val expectedRemaining = model.sizeBytes - bytesDownloaded
                    response.contentLength()?.let { contentLength ->
                        if (contentLength != expectedRemaining) {
                            throw DownloadIntegrityException("Unexpected model response length")
                        }
                    }

                    val channel = response.bodyAsChannel()
                    FileOutputStream(partialFile, append).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var lastProgressAt = System.nanoTime()
                        var lastProgressBytes = bytesDownloaded

                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = channel.readAvailable(buffer)
                            if (read == -1) break
                            if (read == 0) {
                                yield()
                                continue
                            }
                            if (bytesDownloaded > model.sizeBytes - read) {
                                throw DownloadIntegrityException("Model response exceeded the expected size")
                            }

                            output.write(buffer, 0, read)
                            bytesDownloaded += read

                            val now = System.nanoTime()
                            if (
                                now - lastProgressAt >= PROGRESS_INTERVAL_NANOS ||
                                bytesDownloaded - lastProgressBytes >= PROGRESS_BYTE_INTERVAL
                            ) {
                                emit(
                                    ModelDownloadProgress(
                                        modelId = model.id,
                                        bytesDownloaded = bytesDownloaded,
                                        totalBytes = model.sizeBytes,
                                        progress = progressOf(bytesDownloaded, model.sizeBytes),
                                        status = DownloadStatus.DOWNLOADING
                                    )
                                )
                                lastProgressAt = now
                                lastProgressBytes = bytesDownloaded
                            }
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }
            }

            currentCoroutineContext().ensureActive()
            if (partialFile.length() != model.sizeBytes) {
                throw IOException("Model download ended before all bytes were received")
            }
            if (!verifyChecksum(partialFile, model)) {
                Files.deleteIfExists(partialFile.toPath())
                throw DownloadIntegrityException("Downloaded model checksum does not match")
            }

            currentCoroutineContext().ensureActive()
            atomicMove(partialFile, outputFile)
            writeChecksumMarker(model)
            emit(completedProgress(model))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is DownloadIntegrityException) {
                Files.deleteIfExists(partialFile.toPath())
            }
            val retainedBytes = partialFile.takeIf { it.isFile }?.length() ?: 0L
            emit(
                ModelDownloadProgress(
                    modelId = model.id,
                    bytesDownloaded = retainedBytes,
                    totalBytes = model.sizeBytes,
                    progress = progressOf(retainedBytes, model.sizeBytes),
                    status = DownloadStatus.FAILED,
                    error = safeErrorMessage(e)
                )
            )
        } finally {
            activeDownloads.remove(job)
            activeFiles.remove(outputFile.absolutePath)
        }
    }.flowOn(Dispatchers.IO)

    fun cancelDownload() {
        activeDownloads.keys.toList().forEach { job ->
            job.cancel(CancellationException("Model download cancelled"))
        }
    }

    fun deleteModel(model: DownloadableModel): Boolean {
        activeDownloads.entries
            .filter { it.value == model.id }
            .forEach { it.key.cancel(CancellationException("Model deleted")) }

        val targets = listOf(modelFile(model), partialFile(model), checksumMarker(model))
        var deleted = false
        targets.forEach { target ->
            if (target.exists()) {
                deleted = target.delete() || deleted
            }
        }
        return deleted
    }

    override fun close() {
        cancelDownload()
        if (ownsClient) client.close()
    }

    private fun modelFile(model: DownloadableModel): File = safeModelChild(model.fileName)

    private fun partialFile(model: DownloadableModel): File = safeModelChild("${model.fileName}.part")

    private fun checksumMarker(model: DownloadableModel): File = safeModelChild("${model.fileName}.sha256")

    private fun safeModelChild(fileName: String): File {
        require(fileName.isNotBlank() && File(fileName).name == fileName) { "Unsafe model filename" }
        val file = File(modelsDir, fileName).canonicalFile
        require(file.toPath().startsWith(modelsDir.toPath())) { "Model path escapes the model directory" }
        return file
    }

    private fun migrateIncompleteOutput(outputFile: File, partialFile: File, expectedSize: Long) {
        if (!outputFile.exists()) return
        if (partialFile.exists()) {
            Files.deleteIfExists(outputFile.toPath())
            return
        }
        if (outputFile.length() in 1 until expectedSize) {
            atomicMove(outputFile, partialFile)
        } else {
            Files.deleteIfExists(outputFile.toPath())
        }
    }

    private fun parseAndValidateContentRange(value: String?, requestedOffset: Long, expectedSize: Long): ContentRange {
        val match = value?.let { CONTENT_RANGE_PATTERN.matchEntire(it.trim()) }
            ?: throw DownloadIntegrityException("Missing or invalid Content-Range")
        val start = match.groupValues[1].toLongOrNull()
            ?: throw DownloadIntegrityException("Invalid Content-Range start")
        val end = match.groupValues[2].toLongOrNull()
            ?: throw DownloadIntegrityException("Invalid Content-Range end")
        val total = match.groupValues[3].toLongOrNull()
            ?: throw DownloadIntegrityException("Invalid Content-Range total")
        if (start != requestedOffset || end < start || end >= total || total != expectedSize) {
            throw DownloadIntegrityException("Content-Range does not match the partial file")
        }
        return ContentRange(start, end, total)
    }

    private fun verifyChecksum(file: File, model: DownloadableModel): Boolean {
        if (model.sha256.isBlank()) return true
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return actual.equals(model.sha256, ignoreCase = true)
    }

    private fun writeChecksumMarker(model: DownloadableModel) {
        if (model.sha256.isBlank()) return
        val marker = checksumMarker(model)
        val temporary = Files.createTempFile(modelsDir.toPath(), ".checksum-", ".tmp")
        try {
            Files.writeString(
                temporary,
                model.sha256.lowercase(),
                StandardCharsets.US_ASCII,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            try {
                Files.move(
                    temporary,
                    marker.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, marker.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun atomicMove(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun completedProgress(model: DownloadableModel) = ModelDownloadProgress(
        modelId = model.id,
        bytesDownloaded = model.sizeBytes,
        totalBytes = model.sizeBytes,
        progress = 1f,
        status = DownloadStatus.COMPLETED
    )

    private fun progressOf(bytes: Long, total: Long): Float =
        if (total <= 0) 0f else (bytes.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)

    private fun safeErrorMessage(error: Exception): String = when (error) {
        is DownloadIntegrityException -> error.message ?: "Model integrity validation failed"
        is IOException -> error.message?.take(200) ?: "Model download failed"
        else -> "Model download failed"
    }

    private data class ContentRange(val start: Long, val end: Long, val total: Long)

    private class DownloadIntegrityException(message: String) : IOException(message)
}
