package com.postsyncpro.presentation.screen.regression

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.usecase.RegressionAnalysisUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RegressionState(
    val dashboard: RegressionDashboard? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val expandedEndpoint: String? = null
)

class RegressionViewModel(
    private val regressionUseCase: RegressionAnalysisUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(RegressionState())
    val state: StateFlow<RegressionState> = _state.asStateFlow()

    fun loadDashboard() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val dashboard = withContext(Dispatchers.IO) { regressionUseCase.getDashboard() }
                _state.value = _state.value.copy(dashboard = dashboard, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Failed: ${e.message}")
            }
        }
    }

    fun toggleEndpoint(key: String) {
        _state.value = _state.value.copy(
            expandedEndpoint = if (_state.value.expandedEndpoint == key) null else key
        )
    }

    fun onCleared() { scope.cancel() }
}
