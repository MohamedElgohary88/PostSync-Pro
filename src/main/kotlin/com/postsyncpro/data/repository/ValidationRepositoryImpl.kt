package com.postsyncpro.data.repository

import com.postsyncpro.data.parser.SwaggerParser
import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.ValidationRepository

class ValidationRepositoryImpl : ValidationRepository {

    override suspend fun validateSrsAgainstSwagger(
        rules: List<SrsRule>,
        swaggerSpec: SwaggerSpec,
        endpointPath: String,
        method: String
    ): ValidationReport {
        val operation = swaggerSpec.paths[endpointPath]?.get(method.lowercase())
            ?: return ValidationReport(
                endpointPath = endpointPath,
                method = method,
                matches = emptyList(),
                missingInBackend = rules.map { rule ->
                    FieldMismatch(
                        fieldName = rule.fieldName,
                        source = MismatchSource.SRS_ONLY,
                        expectedType = rule.dataType,
                        isRequired = rule.isRequired,
                        description = "Endpoint $method $endpointPath not found in Swagger spec"
                    )
                },
                missingInSrs = emptyList(),
                typeConflicts = emptyList(),
                overallScore = 0f
            )

        val swaggerFields = extractSwaggerFields(operation, swaggerSpec)

        val matches = mutableListOf<FieldMatch>()
        val missingInBackend = mutableListOf<FieldMismatch>()
        val missingInSrs = mutableListOf<FieldMismatch>()
        val typeConflicts = mutableListOf<TypeConflict>()

        val matchedSwaggerFields = mutableSetOf<String>()

        // Deduplicate rules by normalized name (AI may extract duplicates)
        val seenRuleNames = mutableSetOf<String>()
        val dedupedRules = rules.filter { rule ->
            seenRuleNames.add(normalizeFieldName(rule.fieldName))
        }

        for (rule in dedupedRules) {
            val normalizedRuleName = normalizeFieldName(rule.fieldName)
            val swaggerField = swaggerFields.entries.find { (key, _) ->
                normalizeFieldName(key) == normalizedRuleName
            }

            if (swaggerField != null) {
                matchedSwaggerFields.add(swaggerField.key)
                val (swaggerType, swaggerRequired) = swaggerField.value

                if (typesAreCompatible(rule.dataType, swaggerType)) {
                    // Field name and type both match — this is a match
                    matches.add(
                        FieldMatch(
                            fieldName = rule.fieldName,
                            srsType = rule.dataType,
                            swaggerType = swaggerType,
                            isRequired = rule.isRequired || swaggerRequired
                        )
                    )
                    // Note: required-status difference is informational, not a conflict
                } else {
                    // Field name matches but type differs — real conflict
                    typeConflicts.add(
                        TypeConflict(
                            fieldName = rule.fieldName,
                            srsType = rule.dataType,
                            swaggerType = swaggerType,
                            srsRequired = rule.isRequired,
                            swaggerRequired = swaggerRequired,
                            description = "Type mismatch: SRS expects '${rule.dataType}', Swagger defines '$swaggerType'"
                        )
                    )
                }
            } else {
                missingInBackend.add(
                    FieldMismatch(
                        fieldName = rule.fieldName,
                        source = MismatchSource.SRS_ONLY,
                        expectedType = rule.dataType,
                        isRequired = rule.isRequired,
                        description = "Field '${rule.fieldName}' defined in SRS but not found in Swagger spec"
                    )
                )
            }
        }

        for ((fieldName, fieldInfo) in swaggerFields) {
            if (fieldName !in matchedSwaggerFields) {
                val (swaggerType, swaggerRequired) = fieldInfo
                missingInSrs.add(
                    FieldMismatch(
                        fieldName = fieldName,
                        source = MismatchSource.SWAGGER_ONLY,
                        expectedType = swaggerType,
                        isRequired = swaggerRequired,
                        description = "Field '$fieldName' exists in Swagger but not in SRS"
                    )
                )
            }
        }

        // Score = matched fields / total unique fields from both SRS and Swagger
        // matches + typeConflicts = fields found in both (by name)
        // missingInBackend = SRS-only fields
        // missingInSrs = Swagger-only fields
        // Conflicts count as partial matches (name matched, type didn't)
        val totalUniqueFields = matches.size + typeConflicts.size + missingInBackend.size + missingInSrs.size
        val matchScore = matches.size.toFloat() + (typeConflicts.size.toFloat() * 0.5f) // partial credit for name matches
        val overallScore = if (totalUniqueFields > 0) {
            (matchScore / totalUniqueFields).coerceIn(0f, 1f)
        } else {
            1f
        }

        return ValidationReport(
            endpointPath = endpointPath,
            method = method,
            matches = matches,
            missingInBackend = missingInBackend,
            missingInSrs = missingInSrs,
            typeConflicts = typeConflicts,
            overallScore = overallScore
        )
    }

