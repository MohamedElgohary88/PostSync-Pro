package com.postsyncpro.domain.usecase

import com.postsyncpro.data.parser.SwaggerParser
import com.postsyncpro.domain.model.*
import java.util.UUID

private data class SimpleEndpoint(
    val method: String,
    val path: String,
    val bodyTemplate: String = "",
    val tags: List<String> = emptyList()
)

/**
 * Module 12: Auto Flow — intelligently builds and orders the full test flow
 */
class AutoFlowUseCase {

    fun buildFlowPlan(spec: SwaggerSpec): AutoFlowPlan {
        val allEndpoints = extractAllEndpoints(spec)
        val authEndpoints = allEndpoints.filter { isAuthEndpoint(it) }
        val resourceEndpoints = allEndpoints.filter { !isAuthEndpoint(it) }
        val resourceGroups = groupByResource(resourceEndpoints)

        val phases = mutableListOf<FlowPhase>()
        var stepOrder = 0

        // Phase 1: Authentication
        if (authEndpoints.isNotEmpty()) {
            val authSteps = mutableListOf<FlowStep>()

            // Register first
            val registerEp = authEndpoints.find { isRegisterEndpoint(it) }
            if (registerEp != null) {
                authSteps.add(buildFlowStep(registerEp, stepOrder++, FlowCategory.AUTH_REGISTER, spec, false))
            }

            // Then login
            val loginEp = authEndpoints.find { isLoginEndpoint(it) }
            if (loginEp != null) {
                authSteps.add(buildFlowStep(loginEp, stepOrder++, FlowCategory.AUTH_LOGIN, spec, false))
            }

            // Other auth endpoints (refresh, etc.)
            authEndpoints.filter { !isRegisterEndpoint(it) && !isLoginEndpoint(it) }.forEach { ep ->
                authSteps.add(buildFlowStep(ep, stepOrder++, FlowCategory.OTHER, spec, true))
            }

            if (authSteps.isNotEmpty()) {
                phases.add(FlowPhase("Authentication", "Register, login, and get access token", authSteps))
            }
        }

        // Phase 2+: Each resource group as a CRUD phase
        for ((resource, endpoints) in resourceGroups) {
            val resourceSteps = mutableListOf<FlowStep>()
            val hasAuth = authEndpoints.isNotEmpty()

            // Order: POST (create) → GET all → GET one → PUT/PATCH (update) → DELETE
            val createEps = endpoints.filter { it.method == "POST" }
            val getAllEps = endpoints.filter { it.method == "GET" && !hasPathParam(it.path) }
            val getOneEps = endpoints.filter { it.method == "GET" && hasPathParam(it.path) }
            val updateEps = endpoints.filter { it.method in listOf("PUT", "PATCH") }
            val deleteEps = endpoints.filter { it.method == "DELETE" }
            val otherEps = endpoints.filter { it.method !in listOf("GET", "POST", "PUT", "PATCH", "DELETE") }

            createEps.forEach { resourceSteps.add(buildFlowStep(it, stepOrder++, FlowCategory.CREATE, spec, hasAuth)) }
            getAllEps.forEach { resourceSteps.add(buildFlowStep(it, stepOrder++, FlowCategory.READ_ALL, spec, hasAuth)) }
            getOneEps.forEach { resourceSteps.add(buildFlowStep(it, stepOrder++, FlowCategory.READ_ONE, spec, hasAuth)) }
            updateEps.forEach { resourceSteps.add(buildFlowStep(it, stepOrder++, FlowCategory.UPDATE, spec, hasAuth)) }
            deleteEps.forEach { resourceSteps.add(buildFlowStep(it, stepOrder++, FlowCategory.DELETE, spec, hasAuth)) }
            otherEps.forEach { resourceSteps.add(buildFlowStep(it, stepOrder++, FlowCategory.OTHER, spec, hasAuth)) }

            if (resourceSteps.isNotEmpty()) {
                phases.add(FlowPhase(
                    name = resource.replaceFirstChar { it.uppercase() },
                    description = "CRUD operations for $resource",
                    steps = resourceSteps
                ))
            }
        }

        val totalSteps = phases.sumOf { it.steps.size }
        return AutoFlowPlan(phases = phases, totalSteps = totalSteps)
    }

