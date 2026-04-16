package com.postsyncpro.data.network

import com.postsyncpro.domain.model.DownloadStatus
import com.postsyncpro.domain.model.DownloadableModel
import com.postsyncpro.domain.model.ModelDownloadProgress
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * Handles downloading AI model GGUF files from HuggingFace.
 * Supports progress tracking, cancellation, and resume (via Range headers).
 */
class ModelDownloader {

    private val client = HttpClient(CIO) {
        engine {
            requestTimeout = 0 // No timeout for large downloads
        }
    }

    private var downloadJob: Job? = null
    private val modelsDir: File by lazy {
        val dir = File(System.getProperty("user.home"), ".postsyncpro/models")
        dir.mkdirs()
        dir
    }

    /**
     * Returns the path where a model would be stored.
     */
    fun getModelPath(model: DownloadableModel): String {
        return File(modelsDir, model.fileName).absolutePath
    }

    /**
     * Checks if a model file already exists and is complete.
     */
    fun isModelDownloaded(model: DownloadableModel): Boolean {
        val file = File(modelsDir, model.fileName)
        return file.exists() && file.length() > model.sizeBytes * 0.95
    }

    /**
     * Downloads a model file with progress tracking.
     * Emits ModelDownloadProgress updates via Flow.
     */
    fun downloadModel(model: DownloadableModel): Flow<ModelDownloadProgress> = flow {
        val outputFile = File(modelsDir, model.fileName)
        var bytesDownloaded = 0L

        emit(ModelDownloadProgress(
            modelId = model.id,
            status = DownloadStatus.DOWNLOADING
        ))

        try {
            // Check for partial download (resume support)
            if (outputFile.exists()) {
                bytesDownloaded = outputFile.length()
            }

            client.prepareGet(model.downloadUrl) {
                if (bytesDownloaded > 0) {
                    header(HttpHeaders.Range, "bytes=$bytesDownloaded-")
                }
                // Follow redirects (HuggingFace uses them)
            }.execute { response ->
                val totalBytes = when {
                    response.status == HttpStatusCode.PartialContent -> {
                        val contentRange = response.headers[HttpHeaders.ContentRange]
                        contentRange?.substringAfter("/")?.toLongOrNull() ?: model.sizeBytes
                    }
                    else -> {
                        bytesDownloaded = 0 // Server doesn't support Range, restart
                        response.contentLength() ?: model.sizeBytes
                    }
                }

                val channel: ByteReadChannel = response.bodyAsChannel()
                val fileOutputStream = if (bytesDownloaded > 0 && response.status == HttpStatusCode.PartialContent) {
                    outputFile.outputStream().apply {
                        // Seek to end for append
                        java.io.RandomAccessFile(outputFile, "rw").apply {
                            seek(bytesDownloaded)
                            close()
                        }
                    }
                    java.io.FileOutputStream(outputFile, true)
                } else {
                    java.io.FileOutputStream(outputFile, false)
                }

                fileOutputStream.use { fos ->
                    val buffer = ByteArray(8192)
                    while (!channel.isClosedForRead) {
                        val bytesRead = channel.readAvailable(buffer)
                        if (bytesRead <= 0) continue
                        fos.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        val progress = if (totalBytes > 0) {
                            bytesDownloaded.toFloat() / totalBytes
                        } else 0f

                        emit(ModelDownloadProgress(
                            modelId = model.id,
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = totalBytes,
                            progress = progress.coerceIn(0f, 1f),
                            status = DownloadStatus.DOWNLOADING
                        ))
                    }
                }

                emit(ModelDownloadProgress(
                    modelId = model.id,
                    bytesDownloaded = bytesDownloaded,
                    totalBytes = totalBytes,
                    progress = 1f,
                    status = DownloadStatus.COMPLETED
                ))
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            emit(ModelDownloadProgress(
                modelId = model.id,
                bytesDownloaded = bytesDownloaded,
                totalBytes = model.sizeBytes,
                progress = bytesDownloaded.toFloat() / model.sizeBytes,
                status = DownloadStatus.PAUSED
            ))
            throw e
        } catch (e: Exception) {
            emit(ModelDownloadProgress(
                modelId = model.id,
                bytesDownloaded = bytesDownloaded,
                totalBytes = model.sizeBytes,
                progress = bytesDownloaded.toFloat() / model.sizeBytes,
                status = DownloadStatus.FAILED,
                error = e.message ?: "Download failed"
            ))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Cancels an active download.
     */
    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /**
     * Deletes a downloaded model file.
     */
    fun deleteModel(model: DownloadableModel): Boolean {
        val file = File(modelsDir, model.fileName)
        return if (file.exists()) file.delete() else false
    }
}
