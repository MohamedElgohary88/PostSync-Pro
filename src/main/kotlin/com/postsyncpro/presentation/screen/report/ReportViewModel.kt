package com.postsyncpro.presentation.screen.report

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.usecase.GenerateReportUseCase
import com.postsyncpro.presentation.screen.validator.ValidatorViewModel
import com.postsyncpro.presentation.screen.fuzz.FuzzViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDateTime

data class ReportState(
    val projectName: String = "PostSync Pro QA Report",
    val healthScore: Float = 0f,
    val srsMetrics: SrsComplianceMetrics? = null,
    val fuzzMetrics: FuzzSummaryMetrics? = null,
    val endpointDetails: List<EndpointReportDetail> = emptyList(),
    val isGenerating: Boolean = false,
    val isGenerated: Boolean = false,
    val outputPath: String = "",
    val error: String? = null
)

class ReportViewModel(
    private val generateReportUseCase: GenerateReportUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(ReportState())
    val state: StateFlow<ReportState> = _state.asStateFlow()

    fun updateProjectName(name: String) {
        _state.value = _state.value.copy(projectName = name)
    }

    fun loadDataFromValidation(reports: List<ValidationReport>) {
        if (reports.isEmpty()) return
        val totalMatched = reports.sumOf { it.matches.size }
        val totalMissing = reports.sumOf { it.missingInBackend.size }
        val totalApiOnly = reports.sumOf { it.missingInSrs.size }
        val totalConflicts = reports.sumOf { it.typeConflicts.size }
        val totalFields = totalMatched + totalMissing + totalApiOnly + totalConflicts

        val compliancePercent = if (totalFields > 0) totalMatched.toFloat() / totalFields else 0f

        val metrics = SrsComplianceMetrics(
            totalSrsFields = totalMatched + totalMissing + totalConflicts,
            matchedFields = totalMatched,
            missingInApi = totalMissing,
            missingInSrs = totalApiOnly,
            conflicts = totalConflicts,
            compliancePercent = compliancePercent
        )

        val details = reports.map { report ->
            EndpointReportDetail(
                method = report.method,
                path = report.endpointPath,
                srsScore = report.overallScore,
                fuzzPassRate = 0f
            )
        }

        _state.value = _state.value.copy(
            srsMetrics = metrics,
            endpointDetails = details,
            healthScore = compliancePercent
        )
    }

    fun loadDataFromFuzz(results: List<FuzzTestResult>, endpointResults: List<com.postsyncpro.presentation.screen.fuzz.EndpointFuzzResult> = emptyList()) {
        val total = results.size
        val passed = results.count { it.passed }
        val avgTime = if (results.isNotEmpty()) results.map { it.responseTimeMs }.average().toLong() else 0L

        val fuzzMetrics = FuzzSummaryMetrics(
            totalTests = total,
            passed = passed,
            failed = total - passed,
            passRate = if (total > 0) passed.toFloat() / total else 0f,
            averageResponseTimeMs = avgTime
        )

        val failedDetails = results.filter { !it.passed }.map { result ->
            FailedTestDetail(
                testName = result.testCase.description,
                mutationType = result.testCase.mutationType.displayName,
                expectedStatus = "2xx",
                actualStatus = result.httpStatus,
                errorMessage = result.errorMessage.ifEmpty { "Unexpected status code" }
            )
        }

        // Update endpoint details with fuzz data
        val existingDetails = _state.value.endpointDetails.toMutableList()
        for (er in endpointResults) {
            val existing = existingDetails.find { it.method == er.endpoint.method && it.path == er.endpoint.path }
            if (existing != null) {
                val idx = existingDetails.indexOf(existing)
                existingDetails[idx] = existing.copy(
                    fuzzPassRate = er.passRate,
                    failedTests = er.results.filter { !it.passed }.map { r ->
                        FailedTestDetail(r.testCase.description, r.testCase.mutationType.displayName,
                            "2xx", r.httpStatus, r.errorMessage.ifEmpty { "Unexpected status" })
                    }
                )
            } else {
                existingDetails.add(EndpointReportDetail(
                    method = er.endpoint.method, path = er.endpoint.path,
                    srsScore = 0f, fuzzPassRate = er.passRate,
                    failedTests = er.results.filter { !it.passed }.map { r ->
                        FailedTestDetail(r.testCase.description, r.testCase.mutationType.displayName,
                            "2xx", r.httpStatus, r.errorMessage.ifEmpty { "Unexpected status" })
                    }
                ))
            }
        }

        // Recalculate health score
        val srsScore = _state.value.srsMetrics?.compliancePercent ?: 0f
        val fuzzScore = fuzzMetrics.passRate
        val healthScore = if (srsScore > 0f && fuzzScore > 0f) (srsScore + fuzzScore) / 2f
            else if (srsScore > 0f) srsScore else fuzzScore

        _state.value = _state.value.copy(
            fuzzMetrics = fuzzMetrics,
            endpointDetails = existingDetails,
            healthScore = healthScore
        )
    }

    fun generateReport(outputFile: File) {
        scope.launch {
            _state.value = _state.value.copy(isGenerating = true, error = null)
            try {
                val report = QaReport(
                    projectName = _state.value.projectName,
                    healthScore = _state.value.healthScore,
                    srsCompliance = _state.value.srsMetrics ?: SrsComplianceMetrics(0, 0, 0, 0, 0, 0f),
                    fuzzSummary = _state.value.fuzzMetrics ?: FuzzSummaryMetrics(0, 0, 0, 0f, 0),
                    endpointDetails = _state.value.endpointDetails
                )
                withContext(Dispatchers.IO) {
                    generateReportUseCase(report, outputFile)
                }
                _state.value = _state.value.copy(
                    isGenerating = false,
                    isGenerated = true,
                    outputPath = outputFile.absolutePath
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isGenerating = false, error = "Failed: ${e.message}")
            }
        }
    }

    fun onCleared() { scope.cancel() }
}
