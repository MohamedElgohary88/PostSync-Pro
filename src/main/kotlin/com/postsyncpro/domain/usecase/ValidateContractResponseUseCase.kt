package com.postsyncpro.domain.usecase

import com.google.re2j.Pattern
import com.postsyncpro.domain.model.ContractViolation
import com.postsyncpro.domain.model.ContractViolationSeverity
import com.postsyncpro.domain.model.SwaggerMediaType
import com.postsyncpro.domain.model.SwaggerOperation
import com.postsyncpro.domain.model.SwaggerSchema
import com.postsyncpro.domain.model.SwaggerSpec
import com.postsyncpro.domain.model.WorkbenchResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

class ValidateContractResponseUseCase(
    private val maxViolations: Int = 200,
    private val maxSchemaDepth: Int = 64,
    private val maxJsonNestingDepth: Int = 128,
    private val maxValidatedNodes: Int = 10_000
) {
    private val json = Json { isLenient = false }

    init {
        require(maxViolations > 0)
        require(maxSchemaDepth in 1..256)
        require(maxJsonNestingDepth in 1..512)
        require(maxValidatedNodes > 0)
    }

    operator fun invoke(
        spec: SwaggerSpec,
        path: String,
        method: String,
        response: WorkbenchResponse
    ): List<ContractViolation> {
        val violations = mutableListOf<ContractViolation>()
        val operation = spec.paths[path]?.entries
            ?.firstOrNull { it.key.equals(method, ignoreCase = true) }?.value
        if (operation == null) {
            return listOf(ContractViolation("OPERATION_NOT_FOUND", "Operation $method $path is not in the contract"))
        }

        val documented = findResponse(operation, response.statusCode)
        if (documented == null) {
            return listOf(
                ContractViolation(
                    "STATUS_UNDOCUMENTED",
                    "Status ${response.statusCode} is not documented for $method $path"
                )
            )
        }

        if (documented.content.isEmpty()) return emptyList()
        val actualContentType = response.contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        val selectedMedia = selectMediaType(documented.content, actualContentType)
        if (selectedMedia == null) {
            violations += ContractViolation(
                "CONTENT_TYPE_MISMATCH",
                "Content-Type '${actualContentType ?: "missing"}' is not documented",
                severity = ContractViolationSeverity.ERROR
            )
            return violations
        }

        val schema = selectedMedia.value.schema ?: return violations
        if (response.truncated) {
            violations += ContractViolation(
                "RESPONSE_TRUNCATED",
                "Body validation is partial because the response exceeded the capture limit",
                severity = ContractViolationSeverity.WARNING
            )
            return violations
        }
        if (response.body.isBlank()) {
            if (response.statusCode != 204 && !schema.nullable) {
                violations += ContractViolation("BODY_MISSING", "A response body is required by the documented schema")
            }
            return violations
        }

        if (!isJsonMediaType(actualContentType ?: selectedMedia.key)) return violations
        if (!hasSafeJsonDepth(response.body, maxJsonNestingDepth)) {
            violations += ContractViolation("JSON_DEPTH_LIMIT", "Response JSON exceeds the validation nesting limit")
            return violations
        }
        val body = runCatching { json.parseToJsonElement(response.body) }.getOrElse {
            violations += ContractViolation("INVALID_JSON", "Response body is not valid JSON")
            return violations
        }

        validateSchema(
            value = body,
            schema = schema,
            spec = spec,
            path = "$",
            depth = 0,
            visitedRefs = emptySet(),
            violations = violations,
            budget = ValidationBudget()
        )
        return violations
    }

    private fun validateSchema(
        value: JsonElement,
        schema: SwaggerSchema,
        spec: SwaggerSpec,
        path: String,
        depth: Int,
        visitedRefs: Set<String>,
        violations: MutableList<ContractViolation>,
        budget: ValidationBudget
    ) {
        if (violations.size >= maxViolations) return
        budget.nodes++
        if (budget.nodes > maxValidatedNodes) {
            if (!budget.reported) {
                budget.reported = true
                add(violations, "VALIDATION_BUDGET", "Response validation reached its node limit", path,
                    ContractViolationSeverity.WARNING)
            }
            return
        }
        if (depth >= maxSchemaDepth) {
            add(violations, "SCHEMA_DEPTH_LIMIT", "Schema validation reached its depth limit", path,
                ContractViolationSeverity.WARNING)
            return
        }

        if (schema.ref.isNotEmpty()) {
            if (!schema.ref.startsWith(SCHEMA_PREFIX)) {
                add(violations, "EXTERNAL_REF_UNSUPPORTED", "Only local component refs are validated", path,
                    ContractViolationSeverity.WARNING)
                return
            }
            if (schema.ref in visitedRefs) {
                add(violations, "REF_CYCLE", "A circular schema ref was safely stopped", path,
                    ContractViolationSeverity.WARNING)
                return
            }
            val name = schema.ref.removePrefix(SCHEMA_PREFIX).replace("~1", "/").replace("~0", "~")
            val resolved = spec.components?.schemas?.get(name)
            if (resolved == null) {
                add(violations, "REF_NOT_FOUND", "Schema ref could not be resolved", path)
                return
            }
            validateSchema(value, resolved, spec, path, depth + 1, visitedRefs + schema.ref, violations, budget)
            return
        }

        if (value is JsonNull) {
            if (!schema.nullable) add(violations, "NULL_NOT_ALLOWED", "Value cannot be null", path)
            return
        }

        val expectedType = schema.type.ifBlank {
            when {
                schema.properties.isNotEmpty() -> "object"
                schema.items != null -> "array"
                else -> ""
            }
        }.lowercase()

        when (expectedType) {
            "object" -> validateObject(value, schema, spec, path, depth, visitedRefs, violations, budget)
            "array" -> validateArray(value, schema, spec, path, depth, visitedRefs, violations, budget)
            "string" -> validateString(value, schema, path, violations)
            "integer" -> validateNumber(value, schema, path, integer = true, violations)
            "number" -> validateNumber(value, schema, path, integer = false, violations)
            "boolean" -> {
                val primitive = value as? JsonPrimitive
                if (primitive?.booleanOrNull == null) {
                    add(violations, "TYPE_MISMATCH", "Expected boolean", path)
                } else if (schema.enumValues.isNotEmpty() && primitive.content !in schema.enumValues) {
                    add(violations, "ENUM_MISMATCH", "Value is not one of the documented enum values", path)
                }
            }
        }
    }

    private fun validateObject(
        value: JsonElement,
        schema: SwaggerSchema,
        spec: SwaggerSpec,
        path: String,
        depth: Int,
        visitedRefs: Set<String>,
        violations: MutableList<ContractViolation>,
        budget: ValidationBudget
    ) {
        val obj = value as? JsonObject
        if (obj == null) {
            add(violations, "TYPE_MISMATCH", "Expected object", path)
            return
        }
        schema.minProperties?.let {
            if (obj.size < it) add(violations, "MIN_PROPERTIES", "Expected at least $it properties", path)
        }
        schema.maxProperties?.let {
            if (obj.size > it) add(violations, "MAX_PROPERTIES", "Expected at most $it properties", path)
        }
        schema.required.forEach { required ->
            if (required !in obj) add(violations, "REQUIRED_MISSING", "Required property '$required' is missing", childPath(path, required))
        }
        schema.properties.forEach { (name, childSchema) ->
            obj[name]?.let { child ->
                validateSchema(child, childSchema, spec, childPath(path, name), depth + 1, emptySet(), violations, budget)
            }
        }
    }

    private fun validateArray(
        value: JsonElement,
        schema: SwaggerSchema,
        spec: SwaggerSpec,
        path: String,
        depth: Int,
        visitedRefs: Set<String>,
        violations: MutableList<ContractViolation>,
        budget: ValidationBudget
    ) {
        val array = value as? JsonArray
        if (array == null) {
            add(violations, "TYPE_MISMATCH", "Expected array", path)
            return
        }
        schema.minItems?.let {
            if (array.size < it) add(violations, "MIN_ITEMS", "Expected at least $it items", path)
        }
        schema.maxItems?.let {
            if (array.size > it) add(violations, "MAX_ITEMS", "Expected at most $it items", path)
        }
        if (schema.uniqueItems) {
            if (array.size <= maxValidatedNodes) {
                if (array.distinct().size != array.size) {
                    add(violations, "UNIQUE_ITEMS", "Array items must be unique", path)
                }
            } else {
                add(violations, "UNIQUE_ITEMS_SKIPPED", "Unique-items check exceeded the validation budget", path,
                    ContractViolationSeverity.WARNING)
            }
        }
        schema.items?.let { itemSchema ->
            array.forEachIndexed { index, child ->
                validateSchema(child, itemSchema, spec, "$path[$index]", depth + 1, emptySet(), violations, budget)
            }
        }
    }

    private fun validateString(
        value: JsonElement,
        schema: SwaggerSchema,
        path: String,
        violations: MutableList<ContractViolation>
    ) {
        val primitive = value as? JsonPrimitive
        if (primitive == null || !primitive.isString) {
            add(violations, "TYPE_MISMATCH", "Expected string", path)
            return
        }
        val text = primitive.content
        schema.minLength?.let {
            if (text.length < it) add(violations, "MIN_LENGTH", "String must contain at least $it characters", path)
        }
        schema.maxLength?.let {
            if (text.length > it) add(violations, "MAX_LENGTH", "String must contain at most $it characters", path)
        }
        if (schema.enumValues.isNotEmpty() && text !in schema.enumValues) {
            add(violations, "ENUM_MISMATCH", "Value is not one of the documented enum values", path)
        }
        if (schema.pattern.isNotEmpty()) {
            val matches = runCatching { Pattern.compile(schema.pattern).matcher(text).find() }.getOrNull()
            when (matches) {
                false -> add(violations, "PATTERN_MISMATCH", "String does not match the documented pattern", path)
                null -> add(violations, "PATTERN_UNSUPPORTED", "Pattern could not be evaluated safely", path,
                    ContractViolationSeverity.WARNING)
                true -> Unit
            }
        }
    }

    private fun validateNumber(
        value: JsonElement,
        schema: SwaggerSchema,
        path: String,
        integer: Boolean,
        violations: MutableList<ContractViolation>
    ) {
        val number = (value as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
        if (number == null || (integer && number % 1.0 != 0.0)) {
            add(violations, "TYPE_MISMATCH", if (integer) "Expected integer" else "Expected number", path)
            return
        }
        schema.minimum?.let {
            if (number < it) add(violations, "MINIMUM", "Number must be at least $it", path)
        }
        schema.maximum?.let {
            if (number > it) add(violations, "MAXIMUM", "Number must be at most $it", path)
        }
        if (schema.enumValues.isNotEmpty() && schema.enumValues.none { it.toDoubleOrNull() == number }) {
            add(violations, "ENUM_MISMATCH", "Value is not one of the documented enum values", path)
        }
    }

    private fun findResponse(operation: SwaggerOperation, status: Int) =
        operation.responses[status.toString()]
            ?: operation.responses.entries.firstOrNull {
                it.key.equals("${status / 100}XX", ignoreCase = true)
            }?.value
            ?: operation.responses.entries.firstOrNull { it.key.equals("default", ignoreCase = true) }?.value

    private fun selectMediaType(
        documented: Map<String, SwaggerMediaType>,
        actual: String?
    ): Map.Entry<String, SwaggerMediaType>? {
        if (actual == null) return null
        return documented.entries.firstOrNull { it.key.substringBefore(';').trim().equals(actual, true) }
            ?: documented.entries.firstOrNull { mediaMatches(it.key, actual) }
    }

    private fun mediaMatches(documented: String, actual: String): Boolean {
        val expected = documented.substringBefore(';').trim().lowercase()
        if (expected == "*/*") return true
        val expectedParts = expected.split('/', limit = 2)
        val actualParts = actual.split('/', limit = 2)
        if (expectedParts.size != 2 || actualParts.size != 2) return false
        if (expectedParts[0] != "*" && expectedParts[0] != actualParts[0]) return false
        return expectedParts[1] == "*" || expectedParts[1] == actualParts[1] ||
            (expectedParts[1].startsWith("*+") && actualParts[1].endsWith(expectedParts[1].removePrefix("*")))
    }

    private fun isJsonMediaType(contentType: String): Boolean {
        val subtype = contentType.substringBefore(';').substringAfter('/', "").lowercase()
        return subtype == "json" || subtype.endsWith("+json") || subtype == "*+json"
    }

    private fun add(
        target: MutableList<ContractViolation>,
        code: String,
        message: String,
        path: String,
        severity: ContractViolationSeverity = ContractViolationSeverity.ERROR
    ) {
        if (target.size < maxViolations) target += ContractViolation(code, message, path, severity)
    }

    private fun childPath(parent: String, name: String): String =
        if (SIMPLE_PROPERTY.matches(name)) "$parent.$name" else "$parent['${name.replace("'", "\\'")}']"

    private fun hasSafeJsonDepth(input: String, maxDepth: Int): Boolean {
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
                    '{', '[' -> if (++depth > maxDepth) return false
                    '}', ']' -> depth--
                }
            }
        }
        return true
    }

    private companion object {
        const val SCHEMA_PREFIX = "#/components/schemas/"
        val SIMPLE_PROPERTY = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }

    private class ValidationBudget(
        var nodes: Int = 0,
        var reported: Boolean = false
    )
}
