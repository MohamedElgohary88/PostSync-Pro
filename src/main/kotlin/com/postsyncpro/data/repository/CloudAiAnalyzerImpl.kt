package com.postsyncpro.data.repository

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.AiAnalyzerRepository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class CloudAiAnalyzerImpl(
    private val cloudConfig: CloudAiConfig,
    private val httpClient: HttpClient
) : AiAnalyzerRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun extractRulesFromText(text: String, targetEndpoint: String): SrsExtractionResult {
        val prompt = buildExtractionPrompt(text, targetEndpoint)
        val aiResponseText = when (cloudConfig.provider) {
            CloudProvider.GEMINI -> callGeminiApi(prompt)
            CloudProvider.GROQ -> callGroqApi(prompt)
        }

        return parseAiResponse(aiResponseText)
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            when (cloudConfig.provider) {
                CloudProvider.GEMINI -> {
                    val response = httpClient.get(
                        "${cloudConfig.provider.baseUrl}/models?key=${cloudConfig.apiKey}"
                    )
                    response.status.isSuccess()
                }
                CloudProvider.GROQ -> {
                    val response = httpClient.get("${cloudConfig.provider.baseUrl}/models") {
                        header(HttpHeaders.Authorization, "Bearer ${cloudConfig.apiKey}")
                    }
                    response.status.isSuccess()
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun getProviderName(): String {
        val model = cloudConfig.model.ifEmpty { cloudConfig.provider.defaultModel }
        return "${cloudConfig.provider.displayName} ($model)"
    }

    private suspend fun callGeminiApi(prompt: String): String {
        val model = cloudConfig.model.ifEmpty { cloudConfig.provider.defaultModel }
        val url = "${cloudConfig.provider.baseUrl}/models/$model:generateContent?key=${cloudConfig.apiKey}"

        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", prompt)
                        }
                    }
                }
            }
        }

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Gemini API error: ${response.status.value} - ${response.bodyAsText()}")
        }

        val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
        return responseJson["candidates"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")
            ?.jsonObject?.get("parts")
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")
            ?.jsonPrimitive?.content
            ?: throw RuntimeException("Unexpected Gemini response format")
    }

    private suspend fun callGroqApi(prompt: String): String {
        val model = cloudConfig.model.ifEmpty { cloudConfig.provider.defaultModel }
        val url = "${cloudConfig.provider.baseUrl}/chat/completions"

        val requestBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", "You are a software requirements analyst. Extract structured field information from requirements documents. Always respond with valid JSON only.")
                }
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
            put("temperature", 0.1)
            put("max_tokens", 4096)
        }

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${cloudConfig.apiKey}")
            setBody(requestBody.toString())
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Groq API error: ${response.status.value} - ${response.bodyAsText()}")
        }

        val responseJson = json.parseToJsonElement(response.bodyAsText()).jsonObject
        return responseJson["choices"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")
            ?.jsonObject?.get("content")
            ?.jsonPrimitive?.content
            ?: throw RuntimeException("Unexpected Groq response format")
    }

    private fun buildExtractionPrompt(text: String, targetEndpoint: String): String {
        val endpointInstruction = if (targetEndpoint.isNotEmpty()) {
            // Parse method and path from targetEndpoint (e.g., "POST /auth/register")
            val parts = targetEndpoint.split(" ", limit = 2)
            val method = parts.getOrElse(0) { "" }
            val path = parts.getOrElse(1) { targetEndpoint }
            val pathKeywords = path.split("/").filter { it.isNotEmpty() && !it.startsWith("{") }

            """You are analyzing requirements for ONE SPECIFIC API endpoint: $targetEndpoint

CRITICAL RULES — follow these strictly:
1. ONLY extract fields that are REQUEST BODY fields, REQUEST PARAMETERS, or RESPONSE BODY fields for this exact endpoint.
2. Identify the SRS section that corresponds to "${pathKeywords.joinToString("/")}" functionality.
   - For "/auth/register": ONLY the User Registration section
   - For "/auth/login": ONLY the User Login/Authentication section
   - For "/products": ONLY the Product Management section
   - For "/orders": ONLY the Order Management section
   - For "/wishlist": ONLY the Wishlist section
3. Extract BOTH request fields (what the client sends) AND response fields (what the server returns).
   Mark each field with "direction": "request", "response", or "both".
4. Do NOT extract fields from unrelated sections. If you see "name" in Products and "username" in Registration, and you're analyzing /auth/register, extract "username" ONLY.
5. Maximum 20 fields per endpoint. If you have more, you're likely including fields from other sections."""
        } else {
            "Extract all data fields mentioned in this requirements document."
        }

        return """$endpointInstruction

From the requirements document below, extract the data fields with their types and constraints.
Use the field names exactly as they would appear in a JSON API (snake_case).

Return your response as JSON in exactly this format:
{
  "rules": [
    {
      "fieldName": "field_name",
      "dataType": "string|integer|number|boolean|array|object|date|email|uuid",
      "isRequired": true,
      "direction": "request|response|both",
      "constraints": ["min_length:1", "max_length:255", "pattern:regex_here"],
      "description": "Brief description of the field"
    }
  ],
  "suggestedEndpoints": ["POST /api/users", "GET /api/users/{id}"],
  "businessLogic": ["Users must verify email before accessing the system"]
}

Requirements document:
---
$text
---

Respond ONLY with the JSON object, no additional text."""
    }

    private fun parseAiResponse(responseText: String): SrsExtractionResult {
        val jsonText = extractJsonFromResponse(responseText)

        return try {
            val parsed = json.parseToJsonElement(jsonText).jsonObject

            val rules = parsed["rules"]?.jsonArray?.map { ruleElement ->
                val rule = ruleElement.jsonObject
                SrsRule(
                    fieldName = rule["fieldName"]?.jsonPrimitive?.content ?: "",
                    dataType = rule["dataType"]?.jsonPrimitive?.content ?: "string",
                    isRequired = rule["isRequired"]?.jsonPrimitive?.booleanOrNull ?: false,
                    constraints = rule["constraints"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                    description = rule["description"]?.jsonPrimitive?.content ?: "",
                    direction = rule["direction"]?.jsonPrimitive?.content ?: "both"
                )
            } ?: emptyList()

            val suggestedEndpoints = parsed["suggestedEndpoints"]?.jsonArray
                ?.map { it.jsonPrimitive.content } ?: emptyList()

            val businessLogic = parsed["businessLogic"]?.jsonArray
                ?.map { it.jsonPrimitive.content } ?: emptyList()

            SrsExtractionResult(
                rules = rules,
                suggestedEndpoints = suggestedEndpoints,
                businessLogic = businessLogic,
                rawAiResponse = responseText
            )
        } catch (e: Exception) {
            SrsExtractionResult(
                rules = emptyList(),
                suggestedEndpoints = emptyList(),
                businessLogic = emptyList(),
                rawAiResponse = "Failed to parse AI response: ${e.message}\n\nRaw response:\n$responseText"
            )
        }
    }

    private fun extractJsonFromResponse(text: String): String {
        val codeBlockRegex = Regex("```(?:json)?\\s*\\n?(\\{[\\s\\S]*?\\})\\s*\\n?```")
        val codeBlockMatch = codeBlockRegex.find(text)
        if (codeBlockMatch != null) {
            return codeBlockMatch.groupValues[1]
        }

        val jsonObjectRegex = Regex("(\\{[\\s\\S]*\\})")
        val jsonMatch = jsonObjectRegex.find(text)
        if (jsonMatch != null) {
            return jsonMatch.groupValues[1]
        }

        return text.trim()
    }
}
