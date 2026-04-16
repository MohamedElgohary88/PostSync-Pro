package com.postsyncpro.domain.model

import java.time.LocalDateTime

/**
 * Module 5: QA Report PDF Generation models
 */
data class QaReport(
    val projectName: String,
    val generatedAt: LocalDateTime = LocalDateTime.now(),
    val healthScore: Float,
    val srsCompliance: SrsComplianceMetrics,
    val fuzzSummary: FuzzSummaryMetrics,
    val endpointDetails: List<EndpointReportDetail>,
    val regressionData: RegressionSummary? = null
)

data class SrsComplianceMetrics(
    val totalSrsFields: Int,
    val matchedFields: Int,
    val missingInApi: Int,
    val missingInSrs: Int,
    val conflicts: Int,
    val compliancePercent: Float
)

data class FuzzSummaryMetrics(
    val totalTests: Int,
    val passed: Int,
    val failed: Int,
    val passRate: Float,
    val averageResponseTimeMs: Long
)

data class EndpointReportDetail(
    val method: String,
    val path: String,
    val srsScore: Float,
    val fuzzPassRate: Float,
    val failedTests: List<FailedTestDetail> = emptyList()
)

data class FailedTestDetail(
    val testName: String,
    val mutationType: String,
    val expectedStatus: String,
    val actualStatus: Int,
    val errorMessage: String
)

data class RegressionSummary(
    val previousRunDate: LocalDateTime?,
    val previousPassRate: Float,
    val currentPassRate: Float,
    val regressedEndpoints: List<String>,
    val improvedEndpoints: List<String>
)