    fun generateRequestBody(schema: SwaggerSchema?, spec: SwaggerSpec): String {
        if (schema == null) return ""
        val resolved = SwaggerParser.resolveSchemaRef(schema, spec)
        return buildJsonFromSchema(resolved, spec, 0)
    }

    private fun buildJsonFromSchema(schema: SwaggerSchema, spec: SwaggerSpec, depth: Int): String {
        if (depth > 5) return "\"\""

        if (schema.ref.isNotEmpty()) {
            val resolved = SwaggerParser.resolveSchemaRef(schema, spec)
            return buildJsonFromSchema(resolved, spec, depth + 1)
        }

        return when (schema.type) {
            "object" -> {
                if (schema.properties.isEmpty()) return "{}"
                val fields = schema.properties.map { (name, propSchema) ->
                    val resolved = SwaggerParser.resolveSchemaRef(propSchema, spec)
                    "  \"$name\": ${buildJsonFromSchema(resolved, spec, depth + 1)}"
                }
                "{\n${fields.joinToString(",\n")}\n}"
            }
            "array" -> {
                val itemJson = schema.items?.let { buildJsonFromSchema(SwaggerParser.resolveSchemaRef(it, spec), spec, depth + 1) } ?: "\"\""
                "[$itemJson]"
            }
            "string" -> {
                if (schema.enumValues.isNotEmpty()) return "\"${schema.enumValues.first()}\""
                when {
                    schema.format == "email" || schema.description.contains("email", true) -> "\"testuser@example.com\""
                    schema.format == "password" || schema.description.contains("password", true) -> "\"TestPass123!\""
                    schema.format == "uri" || schema.format == "url" -> "\"https://example.com/image.png\""
                    schema.format == "uuid" -> "\"${UUID.randomUUID()}\""
                    schema.format == "date" -> "\"2025-01-15\""
                    schema.format == "date-time" -> "\"2025-01-15T10:30:00Z\""
                    schema.description.contains("phone", true) -> "\"+1234567890\""
                    schema.description.contains("name", true) -> "\"Test User\""
                    schema.description.contains("address", true) -> "\"123 Test Street\""
                    else -> "\"test_value\""
                }
            }
            "integer", "number" -> {
                val min = schema.minimum ?: 1.0
                min.toInt().toString()
            }
            "boolean" -> "true"
            else -> "\"\""
        }
    }

