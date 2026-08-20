package com.postsyncpro.data.repository

import com.postsyncpro.domain.model.EndpointInfo
import com.postsyncpro.domain.model.FuzzTestCase
import com.postsyncpro.domain.model.FuzzTestConfig
import com.postsyncpro.domain.model.MutationType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuzzTestRepositoryConcurrencyTest {

    @Test
    fun `configured workers execute concurrently without exceeding the bound`() = runBlocking {
        val inFlight = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val client = HttpClient(MockEngine {
            val current = inFlight.incrementAndGet()
            maximum.updateAndGet { previous -> maxOf(previous, current) }
            try {
                delay(75)
                respond("ok", HttpStatusCode.OK)
            } finally {
                inFlight.decrementAndGet()
            }
        })
        try {
            val repository = FuzzTestRepositoryImpl(client)
            val results = repository.executeTests(testCases(9), config(concurrency = 3)).toList()

            assertEquals(9, results.size)
            assertTrue(maximum.get() > 1, "requests should overlap")
            assertTrue(maximum.get() <= 3, "worker count must honor the configured bound")
        } finally {
            client.close()
        }
    }

    @Test
    fun `stored response is bounded and marked truncated`() = runBlocking {
        val client = HttpClient(MockEngine {
            respond(ByteReadChannel("x".repeat(10_000)), HttpStatusCode.OK)
        })
        try {
            val result = FuzzTestRepositoryImpl(client)
                .executeTests(testCases(1), config(concurrency = 1))
                .toList()
                .single()

            assertTrue(result.responseBody.length <= 2_000)
            assertTrue(result.responseBody.endsWith("[truncated]"))
        } finally {
            client.close()
        }
    }

    @Test
    fun `cancelTests cancels an active run instead of poisoning future runs`() = runBlocking {
        val requestStarted = CompletableDeferred<Unit>()
        val blockRequests = AtomicBoolean(true)
        val client = HttpClient(MockEngine {
            if (blockRequests.get()) {
                requestStarted.complete(Unit)
                delay(60_000)
            }
            respond("ok", HttpStatusCode.OK)
        })
        try {
            val repository = FuzzTestRepositoryImpl(client)
            val active = launch {
                repository.executeTests(testCases(2), config(concurrency = 2)).collect()
            }
            requestStarted.await()
            repository.cancelTests()
            active.join()
            assertTrue(active.isCancelled)

            blockRequests.set(false)
            val freshResults = repository
                .executeTests(testCases(1), config(concurrency = 1))
                .toList()
            assertEquals(1, freshResults.size)
        } finally {
            client.close()
        }
    }

    private fun config(concurrency: Int) = FuzzTestConfig(
        endpoint = EndpointInfo("/test", "GET"),
        mutations = setOf(MutationType.HAPPY_PATH),
        baseUrl = "https://example.test",
        concurrency = concurrency
    )

    private fun testCases(count: Int): List<FuzzTestCase> = (1..count).map { index ->
        FuzzTestCase(
            id = index.toString(),
            mutationType = MutationType.HAPPY_PATH,
            endpointPath = "/test/$index",
            method = "GET",
            payload = "",
            description = "test $index"
        )
    }
}
