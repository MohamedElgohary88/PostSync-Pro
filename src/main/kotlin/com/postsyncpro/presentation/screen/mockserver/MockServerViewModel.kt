package com.postsyncpro.presentation.screen.mockserver

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.MockServerRepository
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class MockServerState(
    val port: Int = 8080,
    val isRunning: Boolean = false,
    val swaggerSpec: SwaggerSpec? = null,
    val swaggerFileName: String = "",
    val routes: List<MockRoute> = emptyList(),
    val logs: List<MockRequestLog> = emptyList(),
    val enableValidation: Boolean = true,
    val enableLogging: Boolean = true,
    val error: String? = null,
    val totalRequests: Int = 0,
    val successRequests: Int = 0,
    val failedRequests: Int = 0
)

class MockServerViewModel(
    private val mockServerRepository: MockServerRepository,
    private val mergeUseCase: MergeCollectionsUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(MockServerState())
    val state: StateFlow<MockServerState> = _state.asStateFlow()

    init {
        // Observe request logs
        scope.launch {
            mockServerRepository.requestLogs.collect { logs ->
                val success = logs.count { it.responseStatus in 200..299 }
                _state.value = _state.value.copy(
                    logs = logs,
                    totalRequests = logs.size,
                    successRequests = success,
                    failedRequests = logs.size - success
                )
            }
        }
    }

    fun loadSwaggerFile(file: File) {
        scope.launch {
            try {
                val (spec, routes) = withContext(Dispatchers.IO) {
                    val json = file.readText()
                    val spec = mergeUseCase.parseSwaggerSpec(json)
                    val routes = buildMockRoutes(spec)
                    spec to routes
                }
                _state.value = _state.value.copy(
                    swaggerSpec = spec,
                    swaggerFileName = file.name,
                    routes = routes,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed to parse: ${e.message}")
            }
        }
    }

    fun updatePort(port: String) {
        port.toIntOrNull()?.let {
            if (it in 1024..65535) {
                _state.value = _state.value.copy(port = it)
            }
        }
    }

    fun toggleValidation() {
        _state.value = _state.value.copy(enableValidation = !_state.value.enableValidation)
    }

    fun toggleLogging() {
        _state.value = _state.value.copy(enableLogging = !_state.value.enableLogging)
    }

    fun toggleServer() {
        if (_state.value.isRunning) {
            stopServer()
        } else {
            startServer()
        }
    }

    private fun startServer() {
        if (_state.value.routes.isEmpty()) {
            _state.value = _state.value.copy(error = "Load a Swagger file first")
            return
        }

        scope.launch {
            try {
                val config = MockServerConfig(
                    port = _state.value.port,
                    enableValidation = _state.value.enableValidation,
                    enableLogging = _state.value.enableLogging
                )
                withContext(Dispatchers.IO) {
                    mockServerRepository.startServer(config, _state.value.routes)
                }
                _state.value = _state.value.copy(isRunning = true, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed to start: ${e.message}")
            }
        }
    }

    private fun stopServer() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { mockServerRepository.stopServer() }
                _state.value = _state.value.copy(isRunning = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed to stop: ${e.message}")
            }
        }
    }

    fun clearLogs() {
        mockServerRepository.clearLogs()
    }

    private fun buildMockRoutes(spec: SwaggerSpec): List<MockRoute> {
        val routes = mutableListOf<MockRoute>()
        for ((path, methods) in spec.paths) {
            for ((method, op) in methods) {
                val requestSchema = op.requestBody?.content?.get("application/json")?.schema
                val responseSchema = (op.responses["200"] ?: op.responses["201"])
                    ?.content?.get("application/json")?.schema
                val requiredFields = requestSchema?.required ?: emptyList()

                val statusCode = when {
                    method.equals("post", true) -> 201
                    method.equals("delete", true) -> 204
                    else -> 200
                }

                routes.add(MockRoute(
                    path = path,
                    method = method.uppercase(),
                    requestSchema = requestSchema,
                    responseSchema = responseSchema,
                    requiredFields = requiredFields,
                    statusCode = statusCode
                ))
            }
        }
        return routes
    }

    fun onCleared() {
        scope.launch {
            if (_state.value.isRunning) {
                mockServerRepository.stopServer()
            }
        }
        scope.cancel()
    }
}
