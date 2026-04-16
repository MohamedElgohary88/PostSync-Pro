package com.postsyncpro.presentation.screen.validator

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.usecase.ExtractRulesUseCase
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import com.postsyncpro.domain.usecase.ValidateSrsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class EndpointResult(
    val endpoint: EndpointInfo,
    val extractedRules: SrsExtractionResult,
    val validationReport: ValidationReport
)

data class ValidatorState(
    val srsText: String = "",
    val swaggerSpec: SwaggerSpec? = null,
    val swaggerFileName: String = "",
    val availableEndpoints: List<EndpointInfo> = emptyList(),
    // Single endpoint selection (legacy)
    val selectedEndpoint: EndpointInfo? = null,
    // Multi-endpoint selection
    val selectedEndpoints: Set<EndpointInfo> = emptySet(),
    val isBatchMode: Boolean = false,
    // Single analysis results
    val extractedRules: SrsExtractionResult? = null,
    val validationReport: ValidationReport? = null,
    // Batch results
    val batchResults: List<EndpointResult> = emptyList(),
    val batchProgress: Int = 0,
    val batchTotal: Int = 0,
    val currentAnalyzingEndpoint: String = "",
    // Status
    val isAnalyzing: Boolean = false,
    val isExtracting: Boolean = false,
    val error: String? = null,
    val aiProviderName: String = ""
) {
    val overallBatchScore: Float
        get() {
            if (batchResults.isEmpty()) return 0f
            return batchResults.map { it.validationReport.overallScore }.average().toFloat()
        }

    val totalMatches: Int get() = batchResults.sumOf { it.validationReport.matches.size }
    val totalIssues: Int get() = batchResults.sumOf { it.validationReport.missingInBackend.size + it.validationReport.missingInSrs.size }
    val totalConflicts: Int get() = batchResults.sumOf { it.validationReport.typeConflicts.size }
}

