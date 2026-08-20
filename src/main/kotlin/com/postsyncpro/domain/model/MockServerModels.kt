package com.postsyncpro.domain.model

/**
 * Module 6: Smart Local Mock Server models
 */
data class MockServerConfig(
    val port: Int = 8080,
    val isRunning: Boolean = false,
    val enableValidation: Boolean = true,
    val enableLogging: Boolean = true,
    /**
     * The mock server is intentionally loopback-only by default. Callers that
     * deliberately expose it to a network must opt in with a different host.
     */
    val host: String = "127.0.0.1",
    val maxRequestBodyBytes: Int = 1_048_576,
    val maxLogEntries: Int = 500,
    /** Origins are expressed as host[:port] values, without a path. */
    val allowedCorsOrigins: Set<String> = emptySet()
)

data class MockRoute(
    val path: String,
    val method: String,
    val requestSchema: SwaggerSchema? = null,
    val responseSchema: SwaggerSchema? = null,
    val requiredFields: List<String> = emptyList(),
    val srsRules: List<SrsRule> = emptyList(),
    val statusCode: Int = 200
)

data class MockRequestLog(
    val timestamp: Long = System.currentTimeMillis(),
    val method: String,
    val path: String,
    val requestBody: String?,
    val responseStatus: Int,
    val responseBody: String,
    val responseTimeMs: Long,
    val validationErrors: List<String> = emptyList()
)
