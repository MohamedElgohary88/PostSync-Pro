package com.postsyncpro.data.repository

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.FuzzTestRepository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

class FuzzTestRepositoryImpl(
    private val httpClient: HttpClient
) : FuzzTestRepository, AutoCloseable {

    private companion object {
        const val MAX_CONCURRENCY = 32
        const val MAX_STORED_RESPONSE_BYTES = 2_000
        const val TRUNCATED_SUFFIX = "\n[truncated]"
    }

    private val activeRuns = ConcurrentHashMap.newKeySet<Job>()
    private val closed = AtomicBoolean(false)

    override suspend fun generateTestCases(config: FuzzTestConfig): List<FuzzTestCase> {
        check(!closed.get()) { "Fuzz test repository is closed" }
        val testCases = mutableListOf<FuzzTestCase>()

        val schema = config.endpoint.requestBody?.content?.values?.firstOrNull()?.schema
        val fieldNames = schema?.properties?.keys?.toList() ?: emptyList()
        val requiredFields = schema?.required ?: emptyList()

        for (mutation in config.mutations) {
            when (mutation) {
                MutationType.HAPPY_PATH -> {
                    testCases.add(
                        FuzzTestCase(
                            id = UUID.randomUUID().toString(),
                            mutationType = MutationType.HAPPY_PATH,
                            endpointPath = config.endpoint.path,
                            method = config.endpoint.method,
                            payload = generateHappyPathPayload(schema),
                            description = "Valid request with all required fields"
                        )
                    )
                }

                MutationType.NULL_VALUES -> {
                    for (field in requiredFields) {
                        testCases.add(
                            FuzzTestCase(
                                id = UUID.randomUUID().toString(),
                                mutationType = MutationType.NULL_VALUES,
                                endpointPath = config.endpoint.path,
                                method = config.endpoint.method,
                                payload = generateNullPayload(schema, field),
                                description = "Set required field '$field' to null"
                            )
                        )
                    }
                }

                MutationType.EMPTY_STRINGS -> {
                    for (field in fieldNames) {
                        val propSchema = schema?.properties?.get(field)
                        if (propSchema?.type == "string") {
                            testCases.add(
                                FuzzTestCase(
                                    id = UUID.randomUUID().toString(),
                                    mutationType = MutationType.EMPTY_STRINGS,
                                    endpointPath = config.endpoint.path,
                                    method = config.endpoint.method,
                                    payload = generateEmptyStringPayload(schema, field),
                                    description = "Set string field '$field' to empty string"
                                )
                            )
                        }
                    }
                }

                MutationType.TYPE_MISMATCH -> {
                    for (field in fieldNames) {
                        val propSchema = schema?.properties?.get(field)
                        testCases.add(
                            FuzzTestCase(
                                id = UUID.randomUUID().toString(),
                                mutationType = MutationType.TYPE_MISMATCH,
                                endpointPath = config.endpoint.path,
                                method = config.endpoint.method,
                                payload = generateTypeMismatchPayload(schema, field, propSchema),
                                description = "Send wrong type for field '$field' (expected: ${propSchema?.type})"
                            )
                        )
                    }
                }

                MutationType.BOUNDARY_VALUES -> {
                    for (field in fieldNames) {
                        val propSchema = schema?.properties?.get(field)
                        if (propSchema?.type in listOf("integer", "number")) {
                            testCases.add(
                                FuzzTestCase(
                                    id = UUID.randomUUID().toString(),
                                    mutationType = MutationType.BOUNDARY_VALUES,
                                    endpointPath = config.endpoint.path,
                                    method = config.endpoint.method,
                                    payload = generateBoundaryPayload(schema, field, isMax = true),
                                    description = "Send MAX value for numeric field '$field'"
                                )
                            )
                            testCases.add(
                                FuzzTestCase(
                                    id = UUID.randomUUID().toString(),
                                    mutationType = MutationType.BOUNDARY_VALUES,
                                    endpointPath = config.endpoint.path,
                                    method = config.endpoint.method,
                                    payload = generateBoundaryPayload(schema, field, isMax = false),
                                    description = "Send MIN value for numeric field '$field'"
                                )
                            )
                        }
                        if (propSchema?.type == "string") {
                            testCases.add(
                                FuzzTestCase(
                                    id = UUID.randomUUID().toString(),
                                    mutationType = MutationType.BOUNDARY_VALUES,
                                    endpointPath = config.endpoint.path,
                                    method = config.endpoint.method,
                                    payload = generateLongStringPayload(schema, field),
                                    description = "Send very long string for field '$field'"
                                )
                            )
                        }
                    }
                }

                MutationType.SQL_INJECTION -> {
                    val sqlPayloads = listOf(
                        "' OR 1=1 --",
                        "'; DROP TABLE users;--",
                        "' UNION SELECT * FROM users --",
                        "1; SELECT * FROM information_schema.tables --",
                        "' OR ''='",
                        "admin'--"
                    )
                    for (payload in sqlPayloads) {
                        for (field in fieldNames) {
                            val propSchema = schema?.properties?.get(field)
                            if (propSchema?.type == "string") {
                                testCases.add(
                                    FuzzTestCase(
                                        id = UUID.randomUUID().toString(),
                                        mutationType = MutationType.SQL_INJECTION,
                                        endpointPath = config.endpoint.path,
                                        method = config.endpoint.method,
                                        payload = generateInjectionPayload(schema, field, payload),
                                        description = "SQL injection on '$field': $payload"
                                    )
                                )
                                break // One string field per payload is enough
                            }
                        }
                    }
                }

                MutationType.XSS_PAYLOADS -> {
                    val xssPayloads = listOf(
                        "<script>alert('xss')</script>",
                        "<img onerror=alert(1) src=x>",
                        "<svg onload=alert('xss')>",
                        "javascript:alert(1)",
                        "\"><script>alert(1)</script>",
                        "<iframe src=\"javascript:alert(1)\">"
                    )
                    for (payload in xssPayloads) {
                        for (field in fieldNames) {
                            val propSchema = schema?.properties?.get(field)
                            if (propSchema?.type == "string") {
                                testCases.add(
                                    FuzzTestCase(
                                        id = UUID.randomUUID().toString(),
                                        mutationType = MutationType.XSS_PAYLOADS,
                                        endpointPath = config.endpoint.path,
                                        method = config.endpoint.method,
                                        payload = generateInjectionPayload(schema, field, payload),
                                        description = "XSS payload on '$field'"
                                    )
                                )
                                break
                            }
                        }
                    }
                }

                MutationType.OVERFLOW -> {
                    val longString = "A".repeat(10000)
                    for (field in fieldNames) {
                        testCases.add(
                            FuzzTestCase(
                                id = UUID.randomUUID().toString(),
                                mutationType = MutationType.OVERFLOW,
                                endpointPath = config.endpoint.path,
                                method = config.endpoint.method,
                                payload = generateInjectionPayload(schema, field, longString),
                                description = "Overflow: 10000-char string for field '$field'"
                            )
                        )
                    }
                    // Also test with huge number
                    for (field in fieldNames) {
                        val propSchema = schema?.properties?.get(field)
                        if (propSchema?.type in listOf("integer", "number")) {
                            testCases.add(
                                FuzzTestCase(
                                    id = UUID.randomUUID().toString(),
                                    mutationType = MutationType.OVERFLOW,
                                    endpointPath = config.endpoint.path,
                                    method = config.endpoint.method,
                                    payload = generateOverflowNumberPayload(schema, field),
                                    description = "Overflow: huge number for field '$field'"
                                )
                            )
                        }
                    }
                }
            }
        }

        return testCases
    }

    override fun executeTests(testCases: List<FuzzTestCase>, config: FuzzTestConfig): Flow<FuzzTestResult> = channelFlow {
        check(!closed.get()) { "Fuzz test repository is closed" }
        val runJob = currentCoroutineContext()[Job]
            ?: throw IllegalStateException("Fuzz execution requires a coroutine Job")
        activeRuns.add(runJob)

        try {
            if (testCases.isEmpty()) return@channelFlow
            val workerCount = config.concurrency
                .coerceIn(1, MAX_CONCURRENCY)
                .coerceAtMost(testCases.size)
            val nextIndex = AtomicInteger(0)

            val workers = List(workerCount) {
                launch {
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val index = nextIndex.getAndIncrement()
                        if (index >= testCases.size) break
                        send(executeTestCase(testCases[index], config))
                    }
                }
            }
            workers.joinAll()
        } finally {
            activeRuns.remove(runJob)
        }
    }

    override suspend fun cancelTests() {
        activeRuns.toList().forEach { job ->
            job.cancel(CancellationException("Fuzz test run cancelled"))
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        activeRuns.toList().forEach { job ->
            job.cancel(CancellationException("Fuzz test repository closed"))
        }
        httpClient.close()
    }

    private suspend fun executeTestCase(testCase: FuzzTestCase, config: FuzzTestConfig): FuzzTestResult {
        val url = buildRequestUrl(config.baseUrl, testCase.endpointPath)
        val startTime = System.currentTimeMillis()

        return try {
            val response = httpClient.request(url) {
                method = HttpMethod.parse(testCase.method)
                for ((key, value) in config.headers) {
                    header(key, value)
                }
                if (testCase.payload.isNotBlank() && testCase.method.uppercase() in listOf("POST", "PUT", "PATCH")) {
                    contentType(ContentType.Application.Json)
                    setBody(testCase.payload)
                }
            }

            val responseTime = System.currentTimeMillis() - startTime
            val responseBody = readResponseBodyLimited(response)
            val passed = evaluateResult(testCase.mutationType, response.status.value)

            FuzzTestResult(
                testCase = testCase,
                httpStatus = response.status.value,
                responseBody = responseBody,
                responseTimeMs = responseTime,
                passed = passed
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            FuzzTestResult(
                testCase = testCase,
                httpStatus = 0,
                responseBody = "",
                responseTimeMs = responseTime,
                passed = false,
                errorMessage = e.message ?: "Unknown error: ${e::class.simpleName}"
            )
        }
    }

    private suspend fun readResponseBodyLimited(response: HttpResponse): String {
        val channel = response.bodyAsChannel()
        val output = ByteArrayOutputStream(MAX_STORED_RESPONSE_BYTES)
        val buffer = ByteArray(1_024)
        var total = 0
        var truncated = false

        while (true) {
            val read = channel.readAvailable(buffer)
            if (read == -1) break
            if (read == 0) {
                kotlinx.coroutines.yield()
                continue
            }

            val remaining = MAX_STORED_RESPONSE_BYTES - total
            if (read > remaining) {
                if (remaining > 0) output.write(buffer, 0, remaining)
                truncated = true
                channel.cancel(CancellationException("Response body limit reached"))
                break
            }

            output.write(buffer, 0, read)
            total += read
        }

        val body = output.toString(StandardCharsets.UTF_8)
        return if (truncated) {
            body.take((MAX_STORED_RESPONSE_BYTES - TRUNCATED_SUFFIX.length).coerceAtLeast(0)) + TRUNCATED_SUFFIX
        } else body
    }

    private fun buildRequestUrl(baseUrl: String, endpointPath: String): String {
        val base = baseUrl.trimEnd('/')
        val path = endpointPath.trimStart('/')
        return "$base/$path"
    }

    private fun evaluateResult(mutationType: MutationType, statusCode: Int): Boolean {
        return when (mutationType) {
            MutationType.HAPPY_PATH -> statusCode in 200..299
            MutationType.NULL_VALUES -> statusCode in listOf(400, 422)
            MutationType.EMPTY_STRINGS -> statusCode in listOf(400, 422)
            MutationType.TYPE_MISMATCH -> statusCode in listOf(400, 422)
            MutationType.BOUNDARY_VALUES -> statusCode in 200..499 // Depends on field constraints
            MutationType.SQL_INJECTION -> statusCode != 500 // Server should not crash
            MutationType.XSS_PAYLOADS -> statusCode != 500
            MutationType.OVERFLOW -> statusCode != 500
        }
    }

    // --- Payload generation helpers ---

    private fun generateHappyPathPayload(schema: SwaggerSchema?): String {
        if (schema == null) return "{}"
        return buildJsonPayload(schema) { field, propSchema ->
            generateSampleValue(propSchema)
        }
    }

    private fun generateNullPayload(schema: SwaggerSchema?, nullField: String): String {
        if (schema == null) return "{}"
        return buildJsonPayload(schema) { field, propSchema ->
            if (field == nullField) "null" else generateSampleValue(propSchema)
        }
    }

    private fun generateEmptyStringPayload(schema: SwaggerSchema?, emptyField: String): String {
        if (schema == null) return "{}"
        return buildJsonPayload(schema) { field, propSchema ->
            if (field == emptyField) "\"\"" else generateSampleValue(propSchema)
        }
    }

    private fun generateTypeMismatchPayload(schema: SwaggerSchema?, targetField: String, targetSchema: SwaggerSchema?): String {
        if (schema == null) return "{}"
        return buildJsonPayload(schema) { field, propSchema ->
            if (field == targetField) {
                when (propSchema.type) {
                    "string" -> "12345"
                    "integer", "number" -> "\"not_a_number\""
                    "boolean" -> "\"not_a_boolean\""
                    "array" -> "\"not_an_array\""
                    "object" -> "\"not_an_object\""
                    else -> "null"
                }
            } else {
                generateSampleValue(propSchema)
            }
        }
    }

    private fun generateBoundaryPayload(schema: SwaggerSchema?, targetField: String, isMax: Boolean): String {
        if (schema == null) return "{}"
        return buildJsonPayload(schema) { field, propSchema ->
            if (field == targetField) {
                if (isMax) "${Long.MAX_VALUE}" else "${Long.MIN_VALUE}"
            } else {
                generateSampleValue(propSchema)
            }
        }
    }

    private fun generateLongStringPayload(schema: SwaggerSchema?, targetField: String): String {
        if (schema == null) return "{}"
        return buildJsonPayload(schema) { field, propSchema ->
            if (field == targetField) "\"${"A".repeat(10000)}\"" else generateSampleValue(propSchema)
        }
    }

    private fun generateInjectionPayload(schema: SwaggerSchema?, targetField: String, injectionValue: String): String {
        if (schema == null) return "{\"payload\": \"${escapeJson(injectionValue)}\"}"
        return buildJsonPayload(schema) { field, propSchema ->
            if (field == targetField) "\"${escapeJson(injectionValue)}\"" else generateSampleValue(propSchema)
        }
    }

    private fun generateOverflowNumberPayload(schema: SwaggerSchema?, targetField: String): String {
        if (schema == null) return "{}"
        return buildJsonPayload(schema) { field, propSchema ->
            if (field == targetField) "99999999999999999999" else generateSampleValue(propSchema)
        }
    }

    private fun buildJsonPayload(
        schema: SwaggerSchema,
        valueProvider: (String, SwaggerSchema) -> String
    ): String {
        val entries = schema.properties.map { (fieldName, propSchema) ->
            "\"$fieldName\": ${valueProvider(fieldName, propSchema)}"
        }
        return "{${entries.joinToString(", ")}}"
    }

    private fun generateSampleValue(schema: SwaggerSchema): String {
        return when (schema.type) {
            "string" -> when (schema.format) {
                "email" -> "\"test@example.com\""
                "date-time" -> "\"2024-01-01T00:00:00Z\""
                "date" -> "\"2024-01-01\""
                "uuid" -> "\"${UUID.randomUUID()}\""
                else -> "\"test_value\""
            }
            "integer" -> "1"
            "number" -> "1.0"
            "boolean" -> "true"
            "array" -> "[]"
            "object" -> "{}"
            else -> "null"
        }
    }

    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
