package com.postsyncpro.data.parser

import com.postsyncpro.domain.model.EndpointInfo
import com.postsyncpro.domain.model.SwaggerMediaType
import com.postsyncpro.domain.model.SwaggerOperation
import com.postsyncpro.domain.model.SwaggerParameter
import com.postsyncpro.domain.model.SwaggerSchema
import com.postsyncpro.domain.model.SwaggerSpec
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
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.IdentityHashMap

data class SwaggerParseLimits(
    val maxInputBytes: Int = 5 * 1024 * 1024,
    val maxNestingDepth: Int = 128,
    val maxAliases: Int = 20,
    val maxOperations: Int = 10_000,
    val maxSchemas: Int = 10_000,
    val maxRefDepth: Int = 48
)

object SwaggerParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }
    private val httpMethods = setOf("get", "post", "put", "patch", "delete", "head", "options", "trace")

    /** Parses OpenAPI JSON or YAML without enabling arbitrary YAML constructors. */
    fun parse(input: String, limits: SwaggerParseLimits = SwaggerParseLimits()): SwaggerSpec {
        validateLimits(limits)
        require(input.isNotBlank()) { "OpenAPI document is empty" }
        val bytes = input.toByteArray(Charsets.UTF_8)
        require(bytes.size <= limits.maxInputBytes) {
            "OpenAPI document exceeds the ${limits.maxInputBytes}-byte limit"
        }
        return parseValidatedInput(input, limits)
    }

    /** Bounded file API for callers that do not already hold the document in memory. */
    fun parse(path: Path, limits: SwaggerParseLimits = SwaggerParseLimits()): SwaggerSpec {
        validateLimits(limits)
        require(Files.size(path) <= limits.maxInputBytes) {
            "OpenAPI document exceeds the ${limits.maxInputBytes}-byte limit"
        }
        return Files.newInputStream(path).use { parse(it, limits) }
    }

    /** Reads at most maxInputBytes + one sentinel byte before decoding. */
    fun parse(input: InputStream, limits: SwaggerParseLimits = SwaggerParseLimits()): SwaggerSpec {
        validateLimits(limits)
        val output = ByteArrayOutputStream(minOf(limits.maxInputBytes, 16 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer, 0, minOf(buffer.size, limits.maxInputBytes + 1 - total))
            if (read < 0) break
            if (read == 0) {
                val single = input.read()
                if (single < 0) break
                total++
                require(total <= limits.maxInputBytes) {
                    "OpenAPI document exceeds the ${limits.maxInputBytes}-byte limit"
                }
                output.write(single)
                continue
            }
            total += read
            require(total <= limits.maxInputBytes) {
                "OpenAPI document exceeds the ${limits.maxInputBytes}-byte limit"
            }
            output.write(buffer, 0, read)
        }
        return parseValidatedInput(output.toByteArray().toString(Charsets.UTF_8), limits)
    }

    private fun parseValidatedInput(input: String, limits: SwaggerParseLimits): SwaggerSpec {
        val normalizedInput = input.removePrefix("\uFEFF")

        val parsed = if (normalizedInput.firstOrNull { !it.isWhitespace() } in setOf('{', '[')) {
            enforceJsonDepth(normalizedInput, limits.maxNestingDepth)
            json.parseToJsonElement(normalizedInput)
        } else {
            parseYaml(normalizedInput, limits)
        }

        val root = parsed as? JsonObject
            ?: throw IllegalArgumentException("OpenAPI document root must be an object")
        val version = (root["openapi"] as? JsonPrimitive)?.contentOrNull
            ?: throw IllegalArgumentException("OpenAPI document must declare an openapi version")
        require(OPENAPI_VERSION.matches(version)) { "Only OpenAPI 3.0 and 3.1 documents are supported" }
        require(root["info"] is JsonObject) { "OpenAPI document must contain an info object" }
        require(root["paths"] == null || root["paths"] is JsonObject) {
            "OpenAPI paths must be an object"
        }
        val normalized = normalizeDocument(root, limits)
        return json.decodeFromJsonElement(normalized)
    }

    fun extractEndpoints(spec: SwaggerSpec): List<EndpointInfo> = buildList {
        for ((path, methods) in spec.paths) {
            for ((method, operation) in methods) {
                if (method.lowercase() !in httpMethods) continue
                val resolvedParams = operation.parameters.map { param ->
                    if (param.schema != null) {
                        param.copy(schema = resolveSchemaRef(param.schema, spec))
                    } else param
                }

                val resolvedRequestBody = operation.requestBody?.let { body ->
                    body.copy(content = body.content.mapValues { (_, mediaType) ->
                        mediaType.schema?.let { schema ->
                            mediaType.copy(schema = resolveSchemaRef(schema, spec))
                        } ?: mediaType
                    })
                }

                val successResponse = operation.responses["200"]
                    ?: operation.responses["201"]
                    ?: operation.responses.entries.firstOrNull {
                        it.key.matches(Regex("2(?:[0-9]{2}|XX)", RegexOption.IGNORE_CASE))
                    }?.value
                val responseSchema = successResponse?.content?.values?.firstOrNull()?.schema
                    ?.let { resolveSchemaRef(it, spec) }

                add(
                    EndpointInfo(
                        path = path,
                        method = method.uppercase(),
                        parameters = resolvedParams,
                        requestBody = resolvedRequestBody,
                        expectedSchema = responseSchema
                    )
                )
            }
        }
    }

    /** Resolves local component schema refs with explicit cycle and depth guards. */
    fun resolveSchemaRef(
        schema: SwaggerSchema,
        spec: SwaggerSpec,
        maxDepth: Int = 48
    ): SwaggerSchema = resolveSchemaRef(
        schema,
        spec,
        0,
        maxDepth.coerceIn(1, 128),
        emptySet(),
        ResolutionBudget(MAX_RESOLVED_SCHEMA_NODES)
    )

    private fun resolveSchemaRef(
        schema: SwaggerSchema,
        spec: SwaggerSpec,
        depth: Int,
        maxDepth: Int,
        visitedRefs: Set<String>,
        budget: ResolutionBudget
    ): SwaggerSchema {
        if (depth >= maxDepth || budget.remaining-- <= 0) return schema

        if (schema.ref.isNotEmpty()) {
            if (!schema.ref.startsWith(COMPONENT_SCHEMA_PREFIX) || schema.ref in visitedRefs) return schema
            val refName = schema.ref.removePrefix(COMPONENT_SCHEMA_PREFIX)
                .replace("~1", "/")
                .replace("~0", "~")
            val resolved = spec.components?.schemas?.get(refName) ?: return schema
            return resolveSchemaRef(resolved, spec, depth + 1, maxDepth, visitedRefs + schema.ref, budget)
        }

        return schema.copy(
            properties = schema.properties.mapValues { (_, property) ->
                resolveSchemaRef(property, spec, depth + 1, maxDepth, visitedRefs, budget)
            },
            items = schema.items?.let {
                resolveSchemaRef(it, spec, depth + 1, maxDepth, visitedRefs, budget)
            }
        )
    }

    private fun parseYaml(input: String, limits: SwaggerParseLimits): JsonElement {
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = limits.maxAliases.coerceIn(0, 100)
            nestingDepthLimit = limits.maxNestingDepth
            codePointLimit = limits.maxInputBytes
        }
        val value = Yaml(SafeConstructor(options)).load<Any?>(input)
        return yamlValueToJson(value, 0, limits.maxNestingDepth, IdentityHashMap())
    }

    private fun yamlValueToJson(
        value: Any?,
        depth: Int,
        maxDepth: Int,
        activeContainers: IdentityHashMap<Any, Boolean>
    ): JsonElement {
        require(depth <= maxDepth) { "OpenAPI YAML exceeds the nesting limit" }
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Byte -> JsonPrimitive(value)
            is Short -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is Float -> JsonPrimitive(value)
            is Double -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value.toString())
            is Map<*, *> -> withCycleGuard(value, activeContainers) {
                buildJsonObject {
                    value.forEach { (key, child) ->
                        require(key is String) { "OpenAPI YAML object keys must be strings" }
                        put(key, yamlValueToJson(child, depth + 1, maxDepth, activeContainers))
                    }
                }
            }
            is Iterable<*> -> withCycleGuard(value, activeContainers) {
                buildJsonArray {
                    value.forEach { child ->
                        add(yamlValueToJson(child, depth + 1, maxDepth, activeContainers))
                    }
                }
            }
            else -> JsonPrimitive(value.toString())
        }
    }

    private inline fun <T> withCycleGuard(
        container: Any,
        activeContainers: IdentityHashMap<Any, Boolean>,
        block: () -> T
    ): T {
        require(activeContainers.put(container, true) == null) { "Cyclic YAML aliases are not supported" }
        return try {
            block()
        } finally {
            activeContainers.remove(container)
        }
    }

    private fun normalizeDocument(root: JsonObject, limits: SwaggerParseLimits): JsonObject {
        val rawPaths = root["paths"] as? JsonObject ?: JsonObject(emptyMap())
        var operationCount = 0
        val normalizedPaths = buildJsonObject {
            rawPaths.forEach { (path, rawPathItem) ->
                val pathItem = rawPathItem as? JsonObject ?: return@forEach
                val pathParameters = pathItem["parameters"] as? JsonArray ?: JsonArray(emptyList())
                val normalizedItem = buildJsonObject {
                    pathItem.forEach methodLoop@{ (method, rawOperation) ->
                        if (method.lowercase() !in httpMethods) return@methodLoop
                        val operation = rawOperation as? JsonObject ?: return@methodLoop
                        operationCount++
                        require(operationCount <= limits.maxOperations) {
                            "OpenAPI document exceeds the operation limit"
                        }
                        put(method.lowercase(), mergePathParameters(operation, pathParameters, root, limits))
                    }
                }
                put(path, normalizedItem)
            }
        }

        val schemas = ((root["components"] as? JsonObject)?.get("schemas") as? JsonObject)
        require((schemas?.size ?: 0) <= limits.maxSchemas) {
            "OpenAPI document exceeds the schema limit"
        }

        return JsonObject(root.toMutableMap().apply {
            put("paths", normalizeEnums(normalizedPaths))
            root["components"]?.let { put("components", normalizeEnums(it)) }
        })
    }

    private fun mergePathParameters(
        operation: JsonObject,
        pathParameters: JsonArray,
        root: JsonObject,
        limits: SwaggerParseLimits
    ): JsonObject {
        val operationParameters = operation["parameters"] as? JsonArray ?: JsonArray(emptyList())
        val merged = linkedMapOf<String, JsonElement>()
        (pathParameters + operationParameters).forEachIndexed { index, parameter ->
            val resolvedParameter = resolveComponentObject(parameter, root, limits, emptySet(), 0)
            val obj = resolvedParameter as? JsonObject
            val identity = if (obj == null) {
                "index:$index"
            } else {
                obj["\$ref"]?.let { "ref:${(it as? JsonPrimitive)?.contentOrNull}" }
                    ?: "${(obj["in"] as? JsonPrimitive)?.contentOrNull}:${(obj["name"] as? JsonPrimitive)?.contentOrNull}"
            }
            merged[identity] = resolvedParameter
        }
        val requestBody = operation["requestBody"]?.let {
            resolveComponentObject(it, root, limits, emptySet(), 0)
        }
        val responses = (operation["responses"] as? JsonObject)?.let { rawResponses ->
            JsonObject(rawResponses.mapValues { (_, response) ->
                resolveComponentObject(response, root, limits, emptySet(), 0)
            })
        }
        return normalizeEnums(
            JsonObject(operation.toMutableMap().apply {
                put("parameters", JsonArray(merged.values.toList()))
                if (requestBody != null) put("requestBody", requestBody)
                if (responses != null) put("responses", responses)
            })
        ) as JsonObject
    }

    private fun resolveComponentObject(
        element: JsonElement,
        root: JsonObject,
        limits: SwaggerParseLimits,
        visited: Set<String>,
        depth: Int
    ): JsonElement {
        val objectValue = element as? JsonObject ?: return element
        val ref = (objectValue["\$ref"] as? JsonPrimitive)?.contentOrNull ?: return element
        if (!ref.startsWith("#/components/") || ref in visited || depth >= limits.maxRefDepth.coerceIn(1, 128)) {
            return element
        }
        val segments = ref.removePrefix("#/").split('/').map {
            it.replace("~1", "/").replace("~0", "~")
        }
        var current: JsonElement = root
        for (segment in segments) {
            current = (current as? JsonObject)?.get(segment) ?: return element
        }
        return resolveComponentObject(current, root, limits, visited + ref, depth + 1)
    }

    /** String enums remain compatible with the existing SwaggerSchema model. */
    private fun normalizeEnums(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> {
            val normalized = element.mapValues { (key, value) ->
                if (key == "enum" && value is JsonArray) {
                    JsonArray(value.map { enumValue ->
                        when (enumValue) {
                            is JsonPrimitive -> JsonPrimitive(enumValue.content)
                            else -> JsonPrimitive(enumValue.toString())
                        }
                    })
                } else normalizeEnums(value)
            }.toMutableMap()

            val typeUnion = element["type"] as? JsonArray
            val typeNames = typeUnion?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val nonNullTypes = typeNames?.filterNot { it == "null" }.orEmpty()
            if (typeNames != null && "null" in typeNames && nonNullTypes.size == 1) {
                normalized["type"] = JsonPrimitive(nonNullTypes.single())
                normalized["nullable"] = JsonPrimitive(true)
            }
            JsonObject(normalized)
        }
        is JsonArray -> JsonArray(element.map(::normalizeEnums))
        else -> element
    }

    private fun enforceJsonDepth(input: String, maxDepth: Int) {
        var depth = 0
        var inString = false
        var escaped = false
        input.forEach { char ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{', '[' -> {
                        depth++
                        require(depth <= maxDepth) { "OpenAPI JSON exceeds the nesting limit" }
                    }
                    '}', ']' -> depth--
                }
            }
        }
    }

    private fun validateLimits(limits: SwaggerParseLimits) {
        require(limits.maxInputBytes in 1..MAX_ALLOWED_INPUT_BYTES) { "Invalid OpenAPI input limit" }
        require(limits.maxNestingDepth in 1..512) { "Invalid OpenAPI nesting limit" }
        require(limits.maxAliases in 0..100) { "Invalid OpenAPI alias limit" }
        require(limits.maxOperations in 1..100_000) { "Invalid OpenAPI operation limit" }
        require(limits.maxSchemas in 1..100_000) { "Invalid OpenAPI schema limit" }
        require(limits.maxRefDepth in 1..128) { "Invalid OpenAPI ref limit" }
    }

    private const val COMPONENT_SCHEMA_PREFIX = "#/components/schemas/"
    private const val MAX_ALLOWED_INPUT_BYTES = 100 * 1024 * 1024
    private const val MAX_RESOLVED_SCHEMA_NODES = 10_000
    private val OPENAPI_VERSION = Regex("3\\.[01]\\.[0-9]+(?:[-+].+)?")

    private data class ResolutionBudget(var remaining: Int)
}
