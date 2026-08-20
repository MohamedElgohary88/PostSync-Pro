package com.postsyncpro.workbench

import com.postsyncpro.domain.model.VariableResolutionError
import com.postsyncpro.domain.model.VariableResolutionResult
import com.postsyncpro.domain.model.WorkbenchAuth
import com.postsyncpro.domain.model.WorkbenchHistoryEntry
import com.postsyncpro.domain.model.WorkbenchRequestDraft
import com.postsyncpro.domain.model.WorkbenchRequestField
import com.postsyncpro.domain.model.WorkbenchVariable
import com.postsyncpro.domain.usecase.ResolveWorkbenchVariablesUseCase
import com.postsyncpro.domain.usecase.SecretRedactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VariableAndRedactionTest {
    private val resolver = ResolveWorkbenchVariablesUseCase()

    @Test
    fun `resolves nested templates across every request section and tracks secrets`() {
        val draft = WorkbenchRequestDraft(
            url = "{{base}}/users/{{id}}",
            query = listOf(WorkbenchRequestField("tenant", "{{tenant}}")),
            headers = listOf(WorkbenchRequestField("X-Trace", "{{trace}}")),
            body = """{"token":"{{token}}"}""",
            bodyEnabled = true,
            auth = WorkbenchAuth.Bearer("{{token}}")
        )
        val result = resolver(
            draft,
            mapOf(
                "host" to WorkbenchVariable("api.example.test"),
                "base" to WorkbenchVariable("https://{{host}}"),
                "id" to WorkbenchVariable("42"),
                "tenant" to WorkbenchVariable("acme"),
                "trace" to WorkbenchVariable("trace-1"),
                "token" to WorkbenchVariable("super-secret", secret = true)
            )
        )

        val request = assertIs<VariableResolutionResult.Success>(result).request
        assertEquals("https://api.example.test/users/42", request.draft.url)
        assertEquals("acme", request.draft.query.single().value)
        assertEquals("trace-1", request.draft.headers.single().value)
        assertEquals("""{"token":"super-secret"}""", request.draft.body)
        assertTrue("super-secret" in request.sensitiveValues)
    }

    @Test
    fun `credential shaped variables are redacted even when metadata misses the secret flag`() {
        val value = "scratch-client-secret-734"
        val result = resolver(
            WorkbenchRequestDraft(
                url = "https://example.test",
                body = """{"echo":"{{client_secret}}"}""",
                bodyEnabled = true
            ),
            mapOf("client_secret" to WorkbenchVariable(value, secret = false))
        )

        val request = assertIs<VariableResolutionResult.Success>(result).request
        assertEquals("""{"echo":"$value"}""", request.draft.body)
        assertTrue(value in request.sensitiveValues)
    }

    @Test
    fun `returns all missing variables but ignores disabled fields`() {
        val result = resolver(
            WorkbenchRequestDraft(
                url = "https://{{host}}/{{path}}",
                query = listOf(WorkbenchRequestField("ignored", "{{notNeeded}}", enabled = false)),
                headers = listOf(WorkbenchRequestField("X-Key", "{{missingHeader}}"))
            ),
            emptyMap()
        )

        val errors = assertIs<VariableResolutionResult.Failure>(result).errors
        val missing = errors.filterIsInstance<VariableResolutionError.Missing>().map { it.variableName }.toSet()
        assertEquals(setOf("host", "path", "missingHeader"), missing)
        assertFalse("notNeeded" in missing)
    }

    @Test
    fun `detects variable cycles without recursion overflow`() {
        val result = resolver(
            WorkbenchRequestDraft(url = "https://example.test/{{a}}"),
            mapOf(
                "a" to WorkbenchVariable("{{b}}"),
                "b" to WorkbenchVariable("{{a}}")
            )
        )
        val cycle = assertIs<VariableResolutionResult.Failure>(result).errors
            .filterIsInstance<VariableResolutionError.Cycle>()
            .single()
        assertEquals(listOf("a", "b", "a"), cycle.variableChain)
    }

    @Test
    fun `history sanitization strips auth variables headers url and json secrets`() {
        val redactor = SecretRedactor()
        val raw = "top-secret-token"
        val safe = redactor.sanitizeHistoryEntry(
            WorkbenchHistoryEntry(
                id = "1",
                createdAtEpochMillis = 1,
                request = WorkbenchRequestDraft(
                    url = "https://example.test/users?access_token=$raw",
                    headers = listOf(
                        WorkbenchRequestField("Authorization", "Bearer $raw"),
                        WorkbenchRequestField("X-Normal", "value-$raw")
                    ),
                    body = """{"password":"$raw","name":"ok"}""",
                    bodyEnabled = true,
                    auth = WorkbenchAuth.Bearer(raw)
                ),
                variables = mapOf("token" to WorkbenchVariable(raw, secret = true))
            )
        )

        val serializedView = listOf(
            safe.request.url,
            safe.request.body,
            safe.request.headers.joinToString { it.value },
            safe.variables.values.joinToString { it.value },
            (safe.request.auth as WorkbenchAuth.Bearer).token
        ).joinToString()
        assertFalse(raw in serializedView)
        assertTrue("<redacted>" in serializedView)
        assertTrue("ok" in safe.request.body)
    }

    @Test
    fun `redacts a sensitive first x-www-form-urlencoded field`() {
        val redacted = SecretRedactor().redactBody(
            "access_token=form-secret&name=safe"
        )

        assertFalse("form-secret" in redacted)
        assertTrue(redacted.startsWith("access_token=<redacted>"))
        assertTrue("name=safe" in redacted)
    }

    @Test
    fun `explicit secrets are replaced once without recursively expanding the marker`() {
        val input = "a".repeat(200_000) + " xyz"
        val redacted = SecretRedactor().redactText(input, setOf("a", "e", "d"))

        assertFalse("aa" in redacted)
        assertTrue(redacted.length <= 2 * 1024 * 1024)
        assertFalse("<r<r" in redacted)
        assertTrue(redacted.endsWith(" xyz"))
    }

    @Test
    fun `collects credential values from json form xml and explicitly sensitive bodies`() {
        val redactor = SecretRedactor()

        val jsonSecrets = redactor.collectSecrets(
            WorkbenchRequestDraft(body = """{"password":"json-secret"}""", bodyEnabled = true)
        )
        val formSecrets = redactor.collectSecrets(
            WorkbenchRequestDraft(body = "access_token=form-secret&safe=value", bodyEnabled = true)
        )
        val xmlSecrets = redactor.collectSecrets(
            WorkbenchRequestDraft(body = "<root><client_secret>xml-secret</client_secret></root>", bodyEnabled = true)
        )
        val opaqueSecrets = redactor.collectSecrets(
            WorkbenchRequestDraft(
                body = "opaque-secret-payload",
                bodyEnabled = true,
                bodySensitive = true
            )
        )

        assertTrue("json-secret" in jsonSecrets)
        assertTrue("form-secret" in formSecrets)
        assertTrue("xml-secret" in xmlSecrets)
        assertTrue("opaque-secret-payload" in opaqueSecrets)
    }
}
