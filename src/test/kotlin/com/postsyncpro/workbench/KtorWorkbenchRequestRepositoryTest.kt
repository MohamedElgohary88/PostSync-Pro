package com.postsyncpro.workbench

import com.postsyncpro.data.repository.KtorWorkbenchRequestRepository
import com.postsyncpro.domain.model.ResolvedWorkbenchRequest
import com.postsyncpro.domain.model.WorkbenchAuth
import com.postsyncpro.domain.model.WorkbenchExecutionResult
import com.postsyncpro.domain.model.WorkbenchFailureKind
import com.postsyncpro.domain.model.WorkbenchRequestDraft
import com.postsyncpro.domain.model.WorkbenchRequestField
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KtorWorkbenchRequestRepositoryTest {
    @Test
    fun `executes method query headers body and bearer auth`(): Unit = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("POST", request.method.value)
            assertEquals("search", request.url.parameters["q"])
            assertEquals("trace-1", request.headers["X-Trace"])
            assertEquals("Bearer secret-token", request.headers[HttpHeaders.Authorization])
            respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repository = KtorWorkbenchRequestRepository(HttpClient(engine))
        val result = repository.execute(
            ResolvedWorkbenchRequest(
                WorkbenchRequestDraft(
                    method = com.postsyncpro.domain.model.WorkbenchHttpMethod.POST,
                    url = "https://example.test/items",
                    query = listOf(WorkbenchRequestField("q", "search")),
                    headers = listOf(WorkbenchRequestField("X-Trace", "trace-1")),
                    body = "{}",
                    bodyEnabled = true,
                    auth = WorkbenchAuth.Bearer("secret-token")
                ),
                sensitiveValues = setOf("secret-token")
            )
        )

        val response = assertIs<WorkbenchExecutionResult.Success>(result, result.toString()).response
        assertEquals(201, response.statusCode)
        assertEquals("application/json", response.contentType)
        assertFalse(response.truncated)
        assertTrue(response.durationMillis >= 0)
    }

    @Test
    fun `bounds response body and reports declared size and truncation`(): Unit = runBlocking {
        val engine = MockEngine {
            respond(
                content = "0123456789ABCDEFGHIJ",
                headers = headersOf(HttpHeaders.ContentLength, "20")
            )
        }
        val result = KtorWorkbenchRequestRepository(HttpClient(engine), maxResponseBytes = 8)
            .execute(ResolvedWorkbenchRequest(WorkbenchRequestDraft(url = "https://example.test")))

        val response = assertIs<WorkbenchExecutionResult.Success>(result, result.toString()).response
        assertEquals("01234567", response.body)
        assertEquals(8, response.receivedBytes)
        assertEquals(20, response.sizeBytes)
        assertTrue(response.truncated)
    }

    @Test
    fun `redacts sensitive response headers and json properties`(): Unit = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"access_token":"server-secret","echo":"url-secret","name":"safe"}""",
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.SetCookie to listOf("session=server-secret")
                )
            )
        }
        val result = KtorWorkbenchRequestRepository(HttpClient(engine)).execute(
                ResolvedWorkbenchRequest(WorkbenchRequestDraft(url = "https://example.test?token=url-secret"))
            )
        val success = assertIs<WorkbenchExecutionResult.Success>(result, result.toString())
        val response = success.response

        assertFalse("server-secret" in response.body)
        assertFalse("url-secret" in response.body)
        assertTrue("safe" in response.body)
        assertEquals("<redacted>", response.headers.first { it.key == HttpHeaders.SetCookie }.value)
        assertTrue("server-secret" in success.contractResponse.body)
        assertTrue("url-secret" in success.contractResponse.body)
        assertEquals("session=server-secret", success.contractResponse.headers
            .first { it.key == HttpHeaders.SetCookie }.value)
    }

    @Test
    fun `redacts body-only credentials echoed under neutral response keys`(): Unit = runBlocking {
        val jsonSecret = "body-json-secret-734"
        val formSecret = "body-form-secret-734"
        val xmlSecret = "body-xml-secret-734"
        val opaqueSecret = "body-opaque-secret-734"
        val response = """{"json":"$jsonSecret","form":"$formSecret","xml":"$xmlSecret","opaque":"$opaqueSecret"}"""
        val engine = MockEngine { respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json")) }
        val repository = KtorWorkbenchRequestRepository(HttpClient(engine))

        val drafts = listOf(
            WorkbenchRequestDraft(
                url = "https://example.test",
                body = """{"password":"$jsonSecret"}""",
                bodyEnabled = true
            ),
            WorkbenchRequestDraft(
                url = "https://example.test",
                body = "access_token=$formSecret&safe=value",
                bodyEnabled = true
            ),
            WorkbenchRequestDraft(
                url = "https://example.test",
                body = "<root><client_secret>$xmlSecret</client_secret></root>",
                bodyEnabled = true
            ),
            WorkbenchRequestDraft(
                url = "https://example.test",
                body = opaqueSecret,
                bodyEnabled = true,
                bodySensitive = true
            )
        )

        drafts.forEach { draft ->
            val success = assertIs<WorkbenchExecutionResult.Success>(
                repository.execute(ResolvedWorkbenchRequest(draft))
            )
            val expectedSecret = when {
                draft.body.contains(jsonSecret) -> jsonSecret
                draft.body.contains(formSecret) -> formSecret
                draft.body.contains(xmlSecret) -> xmlSecret
                else -> opaqueSecret
            }
            assertFalse(expectedSecret in success.response.body)
            assertTrue(expectedSecret in success.contractResponse.body)
        }
    }

    @Test
    fun `returns structured timeout and invalid URL failures`(): Unit = runBlocking {
        val slowEngine = MockEngine {
            delay(1_000)
            respond("ok")
        }
        val timeout = KtorWorkbenchRequestRepository(HttpClient(slowEngine)).execute(
            ResolvedWorkbenchRequest(
                WorkbenchRequestDraft(url = "https://example.test", timeoutMillis = 100)
            )
        )
        assertEquals(
            WorkbenchFailureKind.TIMEOUT,
            assertIs<WorkbenchExecutionResult.Failure>(timeout).error.kind
        )

        val invalid = KtorWorkbenchRequestRepository(HttpClient(MockEngine { respond("ok") })).execute(
            ResolvedWorkbenchRequest(WorkbenchRequestDraft(url = "file:///etc/passwd"))
        )
        assertEquals(
            WorkbenchFailureKind.INVALID_REQUEST,
            assertIs<WorkbenchExecutionResult.Failure>(invalid).error.kind
        )
    }

    @Test
    fun `caller cancellation is propagated rather than converted to network failure`(): Unit = runBlocking {
        val engine = MockEngine {
            delay(10_000)
            respond("ok")
        }
        val repository = KtorWorkbenchRequestRepository(HttpClient(engine))
        val job = async {
            repository.execute(
                ResolvedWorkbenchRequest(
                    WorkbenchRequestDraft(url = "https://example.test", timeoutMillis = 60_000)
                )
            )
        }
        job.cancel(CancellationException("test cancellation"))
        assertFailsWith<CancellationException> { job.await() }
    }
}