    fun buildExtractors(step: FlowStep, spec: SwaggerSpec, operation: SwaggerOperation): List<FlowExtractor> {
        val extractors = mutableListOf<FlowExtractor>()
        val resource = extractResourceName(step.path)

        // Extract from response schema
        val successResponse = operation.responses["201"]
            ?: operation.responses["200"]
            ?: operation.responses.entries.firstOrNull { it.key.startsWith("2") }?.value

        val responseSchema = successResponse?.content?.get("application/json")?.schema?.let {
            SwaggerParser.resolveSchemaRef(it, spec)
        }

        if (responseSchema != null) {
            val props = responseSchema.properties

            // Auto-extract tokens
            if (step.category == FlowCategory.AUTH_LOGIN || step.category == FlowCategory.AUTH_REGISTER) {
                if ("access_token" in props) extractors.add(FlowExtractor("access_token", "access_token"))
                if ("token" in props) extractors.add(FlowExtractor("access_token", "token"))
                if ("refresh_token" in props) extractors.add(FlowExtractor("refresh_token", "refresh_token"))
            }

            // Auto-extract IDs for CRUD chaining
            if (step.category == FlowCategory.CREATE) {
                if ("id" in props) extractors.add(FlowExtractor("${resource}_id", "id"))
                if ("_id" in props) extractors.add(FlowExtractor("${resource}_id", "_id"))
            }

            // Extract from data wrapper
            val dataSchema = props["data"]?.let { SwaggerParser.resolveSchemaRef(it, spec) }
            if (dataSchema != null) {
                if (step.category == FlowCategory.AUTH_LOGIN || step.category == FlowCategory.AUTH_REGISTER) {
                    if ("access_token" in dataSchema.properties) extractors.add(FlowExtractor("access_token", "data.access_token"))
                    if ("token" in dataSchema.properties) extractors.add(FlowExtractor("access_token", "data.token"))
                }
                if (step.category == FlowCategory.CREATE) {
                    if ("id" in dataSchema.properties) extractors.add(FlowExtractor("${resource}_id", "data.id"))
                }
            }

            // For list endpoints, extract first item's ID
            if (step.category == FlowCategory.READ_ALL) {
                if (responseSchema.type == "array" && responseSchema.items != null) {
                    val itemSchema = SwaggerParser.resolveSchemaRef(responseSchema.items, spec)
                    if ("id" in itemSchema.properties) {
                        extractors.add(FlowExtractor("${resource}_id", "0.id"))
                    }
                }
                // Check for data array wrapper
                if (dataSchema?.type == "array" && dataSchema.items != null) {
                    val itemSchema = SwaggerParser.resolveSchemaRef(dataSchema.items, spec)
                    if ("id" in itemSchema.properties) {
                        extractors.add(FlowExtractor("${resource}_id", "data.0.id"))
                    }
                }
            }
        }

        return extractors
    }

    // ─── Postman Collection Support ──────────────────────────────

    fun buildFlowPlanFromPostman(collection: PostmanCollection): AutoFlowPlan {
        val allItems = flattenPostmanItems(collection.item)
        val endpoints = allItems.mapNotNull { item ->
            val req = item.request ?: return@mapNotNull null
            val path = extractPostmanPath(req.url)
            if (path.isBlank()) return@mapNotNull null
            SimpleEndpoint(
                method = req.method.uppercase(),
                path = path,
                bodyTemplate = req.body?.raw ?: "",
                tags = emptyList()
            )
        }

        val authEndpoints = endpoints.filter { isAuthPath(it.path) }
        val resourceEndpoints = endpoints.filter { !isAuthPath(it.path) }
        val resourceGroups = resourceEndpoints.groupBy { extractResourceName(it.path) }.toSortedMap()

        val phases = mutableListOf<FlowPhase>()
        var stepOrder = 0

        // Phase 1: Authentication
        if (authEndpoints.isNotEmpty()) {
            val authSteps = mutableListOf<FlowStep>()

            val registerEp = authEndpoints.find { it.path.lowercase().let { p -> p.contains("register") || p.contains("signup") } && it.method == "POST" }
            if (registerEp != null) {
                authSteps.add(buildSimpleFlowStep(registerEp, stepOrder++, FlowCategory.AUTH_REGISTER, authEndpoints.isNotEmpty()))
            }

            val loginEp = authEndpoints.find { it.path.lowercase().contains("login") && it.method == "POST" }
            if (loginEp != null) {
                authSteps.add(buildSimpleFlowStep(loginEp, stepOrder++, FlowCategory.AUTH_LOGIN, false))
            }

            authEndpoints.filter { ep ->
                val p = ep.path.lowercase()
                !((p.contains("register") || p.contains("signup")) && ep.method == "POST") &&
                !(p.contains("login") && ep.method == "POST")
            }.forEach { ep ->
                authSteps.add(buildSimpleFlowStep(ep, stepOrder++, FlowCategory.OTHER, true))
            }

            if (authSteps.isNotEmpty()) {
                phases.add(FlowPhase("Authentication", "Register, login, and get access token", authSteps))
            }
        }

        // Phase 2+: Resource CRUD
        for ((resource, eps) in resourceGroups) {
            val resourceSteps = mutableListOf<FlowStep>()
            val hasAuth = authEndpoints.isNotEmpty()

            val createEps = eps.filter { it.method == "POST" }
            val getAllEps = eps.filter { it.method == "GET" && !hasPathParam(it.path) }
            val getOneEps = eps.filter { it.method == "GET" && hasPathParam(it.path) }
            val updateEps = eps.filter { it.method in listOf("PUT", "PATCH") }
            val deleteEps = eps.filter { it.method == "DELETE" }
            val otherEps = eps.filter { it.method !in listOf("GET", "POST", "PUT", "PATCH", "DELETE") }

            createEps.forEach { resourceSteps.add(buildSimpleFlowStep(it, stepOrder++, FlowCategory.CREATE, hasAuth)) }
            getAllEps.forEach { resourceSteps.add(buildSimpleFlowStep(it, stepOrder++, FlowCategory.READ_ALL, hasAuth)) }
            getOneEps.forEach { resourceSteps.add(buildSimpleFlowStep(it, stepOrder++, FlowCategory.READ_ONE, hasAuth)) }
            updateEps.forEach { resourceSteps.add(buildSimpleFlowStep(it, stepOrder++, FlowCategory.UPDATE, hasAuth)) }
            deleteEps.forEach { resourceSteps.add(buildSimpleFlowStep(it, stepOrder++, FlowCategory.DELETE, hasAuth)) }
            otherEps.forEach { resourceSteps.add(buildSimpleFlowStep(it, stepOrder++, FlowCategory.OTHER, hasAuth)) }

            if (resourceSteps.isNotEmpty()) {
                phases.add(FlowPhase(
                    name = resource.replaceFirstChar { it.uppercase() },
                    description = "CRUD operations for $resource",
                    steps = resourceSteps
                ))
            }
        }

        return AutoFlowPlan(phases = phases, totalSteps = phases.sumOf { it.steps.size })
    }

