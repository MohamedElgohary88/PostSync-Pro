package com.postsyncpro.data.repository

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.MockServerRepository
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import net.datafaker.Faker
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import io.ktor.utils.io.*

class MockServerRepositoryImpl : MockServerRepository {

    private companion object {
        const val MAX_CONFIGURED_REQUEST_BODY_BYTES = 10 * 1024 * 1024
        const val MAX_CONFIGURED_LOG_ENTRIES = 10_000
        const val MAX_LOG_BODY_CHARS = 16_384
        const val MAX_LOG_PATH_CHARS = 2_048
        const val MAX_SCHEMA_DEPTH = 16
        const val MAX_SCHEMA_PROPERTIES = 200
        const val MAX_GENERATED_STRING_LENGTH = 16_384

        val JSON_SECRET_PATTERN = Regex(
            """(?i)("(?:password|passcode|secret|token|access_token|refresh_token|api[_-]?key|authorization|cookie|client_secret)"\s*:\s*)("(?:\\.|[^"\\])*"|[^,}\s]+)"""
        )
        val QUERY_SECRET_PATTERN = Regex(
            """(?i)([?&](?:password|secret|token|access_token|refresh_token|api[_-]?key|authorization|client_secret)=)[^&#\s]*"""
        )
        val BEARER_PATTERN = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")
    }

    private val faker = Faker()
    private val lifecycleMutex = Mutex()

    private val _requestLogs = MutableStateFlow<List<MockRequestLog>>(emptyList())
    override val requestLogs: StateFlow<List<MockRequestLog>> = _requestLogs.asStateFlow()

    private var server: ApplicationEngine? = null
    private val running = AtomicBoolean(false)

    override suspend fun startServer(config: MockServerConfig, routes: List<MockRoute>) = lifecycleMutex.withLock {
        require(config.port in 1..65535) { "Invalid mock server port" }
        require(routes.isNotEmpty()) { "At least one mock route is required" }
        require(config.host.isBlank() || config.host in setOf("127.0.0.1", "localhost", "::1")) {
            "The mock server may only bind to the local machine"
        }
        routes.forEach { route ->
            require(route.path.startsWith('/')) { "Mock route paths must start with '/'" }
            require(route.statusCode in 100..599) { "Invalid mock response status" }
        }

        val safeConfig = config.copy(
            host = "127.0.0.1",
            maxRequestBodyBytes = config.maxRequestBodyBytes.coerceIn(1, MAX_CONFIGURED_REQUEST_BODY_BYTES),
            maxLogEntries = config.maxLogEntries.coerceIn(1, MAX_CONFIGURED_LOG_ENTRIES)
        )

        stopServerLocked()

        val candidate = embeddedServer(Netty, port = safeConfig.port, host = safeConfig.host) {
            installPlugins(safeConfig)
            routing {
                registerRoutes(routes, safeConfig)
            }
        }

        try {
            candidate.start(wait = false)
            server = candidate
            running.set(true)
        } catch (e: Exception) {
            runCatching { candidate.stop(0, 1_000) }
            server = null
            running.set(false)
            throw e
        }
    }

    override suspend fun stopServer() = lifecycleMutex.withLock {
        stopServerLocked()
    }

    private fun stopServerLocked() {
        try {
            server?.stop(1000, 2000)
        } catch (_: Exception) {
            // Swallow shutdown errors
        } finally {
            server = null
            running.set(false)
        }
    }

    override fun isRunning(): Boolean = running.get()

    override fun clearLogs() {
        _requestLogs.value = emptyList()
    }

    // ── Ktor plugin installation ───────────────────────────────────

