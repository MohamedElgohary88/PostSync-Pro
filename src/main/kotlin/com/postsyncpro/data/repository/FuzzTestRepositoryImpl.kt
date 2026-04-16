package com.postsyncpro.data.repository

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.FuzzTestRepository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

class FuzzTestRepositoryImpl(
    private val httpClient: HttpClient
) : FuzzTestRepository {

    @Volatile
    private var isCancelled: Boolean = false

    override suspend fun generateTestCases(config: FuzzTestConfig): List<FuzzTestCase> {
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

    override fun executeTests(testCases: List<FuzzTestCase>, config: FuzzTestConfig): Flow<FuzzTestResult> = flow {
        isCancelled = false
        val semaphore = Semaphore(config.concurrency)

        for (testCase in testCases) {
            if (isCancelled) break

            semaphore.withPermit {
                if (isCancelled) return@withPermit

                val result = executeTestCase(testCase, config)
                emit(result)
            }
        }
    }

    override suspend fun cancelTests() {
        isCancelled = true
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
            val responseBody = response.bodyAsText()
            val passed = evaluateResult(testCase.mutationType, response.status.value)

            FuzzTestResult(
                testCase = testCase,
                httpStatus = response.status.value,
                responseBody = responseBody.take(2000), // Limit stored response size
                responseTimeMs = responseTime,
                passed = passed
            )
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