class ValidatorViewModel(
    private val extractRules: ExtractRulesUseCase,
    private val validateSrs: ValidateSrsUseCase,
    private val mergeUseCase: MergeCollectionsUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(ValidatorState())
    val state: StateFlow<ValidatorState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(aiProviderName = extractRules.getProviderName())
    }

    fun updateSrsText(text: String) {
        _state.value = _state.value.copy(srsText = text)
    }

    fun loadSwaggerFile(file: File) {
        scope.launch {
            try {
                val (spec, endpoints) = withContext(Dispatchers.IO) {
                    val json = file.readText()
                    val spec = mergeUseCase.parseSwaggerSpec(json)
                    val endpoints = mutableListOf<EndpointInfo>()
                    spec.paths.forEach { (path, methods) ->
                        methods.forEach { (method, operation) ->
                            endpoints.add(EndpointInfo(
                                path = path,
                                method = method.uppercase(),
                                parameters = operation.parameters,
                                requestBody = operation.requestBody
                            ))
                        }
                    }
                    spec to endpoints
                }
                _state.value = _state.value.copy(
                    swaggerSpec = spec,
                    swaggerFileName = file.name,
                    availableEndpoints = endpoints,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed to parse Swagger: ${e.message}")
            }
        }
    }

    // Single endpoint selection
    fun selectEndpoint(endpoint: EndpointInfo) {
        _state.value = _state.value.copy(
            selectedEndpoint = endpoint,
            isBatchMode = false
        )
    }

    // Multi-endpoint toggle
    fun toggleEndpoint(endpoint: EndpointInfo) {
        val current = _state.value.selectedEndpoints.toMutableSet()
        if (current.contains(endpoint)) current.remove(endpoint) else current.add(endpoint)
        _state.value = _state.value.copy(
            selectedEndpoints = current,
            isBatchMode = current.size > 1 || (current.size == 1 && _state.value.isBatchMode),
            selectedEndpoint = if (current.size == 1) current.first() else _state.value.selectedEndpoint
        )
    }

    fun selectAllEndpoints() {
        _state.value = _state.value.copy(
            selectedEndpoints = _state.value.availableEndpoints.toSet(),
            isBatchMode = true
        )
    }

    fun deselectAllEndpoints() {
        _state.value = _state.value.copy(
            selectedEndpoints = emptySet(),
            isBatchMode = false
        )
    }

    fun toggleBatchMode() {
        val newBatch = !_state.value.isBatchMode
        _state.value = _state.value.copy(
            isBatchMode = newBatch,
            selectedEndpoints = if (!newBatch) {
                // Switching to single mode: keep only the first selected
                _state.value.selectedEndpoint?.let { setOf(it) } ?: emptySet()
            } else {
                // Switching to batch mode: add current selected
                _state.value.selectedEndpoint?.let { setOf(it) } ?: emptySet()
            }
        )
    }

    // Single endpoint analysis
    fun analyze() {
        if (_state.value.isBatchMode && _state.value.selectedEndpoints.size > 1) {
            analyzeBatch()
            return
        }

        val text = _state.value.srsText
        val endpoint = _state.value.selectedEndpoint
            ?: _state.value.selectedEndpoints.firstOrNull()
        val spec = _state.value.swaggerSpec
        if (text.isBlank() || endpoint == null || spec == null) return

        scope.launch {
            _state.value = _state.value.copy(
                isExtracting = true, isAnalyzing = true, error = null,
                batchResults = emptyList()
            )
            try {
                val rules = withContext(Dispatchers.IO) {
                    extractRules(text, "${endpoint.method} ${endpoint.path}")
                }
                _state.value = _state.value.copy(extractedRules = rules, isExtracting = false)

                val report = withContext(Dispatchers.IO) {
                    validateSrs(rules.rules, spec, endpoint.path, endpoint.method)
                }
                _state.value = _state.value.copy(validationReport = report, isAnalyzing = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isExtracting = false, isAnalyzing = false,
                    error = "Analysis failed: ${e.message}"
                )
            }
        }
    }

    // Batch analysis for multiple endpoints
    private fun analyzeBatch() {
        val text = _state.value.srsText
        val endpoints = _state.value.selectedEndpoints.toList()
        val spec = _state.value.swaggerSpec
        if (text.isBlank() || endpoints.isEmpty() || spec == null) return

        scope.launch {
            _state.value = _state.value.copy(
                isAnalyzing = true, isExtracting = true, error = null,
                batchResults = emptyList(),
                batchProgress = 0,
                batchTotal = endpoints.size,
                validationReport = null,
                extractedRules = null
            )

            val results = mutableListOf<EndpointResult>()

            for ((index, endpoint) in endpoints.withIndex()) {
                try {
                    _state.value = _state.value.copy(
                        currentAnalyzingEndpoint = "${endpoint.method} ${endpoint.path}",
                        batchProgress = index,
                        isExtracting = true
                    )

                    val rules = withContext(Dispatchers.IO) {
                        extractRules(text, "${endpoint.method} ${endpoint.path}")
                    }

                    _state.value = _state.value.copy(isExtracting = false)

                    val report = withContext(Dispatchers.IO) {
                        validateSrs(rules.rules, spec, endpoint.path, endpoint.method)
                    }

                    results.add(EndpointResult(
                        endpoint = endpoint,
                        extractedRules = rules,
                        validationReport = report
                    ))

                    _state.value = _state.value.copy(
                        batchResults = results.toList(),
                        batchProgress = index + 1
                    )
                } catch (e: Exception) {
                    // Record failed endpoint with empty results, continue batch
                    _state.value = _state.value.copy(
                        batchProgress = index + 1,
                        error = "Warning: ${endpoint.method} ${endpoint.path} failed: ${e.message}"
                    )
                }
            }

            _state.value = _state.value.copy(
                isAnalyzing = false,
                isExtracting = false,
                currentAnalyzingEndpoint = ""
            )
        }
    }

    fun onCleared() { scope.cancel() }
}