    private fun flattenPostmanItems(items: List<PostmanItem>): List<PostmanItem> {
        val result = mutableListOf<PostmanItem>()
        for (item in items) {
            if (item.request != null) {
                result.add(item)
            }
            if (item.item.isNotEmpty()) {
                result.addAll(flattenPostmanItems(item.item))
            }
        }
        return result
    }

    private fun extractPostmanPath(url: PostmanUrl?): String {
        if (url == null) return ""
        // If path segments exist, use them
        if (url.path.isNotEmpty()) {
            val pathStr = "/" + url.path.joinToString("/")
            // Convert :param to {param} for consistency
            return pathStr.replace(Regex(":([a-zA-Z_]+)"), "{$1}")
        }
        // Fallback: parse from raw URL
        val raw = url.raw
        if (raw.isBlank()) return ""
        return try {
            val withoutProtocol = raw.removePrefix("http://").removePrefix("https://")
            val pathStart = withoutProtocol.indexOf("/")
            if (pathStart >= 0) {
                val path = withoutProtocol.substring(pathStart).split("?").first()
                path.replace(Regex(":([a-zA-Z_]+)"), "{$1}")
            } else ""
        } catch (e: Exception) { "" }
    }

    private fun isAuthPath(path: String): Boolean {
        val p = path.lowercase()
        return p.contains("/auth/") || p.contains("/login") || p.contains("/register") ||
                p.contains("/signup") || p.contains("/token") || p.contains("/refresh")
    }

