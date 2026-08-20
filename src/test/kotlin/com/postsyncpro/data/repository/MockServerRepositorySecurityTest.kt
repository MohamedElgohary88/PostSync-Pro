package com.postsyncpro.data.repository

import com.postsyncpro.domain.model.MockRoute
import com.postsyncpro.domain.model.MockServerConfig
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MockServerRepositorySecurityTest {

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    @Test
    fun `start waits for loopback bind and honors validation and logging switches`() = runBlocking {
        val repository = MockServerRepositoryImpl()
        val port = freePort()
        val route = MockRoute(
            path = "/users",
            method = "POST",
            requiredFields = listOf("name")
        )
        try {
            repository.startServer(
                MockServerConfig(
                    port = port,
                    enableValidation = false,
                    enableLogging = false
                ),
                listOf(route)
            )

            val response = post(port, "/users", "{}")
            assertEquals(200, response.statusCode())
            assertTrue(repository.isRunning())
            assertTrue(repository.requestLogs.value.isEmpty())
        } finally {
            repository.stopServer()
        }
        assertFalse(repository.isRunning())
    }

    @Test
    fun `validation rejects missing fields without leaking internals`() = runBlocking {
        val repository = MockServerRepositoryImpl()
        val port = freePort()
        try {
            repository.startServer(
                MockServerConfig(port = port, enableValidation = true),
                listOf(MockRoute("/users", "POST", requiredFields = listOf("name")))
            )

            val response = post(port, "/users", "{}")
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Missing required fields"))
        } finally {
            repository.stopServer()
        }
    }

    @Test
    fun `body and log caps apply and secrets are redacted`() = runBlocking {
        val repository = MockServerRepositoryImpl()
        val port = freePort()
        try {
            repository.startServer(
                MockServerConfig(
                    port = port,
                    enableValidation = false,
                    enableLogging = true,
                    maxRequestBodyBytes = 128,
                    maxLogEntries = 2
                ),
                listOf(MockRoute("/items", "POST"))
            )

            assertEquals(200, post(port, "/items", "{\"first\":true}").statusCode())
            assertEquals(
                200,
                post(port, "/items?token=url-secret", "{\"authorization\":\"Bearer header-secret\"}").statusCode()
            )
            val last = post(port, "/items", "{\"api_key\":\"body-secret\"}", origin = "https://evil.example")
            assertEquals(200, last.statusCode())
            assertFalse(last.headers().firstValue("Access-Control-Allow-Origin").isPresent)

            val oversized = post(port, "/items", "x".repeat(129))
            assertEquals(413, oversized.statusCode())

            val logs = repository.requestLogs.value
            assertEquals(2, logs.size)
            val serialized = logs.joinToString("\n") { "${it.path}\n${it.requestBody}" }
            assertTrue(serialized.contains("[REDACTED]"))
            assertFalse(serialized.contains("url-secret"))
            assertFalse(serialized.contains("header-secret"))
            assertFalse(serialized.contains("body-secret"))
        } finally {
            repository.stopServer()
        }
    }

    private fun post(port: Int, path: String, body: String, origin: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        origin?.let { builder.header("Origin", it) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }
}
