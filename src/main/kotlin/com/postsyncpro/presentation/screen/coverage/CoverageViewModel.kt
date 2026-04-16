package com.postsyncpro.presentation.screen.coverage

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.usecase.AnalyzeCoverageUseCase
import com.postsyncpro.domain.usecase.ExtractRulesUseCase
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import com.postsyncpro.domain.usecase.ValidateSrsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class CoverageState(
    val srsText: String = "",
    val swaggerSpec: SwaggerSpec? = null,
    val swaggerFileName: String = "",
    val matrix: CoverageMatrix? = null,
    val isAnalyzing: Boolean = false,
    val progress: String = "",
    val error: String? = null,
    val selectedCell: CoverageCell? = null
)

class CoverageViewModel(
    private val extractRulesUseCase: ExtractRulesUseCase,
    private val validateSrsUseCase: ValidateSrsUseCase,
    private val mergeUseCase: MergeCollectionsUseCase,
    private val coverageUseCase: AnalyzeCoverageUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(CoverageState())
    val state: StateFlow<CoverageState> = _state.asStateFlow()

    fun updateSrsText(text: String) { _state.value = _state.value.copy(srsText = text) }

    fun loadSwaggerFile(file: File) {
        scope.launch {
            try {
                val spec = withContext(Dispatchers.IO) {
                    mergeUseCase.parseSwaggerSpec(file.readText())
                }
                _state.value = _state.value.copy(swaggerSpec = spec, swaggerFileName = file.name, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Parse error: ${e.message}")
            }
        }
    }

    fun analyzeCoverage() {
        val spec = _state.value.swaggerSpec ?: return
        val srsText = _state.value.srsText
        if (srsText.isBlank()) {
            _state.value = _state.value.copy(error = "Paste SRS requirements first")
            return
        }

        scope.launch {
            _state.value = _state.value.copy(isAnalyzing = true, error = null)
            try {
                val endpoints = spec.paths.flatMap { (path, methods) ->
                    methods.map { (method, _) -> "${method.uppercase()} $path" to (path to method) }
                }

                val extractionResults = mutableMapOf<String, SrsExtractionResult>()
                val validationReports = mutableMapOf<String, ValidationReport>()

                for ((i, ep) in endpoints.withIndex()) {
                    val (key, pathMethod) = ep
                    val (path, method) = pathMethod
                    _state.value = _state.value.copy(
                        progress = "Analyzing ${i + 1}/${endpoints.size}: $key"
                    )

                    try {
                        val extraction = withContext(Dispatchers.IO) {
                            extractRulesUseCase(srsText, key)
                        }
                        extractionResults[key] = extraction

                        val report = withContext(Dispatchers.IO) {
                            validateSrsUseCase(extraction.rules, spec, path, method)
                        }
                        validationReports[key] = report
                    } catch (e: Exception) {
                        // Skip failed endpoint
                    }
                }

                val matrix = coverageUseCase.analyze(srsText, extractionResults, validationReports)
                _state.value = _state.value.copy(
                    matrix = matrix, isAnalyzing = false, progress = ""
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isAnalyzing = false, error = "Analysis failed: ${e.message}")
            }
        }
    }

    fun selectCell(cell: CoverageCell?) {
        _state.value = _state.value.copy(selectedCell = cell)
    }

    fun onCleared() { scope.cancel() }
}
