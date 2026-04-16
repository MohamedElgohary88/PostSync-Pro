package com.postsyncpro.presentation.screen.multienv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Environment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val baseUrl: String = "",
    val authToken: String = "",
    val customHeaders: Map<String, String> = emptyMap(),
    val isDefault: Boolean = false,
    val isActive: Boolean = true,
    val lastTestResult: EnvTestResult? = null
)

data class EnvTestResult(
    val passRate: Double,
    val totalTests: Int,
    val passed: Int,
    val failed: Int,
    val avgResponseTime: Long,
    val timestamp: String
)

data class MultiEnvState(
    val environments: List<Environment> = emptyList(),
    val isAdding: Boolean = false,
    val editingEnv: Environment? = null,
    val isRunningAll: Boolean = false,
    val runningEnvId: String? = null,
    val showComparison: Boolean = false
)

class MultiEnvViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(MultiEnvState())
    val state: StateFlow<MultiEnvState> = _state.asStateFlow()

    fun startAdding() { _state.value = _state.value.copy(isAdding = true, editingEnv = Environment()) }
    fun cancelAdding() { _state.value = _state.value.copy(isAdding = false, editingEnv = null) }
    fun updateEditing(env: Environment) { _state.value = _state.value.copy(editingEnv = env) }

    fun saveEnvironment() {
        val env = _state.value.editingEnv ?: return
        val existing = _state.value.environments.indexOfFirst { it.id == env.id }
        val envs = if (existing >= 0) {
            _state.value.environments.toMutableList().apply { set(existing, env) }
        } else {
            _state.value.environments + env
        }
        _state.value = _state.value.copy(environments = envs, isAdding = false, editingEnv = null)
    }

    fun deleteEnvironment(id: String) {
        _state.value = _state.value.copy(environments = _state.value.environments.filter { it.id != id })
    }

    fun setDefault(id: String) {
        _state.value = _state.value.copy(
            environments = _state.value.environments.map { it.copy(isDefault = it.id == id) }
        )
    }

    fun toggleActive(id: String) {
        _state.value = _state.value.copy(
            environments = _state.value.environments.map {
                if (it.id == id) it.copy(isActive = !it.isActive) else it
            }
        )
    }

    fun runAllEnvironments() {
        scope.launch {
            _state.value = _state.value.copy(isRunningAll = true)
            for (env in _state.value.environments.filter { it.isActive }) {
                _state.value = _state.value.copy(runningEnvId = env.id)
                kotlinx.coroutines.delay(1500 + (Math.random() * 2000).toLong())
                val passRate = 70.0 + Math.random() * 30.0
                val total = 10 + (Math.random() * 30).toInt()
                val passed = (total * passRate / 100).toInt()
                val result = EnvTestResult(
                    passRate = passRate, totalTests = total, passed = passed,
                    failed = total - passed, avgResponseTime = (100 + (Math.random() * 400).toLong()),
                    timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                )
                _state.value = _state.value.copy(
                    environments = _state.value.environments.map {
                        if (it.id == env.id) it.copy(lastTestResult = result) else it
                    }
                )
            }
            _state.value = _state.value.copy(isRunningAll = false, runningEnvId = null, showComparison = true)
        }
    }

    fun toggleComparison() { _state.value = _state.value.copy(showComparison = !_state.value.showComparison) }

    fun onCleared() { scope.cancel() }
}
