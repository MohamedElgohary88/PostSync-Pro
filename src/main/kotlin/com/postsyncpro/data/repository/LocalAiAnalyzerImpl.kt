package com.postsyncpro.data.repository

import com.postsyncpro.domain.model.LocalAiConfig
import com.postsyncpro.domain.model.SrsExtractionResult
import com.postsyncpro.domain.model.SrsRule
import com.postsyncpro.domain.repository.AiAnalyzerRepository
import java.io.File

class LocalAiAnalyzerImpl(
    private val localConfig: LocalAiConfig
) : AiAnalyzerRepository {

    // TODO: Integrate llama.cpp JNI bindings for local model inference
    // TODO: Load GGUF model from localConfig.modelPath
    // TODO: Configure context size from localConfig.contextSize
    // TODO: Set thread count from localConfig.threads

    override suspend fun extractRulesFromText(text: String, targetEndpoint: String): SrsExtractionResult {
        if (!isModelAvailable()) {
            return fallbackRegexExtraction(text)
        }

        // TODO: Replace with actual llama.cpp inference call
        // val prompt = buildPrompt(text, targetEndpoint)
        // val response = llamaInference(prompt)
        // return parseResponse(response)

        return fallbackRegexExtraction(text)
    }

    override suspend fun isAvailable(): Boolean {
        return isModelAvailable()
    }

    override fun getProviderName(): String = "Local AI (Offline)"

    private fun isModelAvailable(): Boolean {
        return try {
            val modelFile = File(localConfig.modelPath)
            modelFile.exists() && modelFile.length() > 0
        } catch (e: Exception) {
            false
        }
    }

    private fun fallbackRegexExtraction(text: String): SrsExtractionResult {
        val rules = mutableListOf<SrsRule>()
        val suggestedEndpoints = mutableListOf<String>()
        val businessLogic = mutableListOf<String>()

        // Pattern: "fieldName (type, required/optional)" or "fieldName: type (required)"
        val fieldPattern = Regex(
            """[-*]\s*(\w[\w_]*)\s*[:(]\s*(string|integer|int|number|float|double|boolean|bool|date|email|uuid|array|object|text|varchar|char)\b[^)]*\b(required|optional|mandatory)?\b""",
            RegexOption.IGNORE_CASE
        )
        for (match in fieldPattern.findAll(text)) {
            val fieldName = match.groupValues[1]
            val rawType = match.groupValues[2]
            val requiredFlag = match.groupValues[3]

            rules.add(
                SrsRule(
                    fieldName = fieldName,
                    dataType = normalizeType(rawType),
                    isRequired = requiredFlag.lowercase() in listOf("required", "mandatory"),
                    constraints = emptyList(),
                    description = "Extracted via regex fallback"
                )
            )
        }

        // Pattern: "field_name - description" style common in requirement docs
        val dashFieldPattern = Regex(
            """[-*]\s*(\w[\w_]*)\s*[-\u2013\u2014]\s*(.+?)(?:\((required|optional|mandatory)\))?$""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )
        for (match in dashFieldPattern.findAll(text)) {
            val fieldName = match.groupValues[1]
            val description = match.groupValues[2].trim()
            val requiredFlag = match.groupValues[3]

            if (rules.none { it.fieldName.equals(fieldName, ignoreCase = true) }) {
                rules.add(
                    SrsRule(
                        fieldName = fieldName,
                        dataType = guessTypeFromDescription(description),
                        isRequired = requiredFlag.lowercase() in listOf("required", "mandatory"),
                        constraints = emptyList(),
                        description = description
                    )
                )
            }
        }

        // Detect endpoints mentioned in the text
        val endpointPattern = Regex(
            """(GET|POST|PUT|PATCH|DELETE)\s+(/[\w/{}\-_.]+)""",
            RegexOption.IGNORE_CASE
        )
        for (match in endpointPattern.findAll(text)) {
            suggestedEndpoints.add("${match.groupValues[1].uppercase()} ${match.groupValues[2]}")
        }

        // Detect business rules (sentences with "must", "shall", "should")
        val sentencePattern = Regex("""[^.!?\n]*\b(must|shall|should|required to)\b[^.!?\n]*[.!?]""", RegexOption.IGNORE_CASE)
        for (match in sentencePattern.findAll(text)) {
            businessLogic.add(match.value.trim())
        }

        return SrsExtractionResult(
            rules = rules,
            suggestedEndpoints = suggestedEndpoints.distinct(),
            businessLogic = businessLogic.distinct(),
            rawAiResponse = "Extracted using regex-based fallback. For better results, configure a local GGUF model or use Cloud AI."
        )
    }

    private fun normalizeType(rawType: String): String {
        return when (rawType.lowercase()) {
            "int", "integer" -> "integer"
            "float", "double", "number" -> "number"
            "bool", "boolean" -> "boolean"
            "string", "text", "varchar", "char" -> "string"
            "date" -> "date"
            "email" -> "email"
            "uuid" -> "uuid"
            "array" -> "array"
            "object" -> "object"
            else -> "string"
        }
    }

    private fun guessTypeFromDescription(description: String): String {
        val lower = description.lowercase()
        return when {
            "email" in lower -> "email"
            "date" in lower || "time" in lower || "timestamp" in lower -> "date"
            "number" in lower || "count" in lower || "amount" in lower || "price" in lower || "age" in lower -> "number"
            "true" in lower || "false" in lower || "flag" in lower || "enabled" in lower -> "boolean"
            "id" in lower || "uuid" in lower || "identifier" in lower -> "uuid"
            "list" in lower || "array" in lower || "items" in lower -> "array"
            else -> "string"
        }
    }
}
