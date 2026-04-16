package com.postsyncpro.data.repository

import com.postsyncpro.domain.model.ChainNodeResult
import com.postsyncpro.domain.model.TestChainNode
import com.postsyncpro.domain.repository.TestChainRepository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class TestChainRepositoryImpl(
    private val httpClient: HttpClient
) : TestChainRepository {

    override suspend fun executeNode(
        node: TestChainNode,
        baseUrl: String,
        variables: Map<String, String>
    ): ChainNodeResult {
        val startTime = System.currentTimeMillis()

        return try {
            val resolvedPath = resolveVariables(node.path, variables)
            val resolvedBody = resolveVariables(node.bodyTemplate, variables)
            val resolvedHeaders = node.headers.mapValues { (_, value) ->
                resolveVariables(value, variables)
            }

            val fullUrl = baseUrl.trimEnd('/') + "/" + resolvedPath.trimStart('/')

            val response = httpClient.request(fullUrl) {
                method = httpMethodFromString(node.method)

                resolvedHeaders.forEach { (key, value) ->
                    header(key, value)
                }

                if (resolvedBody.isNotBlank() && node.method.uppercase() in listOf("POST", "PUT", "PATCH")) {
                    contentType(ContentType.Application.Json)
                    setBody(resolvedBody)
                }
            }

            val responseTimeMs = System.currentTimeMillis() - startTime
            val responseBody = response.bodyAsText()
            val httpStatus = response.status.value
            val passed = httpStatus in 200..299

            val extractedValues = extractValues(responseBody, node.extractors)

            ChainNodeResult(
                nodeId = node.id,
                httpStatus = httpStatus,
                responseBody = responseBody,
                responseTimeMs = responseTimeMs,
                passed = passed,
                extractedValues = extractedValues,
                error = null
            )
        } catch (e: Exception) {
            val responseTimeMs = System.currentTimeMillis() - startTime
            ChainNodeResult(
                nodeId = node.id,
                httpStatus = 0,
                responseBody = "",
                responseTimeMs = responseTimeMs,
                passed = false,
                extractedValues = emptyMap(),
                error = e.message ?: "Unknown error"
            )
        }
    }

    private fun resolveVariables(template: String, variables: Map<String, String>): String {
        var resolved = template
        variables.forEach { (key, value) ->
            resolved = resolved.replace("\${$key}", value)
        }
        return resolved
    }

    private fun httpMethodFromString(method: String): HttpMethod {
        return when (method.uppercase()) {
            "GET" -> HttpMethod.Get
            "POST" -> HttpMethod.Post
            "PUT" -> HttpMethod.Put
            "DELETE" -> HttpMethod.Delete
            "PATCH" -> HttpMethod.Patch
            "HEAD" -> HttpMethod.Head
            "OPTIONS" -> HttpMethod.Options
            else -> HttpMethod.parse(method.uppercase())
        }
    }

    private fun extractValues(
        responseBody: String,
        extractors: List<com.postsyncpro.domain.model.ResponseExtractor>
    ): Map<String, String> {
        if (extractors.isEmpty()) return emptyMap()

        val jsonElement = try {
            Json.parseToJsonElement(responseBody)
        } catch (e: Exception) {
            return emptyMap()
        }

        val result = mutableMapOf<String, String>()

        for (extractor in extractors) {
            val value = navigateJsonPath(jsonElement, extractor.jsonPath)
            if (value != null) {
                result[extractor.variableName] = value
            }
        }

        return result
    }

    private fun navigateJsonPath(root: JsonElement, jsonPath: String): String? {
        val segments = jsonPath.split(".")
        var current: JsonElement = root

        for (segment in segments) {
            current = when (current) {
                is JsonObject -> current[segment] ?: return null
                is JsonArray -> {
                    val index = segment.toIntOrNull() ?: return null
                    current.getOrNull(index) ?: return null
                }
                else -> return null
            }
        }

        return when (current) {
            is JsonPrimitive -> current.content
            else -> current.toString()
        }
    }
}