    private fun Application.installPlugins(config: MockServerConfig) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        if (config.allowedCorsOrigins.isNotEmpty()) {
            install(CORS) {
                config.allowedCorsOrigins.forEach { origin ->
                    allowHost(origin, schemes = listOf("http", "https"))
                }
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.Accept)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Patch)
                allowMethod(HttpMethod.Options)
            }
        }

        install(StatusPages) {
            exception<RequestBodyTooLargeException> { call, _ ->
                call.respondText(
                    text = """{"error":"Request body exceeds the configured limit"}""",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.PayloadTooLarge
                )
            }
            exception<Throwable> { call, _ ->
                call.respondText(
                    text = """{"error":"Internal server error"}""",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.InternalServerError
                )
            }
        }
    }

    // ── Route registration ─────────────────────────────────────────

    private fun Routing.registerRoutes(routes: List<MockRoute>, config: MockServerConfig) {
        routes.forEach { route ->
            val method = HttpMethod.parse(route.method.uppercase())
            val path = route.path

            route(path, method) {
                handle {
                    val startTime = System.currentTimeMillis()
                    val hasRequestBody = method in listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)
                    val requestBody = if (hasRequestBody) {
                        call.receiveTextLimited(config.maxRequestBodyBytes)
                    } else null

                    // Validate request body for POST/PUT/PATCH
                    if (config.enableValidation && hasRequestBody) {
                        val validationError = validateRequestBody(requestBody, route)
                        if (validationError != null) {
                            val elapsed = System.currentTimeMillis() - startTime
                            if (config.enableLogging) {
                                logRequest(call, route, requestBody, 400, elapsed, config.maxLogEntries)
                            }
                            call.respondText(
                                text = validationError,
                                contentType = ContentType.Application.Json,
                                status = HttpStatusCode.BadRequest
                            )
                            return@handle
                        }
                    }

                    // Generate response
                    val responseJson = generateResponseFromSchema(route.responseSchema)
                    val elapsed = System.currentTimeMillis() - startTime

                    if (config.enableLogging) {
                        logRequest(call, route, requestBody, route.statusCode, elapsed, config.maxLogEntries)
                    }

                    call.respondText(
                        text = responseJson,
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.fromValue(route.statusCode)
                    )
                }
            }
        }
    }

    private suspend fun ApplicationCall.receiveTextLimited(limit: Int): String {
        val declaredLength = request.contentLength()
        if (declaredLength != null && declaredLength > limit) {
            throw RequestBodyTooLargeException()
        }

        val channel = receiveChannel()
        val output = ByteArrayOutputStream(minOf(limit, 8_192))
        val buffer = ByteArray(8_192)
        var total = 0

        while (true) {
            val read = channel.readAvailable(buffer)
            if (read == -1) break
            if (read == 0) {
                kotlinx.coroutines.yield()
                continue
            }
            if (total > limit - read) {
                channel.cancel(RequestBodyTooLargeException())
                throw RequestBodyTooLargeException()
            }
            output.write(buffer, 0, read)
            total += read
        }

        return output.toString(StandardCharsets.UTF_8)
    }

    // ── Request validation ─────────────────────────────────────────

    private fun validateRequestBody(body: String?, route: MockRoute): String? {
        val requiredFields = route.requiredFields.ifEmpty {
            route.requestSchema?.required ?: emptyList()
        }
        if (requiredFields.isEmpty()) return null

        if (body.isNullOrBlank()) {
            return buildJsonError("Request body is required. Missing fields: ${requiredFields.joinToString()}")
        }

        val jsonObject = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            return buildJsonError("Invalid JSON in request body")
        }

        val missingFields = requiredFields.filter { field -> !jsonObject.containsKey(field) }
        if (missingFields.isNotEmpty()) {
            return buildJsonError("Missing required fields: ${missingFields.joinToString()}")
        }

        return null
    }

    private fun buildJsonError(message: String): String {
        return buildJsonObject { put("error", message) }.toString()
    }

    // ── Response generation ────────────────────────────────────────

    private fun generateResponseFromSchema(schema: SwaggerSchema?): String {
        if (schema == null) return """{"status": "ok"}"""

        val element = generateJsonElement(schema, depth = 0)
        return Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), element)
    }

    private fun generateJsonElement(schema: SwaggerSchema, depth: Int): JsonElement {
        if (depth >= MAX_SCHEMA_DEPTH) return JsonNull

        return when (schema.type.lowercase()) {
            "object" -> {
                val props = schema.properties.entries.take(MAX_SCHEMA_PROPERTIES).map { (key, propSchema) ->
                    key to generateJsonElement(propSchema, depth + 1)
                }
                JsonObject(props.toMap())
            }

            "array" -> {
                val itemSchema = schema.items ?: SwaggerSchema(type = "string")
                val count = faker.number().numberBetween(1, 4)
                val items = (1..count).map { generateJsonElement(itemSchema, depth + 1) }
                JsonArray(items)
            }

            "string" -> {
                if (schema.enumValues.isNotEmpty()) {
                    JsonPrimitive(schema.enumValues.random())
                } else {
                    JsonPrimitive(generateStringValue(schema))
                }
            }

            "integer", "int", "int32", "int64" -> {
                val min = (schema.minimum?.toInt() ?: 1).coerceIn(-1_000_000, 1_000_000)
                val max = (schema.maximum?.toInt() ?: 1000).coerceIn(-1_000_000, 1_000_000)
                JsonPrimitive(if (min < max) faker.number().numberBetween(min, max) else min)
            }

            "number", "float", "double" -> {
                val min = (schema.minimum ?: 0.0).coerceIn(-1_000_000.0, 1_000_000.0)
                val max = (schema.maximum ?: 1000.0).coerceIn(-1_000_000.0, 1_000_000.0)
                JsonPrimitive(if (min < max) faker.number().randomDouble(2, min.toLong(), max.toLong()) else min)
            }

            "boolean" -> {
                JsonPrimitive(faker.bool().bool())
            }

            else -> JsonPrimitive(faker.lorem().word())
        }
    }

    private fun generateStringValue(schema: SwaggerSchema): String {
        val format = schema.format.lowercase()
        val value = when {
            format.contains("email") || format == "email" -> faker.internet().emailAddress()
            format.contains("uri") || format == "url" -> faker.internet().url()
            format.contains("uuid") -> faker.internet().uuid()
            format.contains("date-time") -> LocalDateTime.now().toString()
            format.contains("date") -> faker.date().birthday().toString()
            format.contains("phone") -> faker.phoneNumber().cellPhone()
            format.contains("name") -> faker.name().fullName()
            format.contains("address") -> faker.address().fullAddress()
            format.contains("ip") -> faker.internet().ipV4Address()
            else -> {
                val word = faker.lorem().sentence(3)
                applyStringConstraints(word, schema)
            }
        }
        return applyStringConstraints(value, schema)
    }

    private fun applyStringConstraints(value: String, schema: SwaggerSchema): String {
        val max = (schema.maxLength ?: MAX_GENERATED_STRING_LENGTH)
            .coerceIn(0, MAX_GENERATED_STRING_LENGTH)
        val min = (schema.minLength ?: 0)
            .coerceIn(0, max)

        val truncated = value.take(max)
        if (truncated.length >= min) return truncated

        return buildString(min) {
            append(truncated)
            while (length < min) {
                append(faker.lorem().character())
            }
            if (length > min) {
                setLength(min)
            }
        }
    }

    // ── Request logging ────────────────────────────────────────────

    private fun logRequest(
        call: ApplicationCall,
        route: MockRoute,
        requestBody: String?,
        statusCode: Int,
        responseTimeMs: Long,
        maxLogEntries: Int
    ) {
        val log = MockRequestLog(
            timestamp = System.currentTimeMillis(),
            method = route.method.uppercase(),
            path = redactSecrets(call.request.uri).take(MAX_LOG_PATH_CHARS),
            requestBody = requestBody?.let { redactSecrets(it).take(MAX_LOG_BODY_CHARS) },
            responseStatus = statusCode,
            responseBody = "",
            responseTimeMs = responseTimeMs
        )

        _requestLogs.update { current ->
            if (current.size < maxLogEntries) current + log
            else current.drop(current.size - maxLogEntries + 1) + log
        }
    }

    private fun redactSecrets(value: String): String {
        val jsonRedacted = JSON_SECRET_PATTERN.replace(value) { match ->
            "${match.groupValues[1]}\"[REDACTED]\""
        }
        val queryRedacted = QUERY_SECRET_PATTERN.replace(jsonRedacted) { match ->
            "${match.groupValues[1]}[REDACTED]"
        }
        return BEARER_PATTERN.replace(queryRedacted, "Bearer [REDACTED]")
    }

    private class RequestBodyTooLargeException : RuntimeException()
}
