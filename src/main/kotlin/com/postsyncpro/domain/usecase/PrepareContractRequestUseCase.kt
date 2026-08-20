package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.ApiKeyLocation
import com.postsyncpro.domain.model.PreparedWorkbenchRequest
import com.postsyncpro.domain.model.SwaggerOperation
import com.postsyncpro.domain.model.SwaggerParameter
import com.postsyncpro.domain.model.SwaggerSchema
import com.postsyncpro.domain.model.SwaggerSecurityScheme
import com.postsyncpro.domain.model.SwaggerSpec
import com.postsyncpro.domain.model.WorkbenchAuth
import com.postsyncpro.domain.model.WorkbenchHttpMethod
import com.postsyncpro.domain.model.WorkbenchRequestDraft
import com.postsyncpro.domain.model.WorkbenchRequestField
import com.postsyncpro.domain.model.WorkbenchVariable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

class PrepareContractRequestUseCase(
    private val redactor: SecretRedactor = SecretRedactor()
) {
    private val prettyJson = Json { prettyPrint = true }
    private val pathTemplate = Regex("\\{([^{}]+)}")

    operator fun invoke(
        spec: SwaggerSpec,
        path: String,
        method: String,
        serverIndex: Int = 0
    ): PreparedWorkbenchRequest {
        val operation = findOperation(spec, path, method)
            ?: throw IllegalArgumentException("Operation $method $path was not found")
        val httpMethod = runCatching { WorkbenchHttpMethod.valueOf(method.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Unsupported HTTP method: $method") }

        val variables = linkedMapOf<String, WorkbenchVariable>()
        val server = spec.servers.getOrNull(serverIndex)
        var serverUrl = server?.url.orEmpty().trimEnd('/')
        server?.variables?.forEach { (name, definition) ->
            val variableName = "server.$name"
            variables[variableName] = WorkbenchVariable(
                value = definition.defaultValue,
                enabled = definition.defaultValue.isNotEmpty()
            )
            serverUrl = serverUrl.replace("{$name}", "{{$variableName}}")
        }
        val baseUrl = when {
            serverUrl.startsWith("http://", ignoreCase = true) ||
                serverUrl.startsWith("https://", ignoreCase = true) -> serverUrl
            serverUrl.isBlank() -> "{{baseUrl}}"
            serverUrl.startsWith('/') -> "{{baseUrl}}$serverUrl"
            else -> "{{baseUrl}}/$serverUrl"
        }
        if (baseUrl.startsWith("{{baseUrl}}")) {
            variables["baseUrl"] = WorkbenchVariable(enabled = false)
        }

        val templatedPath = pathTemplate.replace(path) { "{{${it.groupValues[1]}}}" }
        val url = "$baseUrl${if (templatedPath.startsWith('/')) templatedPath else "/$templatedPath"}"

        val query = mutableListOf<WorkbenchRequestField>()
        val headers = mutableListOf<WorkbenchRequestField>()
        operation.parameters.forEach { parameter ->
            val example = parameter.example ?: parameter.schema?.example ?: parameter.schema?.defaultValue
            val initialValue = example?.let(::elementToFieldValue).orEmpty()
            val variable = WorkbenchVariable(
                value = initialValue,
                enabled = example != null,
                secret = redactor.isSensitiveKey(parameter.name)
            )
            variables[parameter.name] = variable
            val field = WorkbenchRequestField(
                key = parameter.name,
                value = "{{${parameter.name}}}",
                enabled = parameter.required,
                sensitive = variable.secret
            )

            when (parameter.location.lowercase()) {
                "path" -> if (parameter.required || example != null) {
                    variables[parameter.name] = variable.copy(enabled = example != null)
                }
                "query" -> query += field
                "header" -> headers += field
                "cookie" -> headers += field.copy(
                    key = "Cookie",
                    value = "${parameter.name}={{${parameter.name}}}",
                    sensitive = true
                )
            }
        }

        val media = operation.requestBody?.content?.entries?.let { content ->
            content.firstOrNull { it.key.equals("application/json", ignoreCase = true) }
                ?: content.firstOrNull()
        }
        val bodyElement = media?.value?.example
            ?: media?.value?.schema?.example
            ?: media?.value?.schema?.let { generateExample(it, spec, 0, emptySet()) }
        val body = bodyElement?.let { prettyJson.encodeToString(JsonElement.serializer(), it) }.orEmpty()
        if (media != null) {
            headers += WorkbenchRequestField("Content-Type", media.key, enabled = true)
        }

        val security = operation.security ?: spec.security
        val auth = prepareAuth(security, spec, variables)
        val title = operation.summary.ifBlank {
            operation.operationId.ifBlank { "${httpMethod.name} $path" }
        }

        return PreparedWorkbenchRequest(
            draft = WorkbenchRequestDraft(
                name = title,
                method = httpMethod,
                url = url,
                query = query,
                headers = headers,
                body = body,
                bodyEnabled = media != null,
                auth = auth
            ),
            variables = variables
        )
    }

    private fun prepareAuth(
        security: List<Map<String, List<String>>>,
        spec: SwaggerSpec,
        variables: MutableMap<String, WorkbenchVariable>
    ): WorkbenchAuth {
        val requirement = security.firstOrNull() ?: return WorkbenchAuth.None
        for (schemeName in requirement.keys) {
            val scheme = spec.components?.securitySchemes?.get(schemeName) ?: continue
            when {
                scheme.type.equals("apiKey", ignoreCase = true) -> {
                    variables[schemeName] = WorkbenchVariable(enabled = false, secret = true)
                    return WorkbenchAuth.ApiKey(
                        name = scheme.name.ifBlank { schemeName },
                        value = "{{$schemeName}}",
                        location = when (scheme.location.lowercase()) {
                            "query" -> ApiKeyLocation.QUERY
                            "cookie" -> ApiKeyLocation.COOKIE
                            else -> ApiKeyLocation.HEADER
                        }
                    )
                }
                scheme.type.equals("http", ignoreCase = true) &&
                    scheme.scheme.equals("basic", ignoreCase = true) -> {
                    val username = "${schemeName}Username"
                    val password = "${schemeName}Password"
                    variables[username] = WorkbenchVariable(enabled = false)
                    variables[password] = WorkbenchVariable(enabled = false, secret = true)
                    return WorkbenchAuth.Basic("{{$username}}", "{{$password}}")
                }
                scheme.type.equals("http", ignoreCase = true) ||
                    scheme.type.equals("oauth2", ignoreCase = true) ||
                    scheme.type.equals("openIdConnect", ignoreCase = true) -> {
                    variables[schemeName] = WorkbenchVariable(enabled = false, secret = true)
                    return WorkbenchAuth.Bearer("{{$schemeName}}")
                }
            }
        }
        return WorkbenchAuth.None
    }

    private fun generateExample(
        schema: SwaggerSchema,
        spec: SwaggerSpec,
        depth: Int,
        visitedRefs: Set<String>
    ): JsonElement {
        if (depth >= MAX_EXAMPLE_DEPTH) return JsonNull
        schema.example?.let { return it }
        schema.defaultValue?.let { return it }
        schema.enumValues.firstOrNull()?.let { enumValue ->
            return when (schema.type.lowercase()) {
                "integer" -> JsonPrimitive(enumValue.toLongOrNull() ?: 0L)
                "number" -> JsonPrimitive(enumValue.toDoubleOrNull() ?: 0.0)
                "boolean" -> JsonPrimitive(enumValue.toBooleanStrictOrNull() ?: false)
                else -> JsonPrimitive(enumValue)
            }
        }

        if (schema.ref.isNotBlank()) {
            if (schema.ref in visitedRefs || !schema.ref.startsWith(SCHEMA_PREFIX)) return JsonNull
            val name = schema.ref.removePrefix(SCHEMA_PREFIX).replace("~1", "/").replace("~0", "~")
            val resolved = spec.components?.schemas?.get(name) ?: return JsonNull
            return generateExample(resolved, spec, depth + 1, visitedRefs + schema.ref)
        }

        val type = schema.type.ifBlank {
            when {
                schema.properties.isNotEmpty() -> "object"
                schema.items != null -> "array"
                else -> "string"
            }
        }
        return when (type.lowercase()) {
            "object" -> buildJsonObject {
                schema.properties.entries.take(MAX_EXAMPLE_PROPERTIES).forEach { (name, property) ->
                    put(name, generateExample(property, spec, depth + 1, visitedRefs))
                }
            }
            "array" -> buildJsonArray {
                schema.items?.let { add(generateExample(it, spec, depth + 1, visitedRefs)) }
            }
            "integer" -> JsonPrimitive(schema.minimum?.toLong() ?: 0L)
            "number" -> JsonPrimitive(schema.minimum ?: 0.0)
            "boolean" -> JsonPrimitive(false)
            else -> JsonPrimitive(
                when (schema.format.lowercase()) {
                    "date" -> "2026-01-01"
                    "date-time" -> "2026-01-01T00:00:00Z"
                    "email" -> "user@example.com"
                    "uuid" -> "00000000-0000-4000-8000-000000000000"
                    else -> "string"
                }
            )
        }
    }

    private fun elementToFieldValue(element: JsonElement): String = when (element) {
        is JsonPrimitive -> element.content
        else -> element.toString()
    }

    private fun findOperation(spec: SwaggerSpec, path: String, method: String): SwaggerOperation? =
        spec.paths[path]?.entries?.firstOrNull { it.key.equals(method, ignoreCase = true) }?.value

    private companion object {
        const val MAX_EXAMPLE_DEPTH = 12
        const val MAX_EXAMPLE_PROPERTIES = 100
        const val SCHEMA_PREFIX = "#/components/schemas/"
    }
}
