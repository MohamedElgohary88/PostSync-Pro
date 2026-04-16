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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import net.datafaker.Faker
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MockServerRepositoryImpl : MockServerRepository {

    private val faker = Faker()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _requestLogs = MutableStateFlow<List<MockRequestLog>>(emptyList())
    override val requestLogs: StateFlow<List<MockRequestLog>> = _requestLogs.asStateFlow()

    private var server: ApplicationEngine? = null
    private val running = AtomicBoolean(false)

    override suspend fun startServer(config: MockServerConfig, routes: List<MockRoute>) {
        if (running.get()) {
            stopServer()
        }

        server = embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
            installPlugins()
            routing {
                registerRoutes(routes)
            }
        }

        scope.launch {
            server?.start(wait = false)
            running.set(true)
        }
    }

    override suspend fun stopServer() {
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

    private fun Application.installPlugins() {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        install(CORS) {
            anyHost()
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

        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respondText(
                    text = """{"error": "${cause.localizedMessage ?: "Internal server error"}"}""",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.InternalServerError
                )
            }
        }
    }

    // ── Route registration ─────────────────────────────────────────

    private fun Routing.registerRoutes(routes: List<MockRoute>) {
        routes.forEach { route ->
            val method = HttpMethod.parse(route.method.uppercase())
            val path = route.path

            route(path, method) {
                handle {
                    val startTime = System.currentTimeMillis()
                    val requestBody = try {
                        call.receiveText()
                    } catch (_: Exception) {
                        null
                    }

                    // Validate request body for POST/PUT/PATCH
                    if (method in listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch)) {
                        val validationError = validateRequestBody(requestBody, route)
                        if (validationError != null) {
                            val elapsed = System.currentTimeMillis() - startTime
                            logRequest(call, route, requestBody, 400, elapsed)
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

                    logRequest(call, route, requestBody, route.statusCode, elapsed)

                    call.respondText(
                        text = responseJson,
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.fromValue(route.statusCode)
                    )
                }
            }
        }
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
        return """{"error": "$message"}"""
    }

    // ── Response generation ────────────────────────────────────────

    private fun generateResponseFromSchema(schema: SwaggerSchema?): String {
        if (schema == null) return """{"status": "ok"}"""

        val element = generateJsonElement(schema)
        return Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), element)
    }

    private fun generateJsonElement(schema: SwaggerSchema): JsonElement {
        return when (schema.type.lowercase()) {
            "object" -> {
                val props = schema.properties.map { (key, propSchema) ->
                    key to generateJsonElement(propSchema)
                }
                JsonObject(props.toMap())
            }

            "array" -> {
                val itemSchema = schema.items ?: SwaggerSchema(type = "string")
                val count = faker.number().numberBetween(1, 4)
                val items = (1..count).map { generateJsonElement(itemSchema) }
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
                val min = schema.minimum?.toInt() ?: 1
                val max = schema.maximum?.toInt() ?: 1000
                JsonPrimitive(faker.number().numberBetween(min, max))
            }

            "number", "float", "double" -> {
                val min = schema.minimum ?: 0.0
                val max = schema.maximum ?: 1000.0
                JsonPrimitive(faker.number().randomDouble(2, min.toLong(), max.toLong()))
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
        var result = value
        schema.maxLength?.let { max ->
            if (result.length > max) result = result.take(max)
        }
        schema.minLength?.let { min ->
            while (result.length < min) result += faker.lorem().character()
        }
        return result
    }

    // ── Request logging ────────────────────────────────────────────

    private fun logRequest(
        call: ApplicationCall,
        route: MockRoute,
        requestBody: String?,
        statusCode: Int,
        responseTimeMs: Long
    ) {
        val log = MockRequestLog(
            timestamp = System.currentTimeMillis(),
            method = route.method.uppercase(),
            path = call.request.uri,
            requestBody = requestBody,
            responseStatus = statusCode,
            responseBody = "",
            responseTimeMs = responseTimeMs
        )

        _requestLogs.update { current -> current + log }
    }
}
