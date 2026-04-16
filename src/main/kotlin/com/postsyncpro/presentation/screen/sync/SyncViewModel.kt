package com.postsyncpro.presentation.screen.sync

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

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

    fun selectOldCollection(file: File) {
        scope.launch {
            _state.value = _state.value.copy(oldCollectionFile = file, isLoading = true, error = null)
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
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to parse old collection: ${e.message}")
            }
        }
    }

    fun selectNewSource(file: File) {
        scope.launch {
            _state.value = _state.value.copy(newSourceFile = file, isLoading = true, error = null)
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
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed to parse new source: ${e.message}")
            }
        }
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
        scope.launch {
            _state.value = _state.value.copy(isMerging = true, error = null, logs = emptyList())
            try {
                val result = withContext(Dispatchers.IO) { mergeUseCase.merge(old, newSource) }
                _state.value = _state.value.copy(
                    mergeResult = result, logs = result.logs, isMerging = false
                )
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
                    val fileName = "${result.mergedCollection.info.name.replace(" ", "_")}_merged.json"
                    val outputFile = File(directory, fileName)
                    outputFile.writeText(json)
                }
                val log = MergeLog(LogLevel.SUCCESS, "Exported to: ${directory.absolutePath}")
                _state.value = _state.value.copy(logs = _state.value.logs + log)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Export failed: ${e.message}")
            }
        }
    }

    private fun countEndpoints(items: List<PostmanItem>): Int {
        return items.sumOf { if (it.request != null) 1 else countEndpoints(it.item).toInt() }
    }

    fun onCleared() { scope.cancel() }
}
