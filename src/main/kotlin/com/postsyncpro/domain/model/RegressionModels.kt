package com.postsyncpro.domain.model

import java.time.LocalDateTime

/**
 * Module 10: Smart Regression Reporter models
 */
data class TestRunRecord(
    val id: Long = 0,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val endpoint: String,
    val method: String,
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val passRate: Float,
    val avgResponseTimeMs: Long,
    val details: List<TestResultRecord> = emptyList()
)

data class TestResultRecord(
    val id: Long = 0,
    val runId: Long = 0,
    val mutationType: String,
    val testDescription: String,
    val httpStatus: Int,
    val passed: Boolean,
    val responseTimeMs: Long,
    val errorMessage: String = ""
)

data class RegressionAnalysis(
    val currentRun: TestRunRecord,
    val previousRun: TestRunRecord?,
    val status: RegressionStatus,
    val passRateDelta: Float,    // positive = improvement, negative = regression
    val responseTimeDelta: Long, // positive = slower, negative = faster
    val newFailures: List<TestResultRecord>,
    val fixedTests: List<TestResultRecord>
)

enum class RegressionStatus {
    IMPROVED,
    STABLE,
    REGRESSED,
    NO_BASELINE
}

data class RegressionDashboard(
    val endpointAnalyses: List<RegressionAnalysis>,
    val overallTrend: RegressionStatus,
    val totalRunsTracked: Int,
    val lastRunDate: LocalDateTime?
)
