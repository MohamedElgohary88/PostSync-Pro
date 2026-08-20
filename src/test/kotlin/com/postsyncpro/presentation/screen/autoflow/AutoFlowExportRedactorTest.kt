package com.postsyncpro.presentation.screen.autoflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoFlowExportRedactorTest {

    @Test
    fun `sensitive headers and extracted variables are removed`() {
        val redacted = AutoFlowExportRedactor.redactMap(
            mapOf(
                "Authorization" to "Bearer header-secret",
                "x-goog-api-key" to "api-secret",
                "access_token" to "token-secret",
                "Content-Type" to "application/json"
            )
        )

        assertEquals("[REDACTED]", redacted["Authorization"])
        assertEquals("[REDACTED]", redacted["x-goog-api-key"])
        assertEquals("[REDACTED]", redacted["access_token"])
        assertEquals("application/json", redacted["Content-Type"])
    }

    @Test
    fun `bodies and urls redact JSON form auth and user info secrets`() {
        val source = """
            {"password":"json-secret","note":"Bearer bearer-secret"}
            https://user:pass@example.test/path?api_key=query-secret&safe=yes
            client_secret=form-secret
        """.trimIndent()

        val redacted = AutoFlowExportRedactor.redactText(source)

        assertTrue(redacted.contains("[REDACTED]"))
        assertTrue(redacted.contains("safe=yes"))
        assertFalse(redacted.contains("json-secret"))
        assertFalse(redacted.contains("bearer-secret"))
        assertFalse(redacted.contains("user:pass"))
        assertFalse(redacted.contains("query-secret"))
        assertFalse(redacted.contains("form-secret"))
    }

    @Test
    fun `exported values are bounded`() {
        val redacted = AutoFlowExportRedactor.redactText("x".repeat(400_000))
        assertTrue(redacted.length < 300_000)
        assertTrue(redacted.endsWith("[truncated]"))
    }
}
