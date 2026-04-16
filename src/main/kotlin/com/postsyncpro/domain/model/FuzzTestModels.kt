package com.postsyncpro.domain.model

data class FuzzTestConfig(
    val endpoint: EndpointInfo,
    val mutations: Set<MutationType>,
    val baseUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val concurrency: Int = 5
)

data class EndpointInfo(
    val path: String,
    val method: String,
    val parameters: List<SwaggerParameter> = emptyList(),
    val requestBody: SwaggerRequestBody? = null,
    val expectedSchema: SwaggerSchema? = null
)

enum class MutationType(val displayName: String, val description: String) {
    HAPPY_PATH("Happy Path", "All valid data based on schema"),
    NULL_VALUES("Null Values", "Send null for required fields"),
    EMPTY_STRINGS("Empty Strings", "Send empty strings instead of valid values"),
    TYPE_MISMATCH("Type Mismatch", "Send wrong data types (int for string, etc.)"),
    BOUNDARY_VALUES("Boundary Values", "Min/max values for numeric fields"),
    SQL_INJECTION("SQL Injection", "Common SQL injection payloads"),
    XSS_PAYLOADS("XSS Payloads", "Cross-site scripting test strings"),
    OVERFLOW("Overflow", "Extremely long strings and large numbers")
}

data class FuzzTestCase(
    val id: String,
    val mutationType: MutationType,
    val endpointPath: String,
    val method: String,
    val payload: String,
    val description: String
)

data class FuzzTestResult(
    val testCase: FuzzTestCase,
    val httpStatus: Int,
    val responseBody: String,
    val responseTimeMs: Long,
    val passed: Boolean,
    val errorMessage: String = ""
)

data class FuzzTestSuite(
    val endpoint: EndpointInfo,
    val results: List<FuzzTestResult>,
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val averageResponseTimeMs: Long
) {
    val passRate: Float get() = if (totalTests > 0) passedTests.toFloat() / totalTests else 0f
}