    private fun extractSwaggerFields(
        operation: SwaggerOperation,
        spec: SwaggerSpec
    ): Map<String, Pair<String, Boolean>> {
        val fields = mutableMapOf<String, Pair<String, Boolean>>()

        // 1. Path/query/header parameters
        for (param in operation.parameters) {
            val type = param.schema?.type ?: "string"
            fields[param.name] = Pair(type, param.required)
        }

        // 2. Request body fields
        operation.requestBody?.let { reqBody ->
            val jsonContent = reqBody.content["application/json"]
            jsonContent?.schema?.let { schema ->
                val resolved = SwaggerParser.resolveSchemaRef(schema, spec)
                extractFieldsFromSchema(resolved, "", fields)
            }
        }

        // 3. Response body fields (check 200, 201, 2xx responses)
        val successResponse = operation.responses["201"]
            ?: operation.responses["200"]
            ?: operation.responses.entries.firstOrNull { it.key.startsWith("2") }?.value

        successResponse?.let { resp ->
            val jsonContent = resp.content["application/json"]
            jsonContent?.schema?.let { schema ->
                val resolved = SwaggerParser.resolveSchemaRef(schema, spec)
                extractFieldsFromSchema(resolved, "", fields)
            }
        }

        return fields
    }

    private fun extractFieldsFromSchema(
        schema: SwaggerSchema,
        prefix: String,
        fields: MutableMap<String, Pair<String, Boolean>>
    ) {
        val requiredFields = schema.required.toSet()

        for ((propName, propSchema) in schema.properties) {
            val fullName = if (prefix.isEmpty()) propName else "$prefix.$propName"
            val isRequired = propName in requiredFields

            if (propSchema.type == "object" && propSchema.properties.isNotEmpty()) {
                extractFieldsFromSchema(propSchema, fullName, fields)
            } else {
                val type = propSchema.type.ifEmpty { "string" }
                fields[fullName] = Pair(type, isRequired)
            }
        }
    }

    private fun normalizeFieldName(name: String): String {
        return name.lowercase()
            .replace("-", "")
            .replace("_", "")
            .replace(".", "")
            .trim()
    }

    private fun typesAreCompatible(srsType: String, swaggerType: String): Boolean {
        val normalizedSrs = normalizeTypeName(srsType)
        val normalizedSwagger = normalizeTypeName(swaggerType)
        return normalizedSrs == normalizedSwagger
    }

    private fun normalizeTypeName(type: String): String {
        return when (type.lowercase()) {
            "int", "integer", "int32", "int64", "long" -> "integer"
            "float", "double", "number", "decimal" -> "number"
            "bool", "boolean" -> "boolean"
            "string", "text", "varchar", "char", "email", "uuid", "uri", "url", "date", "date-time", "datetime" -> "string"
            "array", "list" -> "array"
            "object", "map" -> "object"
            else -> type.lowercase()
        }
    }
}
