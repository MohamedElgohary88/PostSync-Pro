package com.postsyncpro.presentation.screen.breakingchange

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import com.postsyncpro.domain.usecase.SwaggerDiffUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class BreakingChangeState(
    val oldSpec: SwaggerSpec? = null,
    val newSpec: SwaggerSpec? = null,
    val oldFileName: String = "",
    val newFileName: String = "",
    val diffResult: SwaggerDiffResult? = null,
    val isAnalyzing: Boolean = false,
    val error: String? = null,
    val filter: ChangeFilter = ChangeFilter.ALL,
    val expandedEndpoints: Set<String> = emptySet()
)

enum class ChangeFilter { ALL, BREAKING_ONLY, NON_BREAKING }

class BreakingChangeViewModel(
    private val diffUseCase: SwaggerDiffUseCase,
    private val mergeUseCase: MergeCollectionsUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(BreakingChangeState())
    val state: StateFlow<BreakingChangeState> = _state.asStateFlow()

    fun loadOldSpec(file: File) {
        scope.launch {
            try {
                val spec = withContext(Dispatchers.IO) { mergeUseCase.parseSwaggerSpec(file.readText()) }
                _state.value = _state.value.copy(oldSpec = spec, oldFileName = file.name, error = null, diffResult = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Old spec parse error: ${e.message}")
            }
        }
    }

    fun loadNewSpec(file: File) {
        scope.launch {
            try {
                val spec = withContext(Dispatchers.IO) { mergeUseCase.parseSwaggerSpec(file.readText()) }
                _state.value = _state.value.copy(newSpec = spec, newFileName = file.name, error = null, diffResult = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "New spec parse error: ${e.message}")
            }
        }
    }

    fun compareSpecs() {
        val old = _state.value.oldSpec
        val new = _state.value.newSpec
        if (old == null || new == null) {
            _state.value = _state.value.copy(error = "Load both specs first")
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isAnalyzing = true, error = null)
            try {
                val result = withContext(Dispatchers.IO) { diffUseCase.compare(old, new) }
                _state.value = _state.value.copy(diffResult = result, isAnalyzing = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isAnalyzing = false, error = "Compare failed: ${e.message}")
            }
        }
    }

    fun setFilter(filter: ChangeFilter) { _state.value = _state.value.copy(filter = filter) }

    fun toggleEndpoint(key: String) {
        val expanded = _state.value.expandedEndpoints.toMutableSet()
        if (expanded.contains(key)) expanded.remove(key) else expanded.add(key)
        _state.value = _state.value.copy(expandedEndpoints = expanded)
    }

    fun onCleared() { scope.cancel() }
}
