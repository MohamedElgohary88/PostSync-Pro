package com.postsyncpro.workbench

import com.postsyncpro.domain.model.ApiKeyLocation
import com.postsyncpro.domain.model.SwaggerComponents
import com.postsyncpro.domain.model.SwaggerInfo
import com.postsyncpro.domain.model.SwaggerMediaType
import com.postsyncpro.domain.model.SwaggerOperation
import com.postsyncpro.domain.model.SwaggerParameter
import com.postsyncpro.domain.model.SwaggerRequestBody
import com.postsyncpro.domain.model.SwaggerResponse
import com.postsyncpro.domain.model.SwaggerSchema
import com.postsyncpro.domain.model.SwaggerSecurityScheme
import com.postsyncpro.domain.model.SwaggerServer
import com.postsyncpro.domain.model.SwaggerSpec
import com.postsyncpro.domain.model.WorkbenchAuth
import com.postsyncpro.domain.model.WorkbenchResponse
import com.postsyncpro.domain.usecase.PrepareContractRequestUseCase
import com.postsyncpro.domain.usecase.ValidateContractResponseUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContractWorkbenchTest {
    @Test
    fun `operation selection prepares url params body and inherited auth`() {
        val spec = contractSpec()
        val prepared = PrepareContractRequestUseCase()(spec, "/users/{id}", "post")

        assertEquals("https://api.example.test/users/{{id}}", prepared.draft.url)
        assertEquals("verbose", prepared.draft.query.single().key)
        assertTrue(prepared.draft.bodyEnabled)
        assertTrue("name" in prepared.draft.body)
        val auth = assertIs<WorkbenchAuth.ApiKey>(prepared.draft.auth)
        assertEquals(ApiKeyLocation.HEADER, auth.location)
        assertEquals("X-API-Key", auth.name)
        assertTrue(prepared.variables.getValue("apiKey").secret)
    }

    @Test
    fun `explicit empty operation security disables inherited auth`() {
        val base = contractSpec()
        val operation = base.paths.getValue("/users/{id}").getValue("post").copy(security = emptyList())
        val spec = base.copy(paths = mapOf("/users/{id}" to mapOf("post" to operation)))
        assertIs<WorkbenchAuth.None>(
            PrepareContractRequestUseCase()(spec, "/users/{id}", "post").draft.auth
        )
    }

    @Test
    fun `validates status content type required type enum and constraints`() {
        val spec = contractSpec()
        val validator = ValidateContractResponseUseCase()
        val invalid = response(
            status = 201,
            body = """{"id":"not-an-int","status":"unknown","nickname":"x"}"""
        )
        val violations = validator(spec, "/users/{id}", "post", invalid)
        val codes = violations.map { it.code }.toSet()
        assertTrue("TYPE_MISMATCH" in codes)
        assertTrue("ENUM_MISMATCH" in codes)
        assertTrue("MIN_LENGTH" in codes)
        assertTrue("REQUIRED_MISSING" in codes)

        val valid = response(
            status = 201,
            body = """{"id":7,"name":"Ada","status":"active","nickname":"coder"}"""
        )
        assertTrue(validator(spec, "/users/{id}", "post", valid).isEmpty())
    }

    @Test
    fun `matches status ranges and reports content mismatch and truncation safely`() {
        val spec = contractSpec()
        val validator = ValidateContractResponseUseCase()
        val wrongType = response(201, "{}", contentType = "text/plain")
        assertEquals("CONTENT_TYPE_MISMATCH", validator(spec, "/users/{id}", "post", wrongType).single().code)

        val truncated = response(201, "{", truncated = true)
        val violations = validator(spec, "/users/{id}", "post", truncated)
        assertEquals(listOf("RESPONSE_TRUNCATED"), violations.map { it.code })
    }

    private fun contractSpec(): SwaggerSpec {
        val responseSchema = SwaggerSchema(
            type = "object",
            required = listOf("id", "name", "status"),
            properties = mapOf(
                "id" to SwaggerSchema(type = "integer", minimum = 1.0),
                "name" to SwaggerSchema(type = "string", minLength = 2),
                "status" to SwaggerSchema(type = "string", enumValues = listOf("active", "disabled")),
                "nickname" to SwaggerSchema(type = "string", minLength = 3, maxLength = 20)
            )
        )
        val operation = SwaggerOperation(
            parameters = listOf(
                SwaggerParameter("id", "path", required = true, schema = SwaggerSchema(type = "integer")),
                SwaggerParameter("verbose", "query", schema = SwaggerSchema(type = "boolean"))
            ),
            requestBody = SwaggerRequestBody(
                required = true,
                content = mapOf(
                    "application/json" to SwaggerMediaType(
                        schema = SwaggerSchema(
                            type = "object",
                            properties = mapOf("name" to SwaggerSchema(type = "string")),
                            required = listOf("name")
                        )
                    )
                )
            ),
            responses = mapOf(
                "2XX" to SwaggerResponse(
                    content = mapOf("application/json" to SwaggerMediaType(responseSchema))
                )
            )
        )
        return SwaggerSpec(
            info = SwaggerInfo("Users", "1"),
            servers = listOf(SwaggerServer("https://api.example.test")),
            paths = mapOf("/users/{id}" to mapOf("post" to operation)),
            components = SwaggerComponents(
                securitySchemes = mapOf(
                    "apiKey" to SwaggerSecurityScheme(
                        type = "apiKey",
                        location = "header",
                        name = "X-API-Key"
                    )
                )
            ),
            security = listOf(mapOf("apiKey" to emptyList()))
        )
    }

    private fun response(
        status: Int,
        body: String,
        contentType: String = "application/json; charset=utf-8",
        truncated: Boolean = false
    ) = WorkbenchResponse(
        statusCode = status,
        headers = emptyList(),
        body = body,
        contentType = contentType,
        durationMillis = 10,
        sizeBytes = body.length.toLong(),
        receivedBytes = body.length.toLong(),
        truncated = truncated,
        startedAtEpochMillis = 1,
        completedAtEpochMillis = 11
    )
}
