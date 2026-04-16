package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.RegressionRepository

class RegressionAnalysisUseCase(private val repository: RegressionRepository) {

    suspend fun saveAndAnalyze(
        endpoint: String,
        method: String,
        results: List<FuzzTestResult>
    ): RegressionAnalysis {
        val passed = results.count { it.passed }
        val failed = results.size - passed
        val avgTime = if (results.isNotEmpty()) results.map { it.responseTimeMs }.average().toLong() else 0L

        val currentRun = TestRunRecord(
            endpoint = endpoint,
            method = method,
            totalTests = results.size,
            passedTests = passed,
            failedTests = failed,
            passRate = if (results.isNotEmpty()) passed.toFloat() / results.size else 0f,
            avgResponseTimeMs = avgTime
        )

        // Save to DB
        val runId = repository.saveTestRun(currentRun)
        val testRecords = results.map { result ->
            TestResultRecord(
                runId = runId,
                mutationType = result.testCase.mutationType.name,
                testDescription = result.testCase.description,
                httpStatus = result.httpStatus,
                passed = result.passed,
                responseTimeMs = result.responseTimeMs,
                errorMessage = result.errorMessage
            )
        }
        repository.saveTestResults(testRecords)

        // Get previous run for comparison
        val allRuns = repository.getAllRunsForEndpoint(endpoint, method, limit = 2)
        val previousRun = allRuns.getOrNull(1)

        return if (previousRun != null) {
            val passRateDelta = currentRun.passRate - previousRun.passRate
            val timeDelta = currentRun.avgResponseTimeMs - previousRun.avgResponseTimeMs

            val status = when {
                passRateDelta > 0.05f -> RegressionStatus.IMPROVED
                passRateDelta < -0.05f -> RegressionStatus.REGRESSED
                else -> RegressionStatus.STABLE
            }

            // Find tests that failed before but pass now (fixed tests)
            val previousResults = repository.getTestResultsByRunId(previousRun.id)
            val previousFailedDescriptions = previousResults.filter { !it.passed }.map { it.testDescription }.toSet()
            val currentPassedDescriptions = testRecords.filter { it.passed }.map { it.testDescription }.toSet()
            val fixedDescriptions = previousFailedDescriptions.intersect(currentPassedDescriptions)
            val fixedTests = testRecords.filter { it.testDescription in fixedDescriptions }

            RegressionAnalysis(
                currentRun = currentRun.copy(id = runId),
                previousRun = previousRun,
                status = status,
                passRateDelta = passRateDelta,
                responseTimeDelta = timeDelta,
                newFailures = testRecords.filter { !it.passed },
                fixedTests = fixedTests
            )
        } else {
            RegressionAnalysis(
                currentRun = currentRun.copy(id = runId),
                previousRun = null,
                status = RegressionStatus.NO_BASELINE,
                passRateDelta = 0f,
                responseTimeDelta = 0L,
                newFailures = testRecords.filter { !it.passed },
                fixedTests = emptyList()
            )
        }
    }

    suspend fun getDashboard(): RegressionDashboard {
        val latestRuns = repository.getAllLatestRuns()
        val analyses = latestRuns.map { run ->
            val allRuns = repository.getAllRunsForEndpoint(run.endpoint, run.method, limit = 2)
            val previousRun = allRuns.getOrNull(1)

            if (previousRun != null) {
                val delta = run.passRate - previousRun.passRate
                RegressionAnalysis(
                    currentRun = run,
                    previousRun = previousRun,
                    status = when {
                        delta > 0.05f -> RegressionStatus.IMPROVED
                        delta < -0.05f -> RegressionStatus.REGRESSED
                        else -> RegressionStatus.STABLE
                    },
                    passRateDelta = delta,
                    responseTimeDelta = run.avgResponseTimeMs - previousRun.avgResponseTimeMs,
                    newFailures = emptyList(),
                    fixedTests = emptyList()
                )
            } else {
                RegressionAnalysis(
                    currentRun = run, previousRun = null,
                    status = RegressionStatus.NO_BASELINE,
                    passRateDelta = 0f, responseTimeDelta = 0L,
                    newFailures = emptyList(), fixedTests = emptyList()
                )
            }
        }

        val overallTrend = when {
            analyses.any { it.status == RegressionStatus.REGRESSED } -> RegressionStatus.REGRESSED
            analyses.all { it.status == RegressionStatus.IMPROVED } -> RegressionStatus.IMPROVED
            else -> RegressionStatus.STABLE
        }

        return RegressionDashboard(
            endpointAnalyses = analyses,
            overallTrend = overallTrend,
            totalRunsTracked = repository.getTotalRunCount(),
            lastRunDate = latestRuns.maxByOrNull { it.timestamp }?.timestamp
        )
    }
}
