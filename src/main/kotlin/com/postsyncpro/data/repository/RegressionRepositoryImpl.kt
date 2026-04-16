package com.postsyncpro.data.repository

import com.postsyncpro.data.database.TestResults
import com.postsyncpro.data.database.TestRuns
import com.postsyncpro.domain.model.TestRunRecord
import com.postsyncpro.domain.model.TestResultRecord
import com.postsyncpro.domain.repository.RegressionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RegressionRepositoryImpl : RegressionRepository {

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override suspend fun saveTestRun(run: TestRunRecord): Long = withContext(Dispatchers.IO) {
        transaction {
            TestRuns.insert {
                it[timestamp] = run.timestamp.format(formatter)
                it[endpoint] = run.endpoint
                it[method] = run.method
                it[totalTests] = run.totalTests
                it[passedTests] = run.passedTests
                it[failedTests] = run.failedTests
                it[passRate] = run.passRate
                it[avgResponseTimeMs] = run.avgResponseTimeMs
            } get TestRuns.id
        }
    }

    override suspend fun saveTestResults(results: List<TestResultRecord>) = withContext(Dispatchers.IO) {
        transaction {
            for (result in results) {
                TestResults.insert {
                    it[runId] = result.runId
                    it[mutationType] = result.mutationType
                    it[testDescription] = result.testDescription
                    it[httpStatus] = result.httpStatus
                    it[passed] = result.passed
                    it[responseTimeMs] = result.responseTimeMs
                    it[errorMessage] = result.errorMessage
                }
            }
        }
    }

    override suspend fun getLatestRunForEndpoint(
        endpoint: String,
        method: String
    ): TestRunRecord? = withContext(Dispatchers.IO) {
        transaction {
            TestRuns.selectAll()
                .where { (TestRuns.endpoint eq endpoint) and (TestRuns.method eq method) }
                .orderBy(TestRuns.timestamp, SortOrder.DESC)
                .limit(1)
                .map { it.toTestRunRecord() }
                .firstOrNull()
        }
    }

    override suspend fun getAllRunsForEndpoint(
        endpoint: String,
        method: String,
        limit: Int
    ): List<TestRunRecord> = withContext(Dispatchers.IO) {
        transaction {
            TestRuns.selectAll()
                .where { (TestRuns.endpoint eq endpoint) and (TestRuns.method eq method) }
                .orderBy(TestRuns.timestamp, SortOrder.DESC)
                .limit(limit)
                .map { it.toTestRunRecord() }
        }
    }

    override suspend fun getAllLatestRuns(): List<TestRunRecord> = withContext(Dispatchers.IO) {
        transaction {
            // Get distinct endpoint+method combinations
            val groups = TestRuns
                .select(TestRuns.endpoint, TestRuns.method)
                .withDistinct()
                .map { Pair(it[TestRuns.endpoint], it[TestRuns.method]) }

            // For each group, get the latest run
            groups.mapNotNull { (ep, meth) ->
                TestRuns.selectAll()
                    .where {
                        (TestRuns.endpoint eq ep) and (TestRuns.method eq meth)
                    }
                    .orderBy(TestRuns.timestamp, SortOrder.DESC)
                    .limit(1)
                    .map { it.toTestRunRecord() }
                    .firstOrNull()
            }
        }
    }

    override suspend fun getTestResultsByRunId(runId: Long): List<TestResultRecord> = withContext(Dispatchers.IO) {
        transaction {
            TestResults.selectAll()
                .where { TestResults.runId eq runId }
                .map { row ->
                    TestResultRecord(
                        runId = row[TestResults.runId],
                        mutationType = row[TestResults.mutationType],
                        testDescription = row[TestResults.testDescription],
                        httpStatus = row[TestResults.httpStatus],
                        passed = row[TestResults.passed],
                        responseTimeMs = row[TestResults.responseTimeMs],
                        errorMessage = row[TestResults.errorMessage]
                    )
                }
        }
    }

    override suspend fun getTotalRunCount(): Int = withContext(Dispatchers.IO) {
        transaction {
            TestRuns.selectAll().count().toInt()
        }
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        transaction {
            TestResults.deleteAll()
            TestRuns.deleteAll()
        }
        Unit
    }

    private fun ResultRow.toTestRunRecord(): TestRunRecord {
        return TestRunRecord(
            id = this[TestRuns.id],
            timestamp = LocalDateTime.parse(this[TestRuns.timestamp], formatter),
            endpoint = this[TestRuns.endpoint],
            method = this[TestRuns.method],
            totalTests = this[TestRuns.totalTests],
            passedTests = this[TestRuns.passedTests],
            failedTests = this[TestRuns.failedTests],
            passRate = this[TestRuns.passRate],
            avgResponseTimeMs = this[TestRuns.avgResponseTimeMs]
        )
    }
}
