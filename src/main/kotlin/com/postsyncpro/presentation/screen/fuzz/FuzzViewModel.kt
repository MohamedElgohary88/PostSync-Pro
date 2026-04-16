package com.postsyncpro.presentation.screen.fuzz

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import com.postsyncpro.domain.usecase.RunFuzzTestsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class EndpointFuzzResult(
    val endpoint: EndpointInfo,
    val results: List<FuzzTestResult>,
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val averageResponseTimeMs: Long
) {
    val passRate: Float get() = if (totalTests > 0) passedTests.toFloat() / totalTests else 0f
}

data class FuzzState(
    val swaggerSpec: SwaggerSpec? = null,
    val swaggerFileName: String = "",
    val endpoints: List<EndpointInfo> = emptyList(),
    // Single endpoint selection (for click-to-preview)
    val selectedEndpoint: EndpointInfo? = null,
    // Multi-endpoint selection
    val selectedEndpoints: Set<EndpointInfo> = emptySet(),
    val isBatchMode: Boolean = false,
    val selectedMutations: Set<MutationType> = setOf(MutationType.HAPPY_PATH, MutationType.NULL_VALUES, MutationType.EMPTY_STRINGS, MutationType.TYPE_MISMATCH),
    val baseUrl: String = "",
    val authHeader: String = "",
    val generatedTests: List<FuzzTestCase> = emptyList(),
    // Single endpoint results
    val results: List<FuzzTestResult> = emptyList(),
    // Batch results
    val batchResults: List<EndpointFuzzResult> = emptyList(),
    val batchProgress: Int = 0,
    val batchTotal: Int = 0,
    val currentTestingEndpoint: String = "",
    // Status
    val isLoading: Boolean = false,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val totalTests: Int = 0
) {
    val totalBatchTests: Int get() = batchResults.sumOf { it.totalTests }
    val totalBatchPassed: Int get() = batchResults.sumOf { it.passedTests }
    val totalBatchFailed: Int get() = batchResults.sumOf { it.failedTests }
    val overallPassRate: Float get() = if (totalBatchTests > 0) totalBatchPassed.toFloat() / totalBatchTests else 0f
    val overallAvgTime: Long get() = if (batchResults.isNotEmpty()) batchResults.map { it.averageResponseTimeMs }.average().toLong() else 0L
}

