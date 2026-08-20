package com.postsyncpro.presentation.screen.sync

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.text.Normalizer
import java.util.Locale

data class SyncState(
    val oldCollectionFile: File? = null,
    val newSourceFile: File? = null,
    val oldCollection: PostmanCollection? = null,
    val newCollection: PostmanCollection? = null,
    val newSwagger: SwaggerSpec? = null,
    val mergeResult: MergeResult? = null,
    val logs: List<MergeLog> = emptyList(),
    val isLoading: Boolean = false,
    val isMerging: Boolean = false,
    val error: String? = null,
    val parseStatus: String = ""
)

class SyncViewModel(
    private val mergeUseCase: MergeCollectionsUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()
    private var oldCollectionLoadJob: Job? = null
    private var newSourceLoadJob: Job? = null
    private var mergeJob: Job? = null

    fun selectOldCollection(file: File) {
        oldCollectionLoadJob?.cancel()
        mergeJob?.cancel()
        oldCollectionLoadJob = scope.launch {
            _state.value = _state.value.copy(
                oldCollectionFile = file,
                oldCollection = null,
                mergeResult = null,
                logs = emptyList(),
                isLoading = true,
                isMerging = false,
                error = null
            )
            try {
                val (json, collection) = withContext(Dispatchers.IO) {
                    val json = file.readText()
                    val collection = mergeUseCase.parsePostmanCollection(json)
                    json to collection
                }
                _state.value = _state.value.copy(
                    oldCollection = collection,
                    isLoading = false,
                    parseStatus = "Old collection loaded: ${collection.info.name} (${countEndpoints(collection.item)} endpoints)"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to parse old collection: ${e.message}")
            }
        }
    }

    fun clearOldCollection() {
        oldCollectionLoadJob?.cancel()
        oldCollectionLoadJob = null
        mergeJob?.cancel()
        mergeJob = null
        _state.value = _state.value.copy(
            oldCollectionFile = null,
            oldCollection = null,
            mergeResult = null,
            logs = emptyList(),
            isLoading = newSourceLoadJob?.isActive == true,
            isMerging = false,
            error = null,
            parseStatus = removeParseStatus(_state.value.parseStatus, "Old collection loaded:")
        )
    }

    fun selectNewSource(file: File) {
        newSourceLoadJob?.cancel()
        mergeJob?.cancel()
        newSourceLoadJob = scope.launch {
            _state.value = _state.value.copy(
                newSourceFile = file,
                newCollection = null,
                newSwagger = null,
                mergeResult = null,
                logs = emptyList(),
                isLoading = true,
                isMerging = false,
                error = null
            )
            try {
                val json = withContext(Dispatchers.IO) { file.readText() }
                // Try parsing as Swagger first, then as Postman
                try {
                    val swagger = withContext(Dispatchers.IO) { mergeUseCase.parseSwaggerSpec(json) }
                    if (swagger.paths.isNotEmpty()) {
                        _state.value = _state.value.copy(
                            newSwagger = swagger, newCollection = null, isLoading = false,
                            parseStatus = _state.value.parseStatus + "\nNew source loaded: Swagger - ${swagger.info.title} (${swagger.paths.size} paths)"
                        )
                        return@launch
                    }
                } catch (_: Exception) { }
                val collection = withContext(Dispatchers.IO) { mergeUseCase.parsePostmanCollection(json) }
                _state.value = _state.value.copy(
                    newCollection = collection, newSwagger = null, isLoading = false,
                    parseStatus = _state.value.parseStatus + "\nNew source loaded: Postman - ${collection.info.name}"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to parse new source: ${e.message}")
            }
        }
    }

    fun clearNewSource() {
        newSourceLoadJob?.cancel()
        newSourceLoadJob = null
        mergeJob?.cancel()
        mergeJob = null
        _state.value = _state.value.copy(
            newSourceFile = null,
            newCollection = null,
            newSwagger = null,
            mergeResult = null,
            logs = emptyList(),
            isLoading = oldCollectionLoadJob?.isActive == true,
            isMerging = false,
            error = null,
            parseStatus = removeParseStatus(_state.value.parseStatus, "New source loaded:")
        )
    }

    fun merge() {
        val old = _state.value.oldCollection ?: run {
            _state.value = _state.value.copy(error = "Please load the old Postman collection first")
            return
        }
        val newSource = _state.value.newSwagger ?: _state.value.newCollection ?: run {
            _state.value = _state.value.copy(error = "Please load the new Swagger or Postman file first")
            return
        }
        mergeJob?.cancel()
        mergeJob = scope.launch {
            _state.value = _state.value.copy(isMerging = true, error = null, logs = emptyList())
            try {
                val result = withContext(Dispatchers.IO) { mergeUseCase.merge(old, newSource) }
                _state.value = _state.value.copy(
                    mergeResult = result, logs = result.logs, isMerging = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isMerging = false, error = "Merge failed: ${e.message}")
            }
        }
    }

    fun export(directory: File) {
        val result = _state.value.mergeResult ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val json = mergeUseCase.export(result.mergedCollection)
                    val outputFile = resolveSafeCollectionExportFile(
                        directory = directory,
                        collectionName = result.mergedCollection.info.name
                    )
                    atomicWriteUtf8(outputFile, json)
                }
                val log = MergeLog(LogLevel.SUCCESS, "Exported to: ${directory.absolutePath}")
                _state.value = _state.value.copy(logs = _state.value.logs + log)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Export failed: ${e.message}")
            }
        }
    }

    private fun countEndpoints(items: List<PostmanItem>): Int {
        return items.sumOf { if (it.request != null) 1 else countEndpoints(it.item).toInt() }
    }

    private fun removeParseStatus(status: String, prefix: String): String = status
        .lineSequence()
        .filterNot { it.trimStart().startsWith(prefix) }
        .joinToString("\n")
        .trim()

    fun onCleared() { scope.cancel() }
}

private val WINDOWS_RESERVED_FILE_NAMES = setOf(
    "CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
)

internal fun sanitizeCollectionExportName(collectionName: String): String {
    val normalized = Normalizer.normalize(collectionName, Normalizer.Form.NFKC)
    val cleaned = normalized
        .replace(Regex("""[^\p{L}\p{N}._-]+"""), "_")
        .trim('.', '_', '-')
        .take(80)
        .ifBlank { "collection" }
    val safeStem = if (cleaned.uppercase(Locale.ROOT) in WINDOWS_RESERVED_FILE_NAMES) {
        "_$cleaned"
    } else cleaned
    return "${safeStem}_merged.json"
}

internal fun resolveSafeCollectionExportFile(directory: File, collectionName: String): File {
    val canonicalDirectory = directory.canonicalFile
    require(canonicalDirectory.isDirectory) { "Export destination is not a directory" }

    val target = File(canonicalDirectory, sanitizeCollectionExportName(collectionName)).canonicalFile
    require(target.toPath().startsWith(canonicalDirectory.toPath())) {
        "Export path escapes the selected directory"
    }
    return target
}

internal fun atomicWriteUtf8(target: File, content: String) {
    val directory = target.parentFile?.canonicalFile
        ?: throw IllegalArgumentException("Export target has no parent directory")
    require(directory.isDirectory) { "Export destination is not a directory" }

    val temporary = Files.createTempFile(directory.toPath(), ".postsync-export-", ".tmp")
    try {
        Files.writeString(
            temporary,
            content,
            StandardCharsets.UTF_8,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
        FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
            channel.force(true)
        }
        try {
            Files.move(
                temporary,
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}
