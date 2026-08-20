package com.postsyncpro.data.repository

import com.postsyncpro.domain.model.ApiKeyLocation
import com.postsyncpro.domain.model.ResolvedWorkbenchRequest
import com.postsyncpro.domain.model.WorkbenchAuth
import com.postsyncpro.domain.model.WorkbenchExecutionFailure
import com.postsyncpro.domain.model.WorkbenchExecutionResult
import com.postsyncpro.domain.model.WorkbenchFailureKind
import com.postsyncpro.domain.model.WorkbenchRequestField
import com.postsyncpro.domain.model.WorkbenchResponse
import com.postsyncpro.domain.repository.WorkbenchRequestRepository
import com.postsyncpro.domain.usecase.SecretRedactor
import io.ktor.client.HttpClient
import io.ktor.client.request.cookie
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import java.util.Base64

class KtorWorkbenchRequestRepository(
    private val client: HttpClient,
    private val redactor: SecretRedactor = SecretRedactor(),
    maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val nanoTime: () -> Long = System::nanoTime
) : WorkbenchRequestRepository {
    private val maxResponseBytes = maxResponseBytes.coerceIn(1, MAX_CONFIGURABLE_RESPONSE_BYTES)

    override suspend fun execute(request: ResolvedWorkbenchRequest): WorkbenchExecutionResult {
        val sensitiveValues = request.sensitiveValues + redactor.collectSecrets(request.draft)
        val validationError = validate(request)
        if (validationError != null) {
            return failure(WorkbenchFailureKind.INVALID_REQUEST, validationError, request)
        }

        val startedAt = currentTimeMillis()
        val startedNanos = nanoTime()
        var responseStarted = false

        return try {
            val completed = withTimeoutOrNull(request.draft.timeoutMillis) {
                val draft = request.draft
                val response = client.request(draft.url) {
                    method = HttpMethod.parse(draft.method.name)
                    url {
                        draft.query.asSequence()
                            .filter { it.enabled && it.key.isNotBlank() }
                            .forEach { parameters.append(it.key, it.value) }
                        val apiKey = draft.auth as? WorkbenchAuth.ApiKey
                        if (apiKey?.location == ApiKeyLocation.QUERY && apiKey.name.isNotBlank()) {
                            parameters.append(apiKey.name, apiKey.value)
                        }
                    }

                    draft.headers.asSequence()
                        .filter { it.enabled && it.key.isNotBlank() }
                        .forEach { header -> headers.append(header.key, header.value) }

                    when (val auth = draft.auth) {
                        WorkbenchAuth.None -> Unit
                        is WorkbenchAuth.Bearer -> {
                            headers.remove(HttpHeaders.Authorization)
                            headers.append(HttpHeaders.Authorization, "Bearer ${auth.token}")
                        }
                        is WorkbenchAuth.Basic -> {
                            val raw = "${auth.username}:${auth.password}".toByteArray(Charsets.UTF_8)
                            val encoded = Base64.getEncoder().encodeToString(raw)
                            headers.remove(HttpHeaders.Authorization)
                            headers.append(HttpHeaders.Authorization, "Basic $encoded")
                        }
                        is WorkbenchAuth.ApiKey -> when (auth.location) {
                            ApiKeyLocation.HEADER -> {
                                headers.remove(auth.name)
                                headers.append(auth.name, auth.value)
                            }
                            ApiKeyLocation.COOKIE -> cookie(auth.name, auth.value)
                            ApiKeyLocation.QUERY -> Unit
                        }
                    }

                    if (draft.bodyEnabled) setBody(draft.body)
                }
                responseStarted = true

                val channel = response.bodyAsChannel()
                val capturedWithSentinel = channel.readRemaining(maxResponseBytes.toLong() + 1L).readBytes()
                var truncated = capturedWithSentinel.size > maxResponseBytes
                if (truncated) channel.cancel(null)

                val completedAt = currentTimeMillis()
                val durationMillis = ((nanoTime() - startedNanos) / 1_000_000L).coerceAtLeast(0L)
                val bodyBytes = if (truncated) capturedWithSentinel.copyOf(maxResponseBytes) else capturedWithSentinel
                val declaredSize = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (declaredSize != null && declaredSize > bodyBytes.size) truncated = true

                val raw = WorkbenchResponse(
                    statusCode = response.status.value,
                    statusText = response.status.description,
                    headers = response.headers.entries().flatMap { (key, values) ->
                        values.map { value -> WorkbenchRequestField(key, value) }
                    },
                    body = bodyBytes.toString(Charsets.UTF_8),
                    contentType = response.headers[HttpHeaders.ContentType],
                    durationMillis = durationMillis,
                    sizeBytes = declaredSize ?: capturedWithSentinel.size.toLong(),
                    receivedBytes = bodyBytes.size.toLong(),
                    truncated = truncated,
                    startedAtEpochMillis = startedAt,
                    completedAtEpochMillis = completedAt
                )
                WorkbenchExecutionResult.Success(
                    response = redactor.sanitizeResponse(raw, sensitiveValues),
                    contractResponse = raw
                )
            }
            completed ?: failure(
                WorkbenchFailureKind.TIMEOUT,
                "Request timed out after ${request.draft.timeoutMillis} ms",
                request
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure(
                if (responseStarted) WorkbenchFailureKind.RESPONSE_READ else WorkbenchFailureKind.NETWORK,
                error.message ?: if (responseStarted) "Failed to read response" else "Network request failed",
                request
            )
        }
    }

    private fun validate(request: ResolvedWorkbenchRequest): String? {
        val draft = request.draft
        if (draft.timeoutMillis !in MIN_TIMEOUT_MILLIS..MAX_TIMEOUT_MILLIS) {
            return "Timeout must be between $MIN_TIMEOUT_MILLIS and $MAX_TIMEOUT_MILLIS ms"
        }

        val uri = runCatching { URI(draft.url) }.getOrNull()
            ?: return "URL is invalid"
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            return "Only absolute HTTP and HTTPS URLs are supported"
        }
        if (uri.userInfo != null) {
            return "Credentials in URLs are not supported; use the Auth settings"
        }

        val fields = draft.headers.filter { it.enabled } + when (val auth = draft.auth) {
            is WorkbenchAuth.ApiKey -> if (auth.location == ApiKeyLocation.HEADER) {
                listOf(WorkbenchRequestField(auth.name, auth.value, sensitive = true))
            } else emptyList()
            else -> emptyList()
        }
        fields.forEach { header ->
            if (!HEADER_NAME.matches(header.key)) return "Invalid HTTP header name"
            if (header.value.any { it == '\r' || it == '\n' || it == '\u0000' }) {
                return "HTTP header values cannot contain control characters"
            }
        }

        val apiKey = draft.auth as? WorkbenchAuth.ApiKey
        if (apiKey != null && apiKey.location == ApiKeyLocation.COOKIE && !COOKIE_NAME.matches(apiKey.name)) {
            return "Invalid cookie name"
        }
        return null
    }

    private fun failure(
        kind: WorkbenchFailureKind,
        message: String,
        request: ResolvedWorkbenchRequest
    ) = WorkbenchExecutionResult.Failure(
        WorkbenchExecutionFailure(
            kind,
            redactor.redactText(
                message,
                request.sensitiveValues + redactor.collectSecrets(request.draft)
            )
        )
    )

    private companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_CONFIGURABLE_RESPONSE_BYTES = 50 * 1024 * 1024
        const val MIN_TIMEOUT_MILLIS = 100L
        const val MAX_TIMEOUT_MILLIS = 10 * 60 * 1000L
        val HEADER_NAME = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
        val COOKIE_NAME = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
    }
}
