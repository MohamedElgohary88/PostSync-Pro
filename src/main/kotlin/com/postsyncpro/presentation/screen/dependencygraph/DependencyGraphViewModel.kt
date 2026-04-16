package com.postsyncpro.presentation.screen.dependencygraph

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.usecase.AnalyzeDependenciesUseCase
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class DependencyGraphState(
    val graph: DependencyGraph? = null,
    val swaggerFileName: String = "",
    val isAnalyzing: Boolean = false,
    val selectedNodeId: String? = null,
    val selectedEdge: DependencyEdge? = null,
    val canvasOffsetX: Float = 0f,
    val canvasOffsetY: Float = 0f,
    val zoom: Float = 1f,
    val filterCategory: String? = null,
    val error: String? = null
)

class DependencyGraphViewModel(
    private val dependencyUseCase: AnalyzeDependenciesUseCase,
    private val mergeUseCase: MergeCollectionsUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(DependencyGraphState())
    val state: StateFlow<DependencyGraphState> = _state.asStateFlow()

    fun loadSwaggerFile(file: File) {
        scope.launch {
            _state.value = _state.value.copy(isAnalyzing = true, error = null)
            try {
                val graph = withContext(Dispatchers.IO) {
                    val spec = mergeUseCase.parseSwaggerSpec(file.readText())
                    dependencyUseCase.analyze(spec)
                }
                _state.value = _state.value.copy(
                    graph = graph, swaggerFileName = file.name, isAnalyzing = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isAnalyzing = false, error = "Error: ${e.message}")
            }
        }
    }

    fun selectNode(nodeId: String?) { _state.value = _state.value.copy(selectedNodeId = nodeId, selectedEdge = null) }
    fun selectEdge(edge: DependencyEdge?) { _state.value = _state.value.copy(selectedEdge = edge, selectedNodeId = null) }
    fun setFilter(category: String?) { _state.value = _state.value.copy(filterCategory = category) }
    fun updateZoom(delta: Float) {
        _state.value = _state.value.copy(zoom = (_state.value.zoom + delta).coerceIn(0.3f, 3f))
    }
    fun updateOffset(dx: Float, dy: Float) {
        _state.value = _state.value.copy(
            canvasOffsetX = _state.value.canvasOffsetX + dx,
            canvasOffsetY = _state.value.canvasOffsetY + dy
        )
    }

    fun onCleared() { scope.cancel() }
}