class FuzzViewModel(
    private val fuzzUseCase: RunFuzzTestsUseCase,
    private val mergeUseCase: MergeCollectionsUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(FuzzState())
    val state: StateFlow<FuzzState> = _state.asStateFlow()

    fun loadSwaggerFile(file: File) {
        scope.launch {
            try {
                val (spec, endpoints, baseUrl) = withContext(Dispatchers.IO) {
                    val json = file.readText()
                    val spec = mergeUseCase.parseSwaggerSpec(json)
                    val endpoints = mutableListOf<EndpointInfo>()
                    spec.paths.forEach { (path, methods) ->
                        methods.forEach { (method, op) ->
                            endpoints.add(EndpointInfo(
                                path = path, method = method.uppercase(),
                                parameters = op.parameters, requestBody = op.requestBody,
                                expectedSchema = op.requestBody?.content?.values?.firstOrNull()?.schema
                            ))
                        }
                    }
                    val baseUrl = spec.servers.firstOrNull()?.url ?: ""
                    Triple(spec, endpoints, baseUrl)
                }
                _state.value = _state.value.copy(
                    swaggerSpec = spec, swaggerFileName = file.name,
                    endpoints = endpoints, baseUrl = baseUrl, error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed to parse: ${e.message}")
            }
        }
    }

    // Single endpoint selection (clicking an endpoint row)
    fun selectEndpoint(endpoint: EndpointInfo) {
        if (_state.value.isBatchMode) {
            toggleEndpoint(endpoint)
        } else {
            _state.value = _state.value.copy(
                selectedEndpoint = endpoint,
                selectedEndpoints = setOf(endpoint),
                results = emptyList(), generatedTests = emptyList()
            )
        }
    }

    // Multi-endpoint toggle (checkbox)
    fun toggleEndpoint(endpoint: EndpointInfo) {
        val current = _state.value.selectedEndpoints.toMutableSet()
        if (current.contains(endpoint)) current.remove(endpoint) else current.add(endpoint)
        _state.value = _state.value.copy(
            selectedEndpoints = current,
            selectedEndpoint = if (current.size == 1) current.first() else _state.value.selectedEndpoint
        )
    }

    fun selectAllEndpoints() {
        _state.value = _state.value.copy(
            selectedEndpoints = _state.value.endpoints.toSet(),
            isBatchMode = true
        )
    }

    fun deselectAllEndpoints() {
        _state.value = _state.value.copy(
            selectedEndpoints = emptySet()
        )
    }

    fun toggleBatchMode() {
        val newBatch = !_state.value.isBatchMode
        _state.value = _state.value.copy(
            isBatchMode = newBatch,
            selectedEndpoints = if (!newBatch) {
                _state.value.selectedEndpoint?.let { setOf(it) } ?: emptySet()
            } else {
                _state.value.selectedEndpoint?.let { setOf(it) } ?: emptySet()
            }
        )
    }

    fun toggleMutation(type: MutationType) {
        val current = _state.value.selectedMutations.toMutableSet()
        if (current.contains(type)) current.remove(type) else current.add(type)
        _state.value = _state.value.copy(selectedMutations = current)
    }

    fun updateBaseUrl(url: String) { _state.value = _state.value.copy(baseUrl = url) }
    fun updateAuthHeader(header: String) { _state.value = _state.value.copy(authHeader = header) }

    fun generateAndRun() {
        val endpoints = if (_state.value.isBatchMode && _state.value.selectedEndpoints.size > 1) {
            _state.value.selectedEndpoints.toList()
        } else {
            val ep = _state.value.selectedEndpoint ?: _state.value.selectedEndpoints.firstOrNull() ?: return
            listOf(ep)
        }

        val baseUrl = _state.value.baseUrl
        if (baseUrl.isBlank()) {
            _state.value = _state.value.copy(error = "Base URL is required")
            return
        }

        val headers = mutableMapOf<String, String>()
        if (_state.value.authHeader.isNotBlank()) {
            headers["Authorization"] = _state.value.authHeader
        }

        if (endpoints.size > 1) {
            runBatch(endpoints, baseUrl, headers)
        } else {
            runSingle(endpoints.first(), baseUrl, headers)
        }
    }

    private fun runSingle(endpoint: EndpointInfo, baseUrl: String, headers: Map<String, String>) {
        val config = FuzzTestConfig(
            endpoint = endpoint,
            mutations = _state.value.selectedMutations,
            baseUrl = baseUrl,
            headers = headers
        )

        scope.launch {
            _state.value = _state.value.copy(
                isRunning = true, results = emptyList(), batchResults = emptyList(), error = null
            )
            try {
                val tests = withContext(Dispatchers.IO) { fuzzUseCase.generateTests(config) }
                _state.value = _state.value.copy(generatedTests = tests, totalTests = tests.size)

                fuzzUseCase.executeTests(tests, config)
                    .flowOn(Dispatchers.IO)
                    .collect { result ->
                        val currentResults = _state.value.results + result
                        _state.value = _state.value.copy(
                            results = currentResults,
                            progress = if (tests.isNotEmpty()) currentResults.size.toFloat() / tests.size else 0f
                        )
                    }
                _state.value = _state.value.copy(isRunning = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isRunning = false, error = "Test execution failed: ${e.message}")
            }
        }
    }

    private fun runBatch(endpoints: List<EndpointInfo>, baseUrl: String, headers: Map<String, String>) {
        scope.launch {
            _state.value = _state.value.copy(
                isRunning = true, results = emptyList(), batchResults = emptyList(),
                batchProgress = 0, batchTotal = endpoints.size, error = null
            )

            val batchResults = mutableListOf<EndpointFuzzResult>()

            for ((index, endpoint) in endpoints.withIndex()) {
                _state.value = _state.value.copy(
                    currentTestingEndpoint = "${endpoint.method} ${endpoint.path}",
                    batchProgress = index
                )

                val config = FuzzTestConfig(
                    endpoint = endpoint,
                    mutations = _state.value.selectedMutations,
                    baseUrl = baseUrl,
                    headers = headers
                )

                try {
                    val tests = withContext(Dispatchers.IO) { fuzzUseCase.generateTests(config) }
                    val endpointResults = mutableListOf<FuzzTestResult>()

                    fuzzUseCase.executeTests(tests, config)
                        .flowOn(Dispatchers.IO)
                        .collect { result ->
                            endpointResults.add(result)
                            // Update progress within this endpoint
                            val endpointFrac = if (tests.isNotEmpty()) endpointResults.size.toFloat() / tests.size else 0f
                            val overallProgress = if (endpoints.isNotEmpty()) (index.toFloat() + endpointFrac) / endpoints.size else 0f
                            _state.value = _state.value.copy(progress = overallProgress.coerceIn(0f, 1f))
                        }

                    val passed = endpointResults.count { it.passed }
                    val avgTime = if (endpointResults.isNotEmpty()) endpointResults.map { it.responseTimeMs }.average().toLong() else 0L

                    batchResults.add(EndpointFuzzResult(
                        endpoint = endpoint,
                        results = endpointResults.toList(),
                        totalTests = endpointResults.size,
                        passedTests = passed,
                        failedTests = endpointResults.size - passed,
                        averageResponseTimeMs = avgTime
                    ))

                    _state.value = _state.value.copy(
                        batchResults = batchResults.toList(),
                        batchProgress = index + 1
                    )
                } catch (e: Exception) {
                    // Record failed endpoint with error info, continue batch
                    batchResults.add(EndpointFuzzResult(
                        endpoint = endpoint,
                        results = emptyList(),
                        totalTests = 0,
                        passedTests = 0,
                        failedTests = 0,
                        averageResponseTimeMs = 0
                    ))
                    _state.value = _state.value.copy(
                        batchResults = batchResults.toList(),
                        batchProgress = index + 1
                    )
                }
            }

            _state.value = _state.value.copy(
                isRunning = false, currentTestingEndpoint = ""
            )
        }
    }

    fun cancelTests() {
        scope.launch { fuzzUseCase.cancel() }
        _state.value = _state.value.copy(isRunning = false)
    }

    fun onCleared() { scope.cancel() }
}
