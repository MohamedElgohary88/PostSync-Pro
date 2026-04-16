package com.postsyncpro.presentation.screen.apimonitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HealthStatus { HEALTHY, DEGRADED, DOWN }
enum class CheckInterval(val ms: Long, val label: String) {
    EVERY_30_SEC(30_000, "30s"),
    EVERY_1_MIN(60_000, "1min"),
    EVERY_5_MIN(300_000, "5min"),
    EVERY_15_MIN(900_000, "15min"),
    EVERY_30_MIN(1_800_000, "30min")
}

data class MonitoredEndpoint(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String = "",
    val expectedStatus: Int = 200,
    val checkInterval: CheckInterval = CheckInterval.EVERY_5_MIN,
    val alertOnDown: Boolean = true,
    val status: HealthStatus = HealthStatus.HEALTHY,
    val uptime: Double = 100.0,
    val avgResponseTime: Long = 0,
    val lastCheck: String? = null,
    val responseHistory: List<Long> = emptyList(), // response times
    val incidents: List<Incident> = emptyList()
)

data class Incident(
    val timestamp: String,
    val duration: String,
    val statusCode: Int,
    val message: String
)

data class ApiMonitorState(
    val endpoints: List<MonitoredEndpoint> = emptyList(),
    val isAdding: Boolean = false,
    val editingEndpoint: MonitoredEndpoint? = null,
    val isMonitoring: Boolean = false,
    val selectedEndpointId: String? = null
)

class ApiMonitorViewModel {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(ApiMonitorState())
    val state: StateFlow<ApiMonitorState> = _state.asStateFlow()
    private var monitoringJob: kotlinx.coroutines.Job? = null

    fun startAdding() { _state.value = _state.value.copy(isAdding = true, editingEndpoint = MonitoredEndpoint()) }
    fun cancelAdding() { _state.value = _state.value.copy(isAdding = false, editingEndpoint = null) }
    fun updateEditing(endpoint: MonitoredEndpoint) { _state.value = _state.value.copy(editingEndpoint = endpoint) }

    fun saveEndpoint() {
        val ep = _state.value.editingEndpoint ?: return
        val existing = _state.value.endpoints.indexOfFirst { it.id == ep.id }
        val endpoints = if (existing >= 0) {
            _state.value.endpoints.toMutableList().apply { set(existing, ep) }
        } else {
            _state.value.endpoints + ep
        }
        _state.value = _state.value.copy(endpoints = endpoints, isAdding = false, editingEndpoint = null)
    }

    fun deleteEndpoint(id: String) {
        _state.value = _state.value.copy(endpoints = _state.value.endpoints.filter { it.id != id })
    }

    fun selectEndpoint(id: String?) { _state.value = _state.value.copy(selectedEndpointId = id) }

    fun startMonitoring() {
        _state.value = _state.value.copy(isMonitoring = true)
        monitoringJob = scope.launch {
            while (true) {
                checkAllEndpoints()
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    fun stopMonitoring() {
        monitoringJob?.cancel()
        _state.value = _state.value.copy(isMonitoring = false)
    }

    private fun checkAllEndpoints() {
        _state.value = _state.value.copy(
            endpoints = _state.value.endpoints.map { ep ->
                val responseTime = (50 + (Math.random() * 450).toLong())
                val isDown = Math.random() < 0.05
                val isDegraded = !isDown && responseTime > 400
                val status = when {
                    isDown -> HealthStatus.DOWN
                    isDegraded -> HealthStatus.DEGRADED
                    else -> HealthStatus.HEALTHY
                }
                val history = (ep.responseHistory + responseTime).takeLast(30)
                val incidents = if (isDown) {
                    listOf(Incident(
                        timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
                        duration = "ongoing",
                        statusCode = 503,
                        message = "Service Unavailable"
                    )) + ep.incidents
                } else ep.incidents

                ep.copy(
                    status = status,
                    avgResponseTime = if (history.isNotEmpty()) history.average().toLong() else 0,
                    lastCheck = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
                    responseHistory = history,
                    uptime = ((history.count { it < 500 }.toDouble() / history.size.coerceAtLeast(1)) * 100),
                    incidents = incidents.take(10)
                )
            }
        )
    }

    fun onCleared() {
        monitoringJob?.cancel()
        scope.cancel()
    }
}
