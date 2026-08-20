package com.postsyncpro.data.repository

import com.postsyncpro.domain.model.CloudAiConfig
import com.postsyncpro.domain.model.CloudProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CloudAiAnalyzerSecurityTest {

    @Test
    fun `Gemini availability sends key only in header`() = runBlocking {
        var captured: HttpRequestData? = null
        val client = HttpClient(MockEngine { request ->
            captured = request
            respond("{}", HttpStatusCode.OK)
        })
        try {
            val analyzer = CloudAiAnalyzerImpl(
                CloudAiConfig(CloudProvider.GEMINI, apiKey = "top-secret-key"),
                client
            )

            assertEquals(true, analyzer.isAvailable())
            val request = requireNotNull(captured)
            assertEquals("top-secret-key", request.headers["x-goog-api-key"])
            assertFalse(request.url.toString().contains("top-secret-key"))
            assertFalse(request.url.parameters.contains("key"))
        } finally {
            client.close()
        }
    }

    @Test
    fun `Gemini generation sends key only in header`() = runBlocking {
        var captured: HttpRequestData? = null
        val aiJson = """{"rules":[],"suggestedEndpoints":[],"businessLogic":[]}"""
        val providerResponse = buildJsonObject {
            putJsonArray("candidates") {
                add(buildJsonObject {
                    putJsonObject("content") {
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", aiJson) })
                        }
                    }
                })
            }
        }.toString()
        val client = HttpClient(MockEngine { request ->
            captured = request
            respond(providerResponse, HttpStatusCode.OK)
        })
        try {
            val analyzer = CloudAiAnalyzerImpl(
                CloudAiConfig(CloudProvider.GEMINI, apiKey = "generation-secret"),
                client
            )

            val result = analyzer.extractRulesFromText("A simple requirement", "")
            assertEquals(0, result.rules.size)
            val request = requireNotNull(captured)
            assertEquals("generation-secret", request.headers["x-goog-api-key"])
            assertFalse(request.url.toString().contains("generation-secret"))
            assertFalse(request.url.parameters.contains("key"))
        } finally {
            client.close()
        }
    }
}
