package com.postsyncpro.workbench

import com.postsyncpro.domain.model.WorkbenchAssertion
import com.postsyncpro.domain.model.WorkbenchAssertionOutcome
import com.postsyncpro.domain.model.WorkbenchRequestField
import com.postsyncpro.domain.model.WorkbenchResponse
import com.postsyncpro.domain.usecase.EvaluateWorkbenchAssertionsUseCase
import com.postsyncpro.domain.usecase.WorkbenchAssertionLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkbenchAssertionsTest {
    private val evaluator = EvaluateWorkbenchAssertionsUseCase()

    @Test
    fun `evaluates supported assertions in deterministic input order`() = runTest {
        val assertions = listOf(
            WorkbenchAssertion.StatusEquals(id = "status", expected = 201),
            WorkbenchAssertion.StatusRange(id = "range", minimum = 200, maximum = 299),
            WorkbenchAssertion.ResponseTimeMax(id = "time", maximumMillis = 50),
            WorkbenchAssertion.HeaderExists(id = "exists", name = "x-trace"),
            WorkbenchAssertion.HeaderEquals(id = "equals", name = "X-Trace", expected = "ABC-ready"),
            WorkbenchAssertion.HeaderContains(
                id = "header-contains",
                name = "X-Trace",
                expected = "READY",
                ignoreCase = true
            ),
            WorkbenchAssertion.BodyContains(id = "body", expected = "Ada"),
            WorkbenchAssertion.JsonPathExists(id = "path", path = "$.user.roles[1]"),
            WorkbenchAssertion.JsonPathEquals(
                id = "json-equals",
                path = "$['user'].active",
                expectedJson = "true"
            ),
            WorkbenchAssertion.StatusEquals(id = "disabled", enabled = false, expected = 500)
        )
        val report = evaluator(
            response(
                status = 201,
                durationMillis = 75,
                headers = listOf(WorkbenchRequestField("X-Trace", "abc-ready")),
                body = """{"user":{"name":"Ada","active":true,"roles":["admin","editor"]}}"""
            ),
            assertions
        )

        assertEquals(assertions.map { it.id }, report.results.map { it.assertionId })
        assertEquals(
            listOf(
                WorkbenchAssertionOutcome.PASSED,
                WorkbenchAssertionOutcome.PASSED,
                WorkbenchAssertionOutcome.FAILED,
                WorkbenchAssertionOutcome.PASSED,
                WorkbenchAssertionOutcome.FAILED,
                WorkbenchAssertionOutcome.PASSED,
                WorkbenchAssertionOutcome.PASSED,
                WorkbenchAssertionOutcome.PASSED,
                WorkbenchAssertionOutcome.PASSED,
                WorkbenchAssertionOutcome.SKIPPED
            ),
            report.results.map { it.outcome }
        )
        assertEquals(7, report.passedCount)
        assertEquals(2, report.failedCount)
        assertEquals(0, report.errorCount)
        assertEquals(1, report.skippedCount)
        assertTrue(report.hasEvaluatedAssertions)
        assertFalse(report.allPassed)
    }

    @Test
    fun `supports typed root quoted-property array and null JSON path checks`() = runTest {
        val report = evaluator(
            response(
                body = """{"odd.key":{"items":[null,{"display name":"ready"}]},"number":1}"""
            ),
            listOf(
                WorkbenchAssertion.JsonPathExists(path = "$['odd.key'].items[0]"),
                WorkbenchAssertion.JsonPathEquals(
                    path = "$['odd.key'].items[1]['display name']",
                    expectedJson = "\"ready\""
                ),
                WorkbenchAssertion.JsonPathEquals(path = "$.number", expectedJson = "\"1\""),
                WorkbenchAssertion.JsonPathEquals(path = "$", expectedJson = "{}"),
                WorkbenchAssertion.JsonPathExists(path = "$.missing")
            )
        )

        assertEquals(
            listOf(
                WorkbenchAssertionOutcome.PASSED,
                WorkbenchAssertionOutcome.PASSED,
                WorkbenchAssertionOutcome.FAILED,
                WorkbenchAssertionOutcome.FAILED,
                WorkbenchAssertionOutcome.FAILED
            ),
            report.results.map { it.outcome }
        )
    }

    @Test
    fun `malformed response expected JSON and paths are evaluation errors`() = runTest {
        val badPath = evaluator(
            response(body = """{"ok":true}"""),
            listOf(WorkbenchAssertion.JsonPathExists(path = "$.items[*]"))
        )
        val badExpected = evaluator(
            response(body = """{"ok":true}"""),
            listOf(WorkbenchAssertion.JsonPathEquals(path = "$.ok", expectedJson = "not-json"))
        )
        val badResponse = evaluator(
            response(body = "{"),
            listOf(WorkbenchAssertion.JsonPathExists(path = "$.ok"))
        )

        assertEquals(WorkbenchAssertionOutcome.ERROR, badPath.results.single().outcome)
        assertEquals(WorkbenchAssertionOutcome.ERROR, badExpected.results.single().outcome)
        assertEquals(WorkbenchAssertionOutcome.ERROR, badResponse.results.single().outcome)
    }

    @Test
    fun `diagnostics never echo actual expected or explicit secret values`() = runTest {
        val actualSecret = "raw-response-secret-734"
        val expectedSecret = "configured-secret-882"
        val report = evaluator(
            response(
                status = 401,
                headers = listOf(WorkbenchRequestField("Authorization", "Bearer $actualSecret")),
                body = """{"token":"$actualSecret"}"""
            ),
            listOf(
                WorkbenchAssertion.HeaderEquals(
                    id = actualSecret,
                    name = "Authorization",
                    expected = "Bearer $expectedSecret"
                ),
                WorkbenchAssertion.BodyContains(expected = expectedSecret),
                WorkbenchAssertion.JsonPathEquals(path = "$.token", expectedJson = "\"$expectedSecret\"")
            ),
            sensitiveValues = setOf(actualSecret, expectedSecret)
        )

        val diagnostics = report.results.joinToString { "${it.assertionId}:${it.message}" }
        assertFalse(actualSecret in diagnostics)
        assertFalse(expectedSecret in diagnostics)
        assertTrue(report.results.all { it.outcome == WorkbenchAssertionOutcome.FAILED })
    }

    @Test
    fun `rejects an oversized assertion run without partial execution`() = runTest {
        val bounded = EvaluateWorkbenchAssertionsUseCase(
            WorkbenchAssertionLimits(maxAssertions = 2)
        )
        val report = bounded(
            response(),
            listOf(
                WorkbenchAssertion.StatusEquals(expected = 200),
                WorkbenchAssertion.StatusEquals(expected = 200),
                WorkbenchAssertion.StatusEquals(expected = 200)
            )
        )

        assertEquals(1, report.results.size)
        assertEquals(-1, report.results.single().assertionIndex)
        assertEquals(WorkbenchAssertionOutcome.ERROR, report.results.single().outcome)
        assertFalse(report.allPassed)
    }

    @Test
    fun `body header depth token path and expected-value budgets fail closed`() = runTest {
        val bodyLimited = EvaluateWorkbenchAssertionsUseCase(
            WorkbenchAssertionLimits(maxResponseBodyCharacters = 8)
        )
        val headerLimited = EvaluateWorkbenchAssertionsUseCase(
            WorkbenchAssertionLimits(maxHeaders = 1)
        )
        val jsonLimited = EvaluateWorkbenchAssertionsUseCase(
            WorkbenchAssertionLimits(maxJsonDepth = 2, maxJsonStructuralTokens = 4)
        )
        val pathLimited = EvaluateWorkbenchAssertionsUseCase(
            WorkbenchAssertionLimits(maxJsonPathSegments = 1)
        )
        val expectedLimited = EvaluateWorkbenchAssertionsUseCase(
            WorkbenchAssertionLimits(maxExpectedCharacters = 4, maxContainsNeedleCharacters = 4)
        )

        val outcomes = listOf(
            bodyLimited(
                response(body = "0123456789"),
                listOf(WorkbenchAssertion.BodyContains(expected = "2"))
            ),
            headerLimited(
                response(
                    headers = listOf(
                        WorkbenchRequestField("A", "1"),
                        WorkbenchRequestField("B", "2")
                    )
                ),
                listOf(WorkbenchAssertion.HeaderExists(name = "A"))
            ),
            jsonLimited(
                response(body = """{"a":{"b":{"c":1}}}"""),
                listOf(WorkbenchAssertion.JsonPathExists(path = "$.a"))
            ),
            jsonLimited(
                response(body = "[1,2,3,4,5]"),
                listOf(WorkbenchAssertion.JsonPathExists(path = "$"))
            ),
            pathLimited(
                response(body = """{"a":{"b":1}}"""),
                listOf(WorkbenchAssertion.JsonPathExists(path = "$.a.b"))
            ),
            expectedLimited(
                response(body = """{"a":1}"""),
                listOf(WorkbenchAssertion.JsonPathEquals(path = "$.a", expectedJson = "12345"))
            )
        ).map { it.results.single().outcome }

        assertEquals(List(outcomes.size) { WorkbenchAssertionOutcome.ERROR }, outcomes)
    }

    @Test
    fun `truncated bodies are never treated as complete assertion input`() = runTest {
        val report = evaluator(
            response(body = "needle", truncated = true),
            listOf(
                WorkbenchAssertion.StatusEquals(expected = 200),
                WorkbenchAssertion.BodyContains(expected = "needle"),
                WorkbenchAssertion.JsonPathExists(path = "$"),
                WorkbenchAssertion.HeaderExists(name = "X-Missing")
            )
        )

        assertEquals(
            listOf(
                WorkbenchAssertionOutcome.PASSED,
                WorkbenchAssertionOutcome.ERROR,
                WorkbenchAssertionOutcome.ERROR,
                WorkbenchAssertionOutcome.FAILED
            ),
            report.results.map { it.outcome }
        )
    }

    @Test
    fun `assertion models round trip through serialization`() {
        val json = Json { classDiscriminator = "type" }
        val original: List<WorkbenchAssertion> = listOf(
            WorkbenchAssertion.StatusRange(id = "status", minimum = 200, maximum = 299),
            WorkbenchAssertion.JsonPathEquals(id = "json", path = "$.state", expectedJson = "\"ready\"")
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<List<WorkbenchAssertion>>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `pre-cancelled evaluation preserves structured cancellation`() = runTest {
        val cancelled = Job().apply { cancel() }

        assertFailsWith<CancellationException> {
            withContext(cancelled) {
                evaluator(
                    response(body = "a".repeat(100_000)),
                    listOf(WorkbenchAssertion.BodyContains(expected = "missing"))
                )
            }
        }
    }

    @Test
    fun `invalid assertion configurations return errors instead of throwing`() = runTest {
        val report = evaluator(
            response(durationMillis = -1),
            listOf(
                WorkbenchAssertion.StatusEquals(expected = 99),
                WorkbenchAssertion.StatusRange(minimum = 500, maximum = 400),
                WorkbenchAssertion.ResponseTimeMax(maximumMillis = -1),
                WorkbenchAssertion.HeaderExists(name = ""),
                WorkbenchAssertion.HeaderExists(name = "Bad\r\nName"),
                WorkbenchAssertion.HeaderContains(name = "X-Test", expected = ""),
                WorkbenchAssertion.BodyContains(expected = "")
            )
        )

        assertTrue(report.results.all { it.outcome == WorkbenchAssertionOutcome.ERROR })
        assertTrue(report.hasEvaluatedAssertions)
        assertFalse(report.allPassed)
        assertEquals(report.results.indices.toList(), report.results.map { it.assertionIndex })
    }

    private fun response(
        status: Int = 200,
        durationMillis: Long = 25,
        headers: List<WorkbenchRequestField> = emptyList(),
        body: String = "{}",
        truncated: Boolean = false
    ) = WorkbenchResponse(
        statusCode = status,
        statusText = "",
        headers = headers,
        body = body,
        contentType = "application/json",
        durationMillis = durationMillis,
        sizeBytes = body.length.toLong(),
        receivedBytes = body.length.toLong(),
        truncated = truncated,
        startedAtEpochMillis = 1,
        completedAtEpochMillis = durationMillis + 1
    )
}
