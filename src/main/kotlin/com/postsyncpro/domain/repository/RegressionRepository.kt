package com.postsyncpro.domain.repository

import com.postsyncpro.domain.model.*

interface RegressionRepository {
    suspend fun saveTestRun(run: TestRunRecord): Long
    suspend fun saveTestResults(results: List<TestResultRecord>)
    suspend fun getLatestRunForEndpoint(endpoint: String, method: String): TestRunRecord?
    suspend fun getAllRunsForEndpoint(endpoint: String, method: String, limit: Int = 20): List<TestRunRecord>
    suspend fun getAllLatestRuns(): List<TestRunRecord>
    suspend fun getTestResultsByRunId(runId: Long): List<TestResultRecord>
    suspend fun getTotalRunCount(): Int
    suspend fun clearHistory()
}