    private fun buildSimpleFlowStep(
        ep: SimpleEndpoint,
        order: Int,
        category: FlowCategory,
        requiresAuth: Boolean
    ): FlowStep {
        val resource = extractResourceName(ep.path)
        val label = "${category.displayName} ${resource.replaceFirstChar { it.uppercase() }}"

        // Build smart extractors based on category
        val extractors = mutableListOf<FlowExtractor>()
        if (category == FlowCategory.AUTH_LOGIN || category == FlowCategory.AUTH_REGISTER) {
            extractors.add(FlowExtractor("access_token", "access_token"))
            extractors.add(FlowExtractor("refresh_token", "refresh_token"))
            // Also try nested data.access_token
            extractors.add(FlowExtractor("access_token", "data.access_token"))
            extractors.add(FlowExtractor("access_token", "token"))
            extractors.add(FlowExtractor("access_token", "data.token"))
        }
        if (category == FlowCategory.CREATE) {
            extractors.add(FlowExtractor("${resource}_id", "id"))
            extractors.add(FlowExtractor("${resource}_id", "_id"))
            extractors.add(FlowExtractor("${resource}_id", "data.id"))
            extractors.add(FlowExtractor("${resource}_id", "data._id"))
        }
        if (category == FlowCategory.READ_ALL) {
            extractors.add(FlowExtractor("${resource}_id", "0.id"))
            extractors.add(FlowExtractor("${resource}_id", "data.0.id"))
        }

        return FlowStep(
            id = UUID.randomUUID().toString(),
            order = order,
            method = ep.method,
            path = ep.path,
            label = label,
            category = category,
            requiresAuth = requiresAuth,
            bodyTemplate = ep.bodyTemplate,
            extractors = extractors
        )
    }

    // ─── Helpers ────────────────────────────────────────────────

    private data class RawEndpoint(val method: String, val path: String, val operation: SwaggerOperation)

    private fun extractAllEndpoints(spec: SwaggerSpec): List<RawEndpoint> {
        return spec.paths.flatMap { (path, methods) ->
            methods.map { (method, operation) ->
                RawEndpoint(method.uppercase(), path, operation)
            }
        }
    }

    private fun isAuthEndpoint(ep: RawEndpoint): Boolean {
        val p = ep.path.lowercase()
        return p.contains("/auth/") || p.contains("/login") || p.contains("/register") ||
                p.contains("/signup") || p.contains("/token") || p.contains("/refresh") ||
                ep.operation.tags.any { it.lowercase() in listOf("auth", "authentication", "authorization") }
    }

    private fun isRegisterEndpoint(ep: RawEndpoint): Boolean {
        val p = ep.path.lowercase()
        return (p.contains("register") || p.contains("signup")) && ep.method == "POST"
    }

    private fun isLoginEndpoint(ep: RawEndpoint): Boolean {
        val p = ep.path.lowercase()
        return p.contains("login") && ep.method == "POST"
    }

    private fun hasPathParam(path: String): Boolean = path.contains("{")

    private fun groupByResource(endpoints: List<RawEndpoint>): Map<String, List<RawEndpoint>> {
        return endpoints.groupBy { extractResourceName(it.path) }
            .toSortedMap()
    }

    private fun extractResourceName(path: String): String {
        val segments = path.split("/").filter { it.isNotBlank() && !it.startsWith("{") }
        // Skip common prefixes like "api", "v1", "v2"
        val meaningful = segments.filter { it.lowercase() !in listOf("api", "v1", "v2", "v3") }
        return meaningful.lastOrNull()?.lowercase() ?: segments.lastOrNull()?.lowercase() ?: "unknown"
    }

    private fun buildFlowStep(
        ep: RawEndpoint,
        order: Int,
        category: FlowCategory,
        spec: SwaggerSpec,
        requiresAuth: Boolean
    ): FlowStep {
        val requestSchema = ep.operation.requestBody?.content?.get("application/json")?.schema
        val bodyTemplate = if (requestSchema != null && ep.method in listOf("POST", "PUT", "PATCH")) {
            generateRequestBody(requestSchema, spec)
        } else ""

        val resource = extractResourceName(ep.path)
        val label = "${category.displayName} ${resource.replaceFirstChar { it.uppercase() }}"
        val stepId = UUID.randomUUID().toString()
        val extractors = buildExtractors(
            FlowStep(
                id = stepId,
                order = order,
                method = ep.method,
                path = ep.path,
                label = label,
                category = category,
                requiresAuth = requiresAuth,
                bodyTemplate = bodyTemplate
            ),
            spec,
            ep.operation
        )

        return FlowStep(
            id = stepId,
            order = order,
            method = ep.method,
            path = ep.path,
            label = label,
            category = category,
            requiresAuth = requiresAuth,
            bodyTemplate = bodyTemplate,
            extractors = extractors
        )
    }
}
