package com.postsyncpro.domain.model

import kotlinx.serialization.*
import kotlinx.serialization.json.JsonElement

@Serializable
data class SwaggerSpec(
    val openapi: String = "3.0.0",
    val info: SwaggerInfo = SwaggerInfo(),
    val servers: List<SwaggerServer> = emptyList(),
    val paths: Map<String, Map<String, SwaggerOperation>> = emptyMap(),
    val components: SwaggerComponents? = null,
    val security: List<Map<String, List<String>>> = emptyList()
)

@Serializable
data class SwaggerInfo(
    val title: String = "",
    val version: String = "",
    val description: String = ""
)

@Serializable
data class SwaggerServer(
    val url: String = "",
    val description: String = "",
    val variables: Map<String, SwaggerServerVariable> = emptyMap()
)

@Serializable
data class SwaggerServerVariable(
    @SerialName("default") val defaultValue: String = "",
    @SerialName("enum") val enumValues: List<String> = emptyList(),
    val description: String = ""
)

@Serializable
data class SwaggerOperation(
    val summary: String = "",
    val description: String = "",
    val operationId: String = "",
    val tags: List<String> = emptyList(),
    val parameters: List<SwaggerParameter> = emptyList(),
    val requestBody: SwaggerRequestBody? = null,
    val responses: Map<String, SwaggerResponse> = emptyMap(),
    /** null means inherit root security; an explicit empty list means no auth. */
    val security: List<Map<String, List<String>>>? = null
)

@Serializable
data class SwaggerParameter(
    val name: String = "",
    @SerialName("in") val location: String = "", // "query", "path", "header", "cookie"
    val required: Boolean = false,
    val description: String = "",
    val schema: SwaggerSchema? = null,
    @SerialName("\$ref") val ref: String = "",
    val example: JsonElement? = null
)

@Serializable
data class SwaggerRequestBody(
    val description: String = "",
    val required: Boolean = false,
    val content: Map<String, SwaggerMediaType> = emptyMap(),
    @SerialName("\$ref") val ref: String = ""
)

@Serializable
data class SwaggerMediaType(
    val schema: SwaggerSchema? = null,
    val example: JsonElement? = null
)

@Serializable
data class SwaggerSchema(
    val type: String = "",
    val format: String = "",
    val properties: Map<String, SwaggerSchema> = emptyMap(),
    val required: List<String> = emptyList(),
    val items: SwaggerSchema? = null,
    val description: String = "",
    @SerialName("enum") val enumValues: List<String> = emptyList(),
    @SerialName("\$ref") val ref: String = "",
    val example: JsonElement? = null,
    val nullable: Boolean = false,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String = "",
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val minProperties: Int? = null,
    val maxProperties: Int? = null,
    val uniqueItems: Boolean = false,
    @SerialName("default") val defaultValue: JsonElement? = null
)

@Serializable
data class SwaggerResponse(
    val description: String = "",
    val content: Map<String, SwaggerMediaType> = emptyMap(),
    @SerialName("\$ref") val ref: String = ""
)

@Serializable
data class SwaggerComponents(
    val schemas: Map<String, SwaggerSchema> = emptyMap(),
    val securitySchemes: Map<String, SwaggerSecurityScheme> = emptyMap(),
    val parameters: Map<String, SwaggerParameter> = emptyMap(),
    val requestBodies: Map<String, SwaggerRequestBody> = emptyMap(),
    val responses: Map<String, SwaggerResponse> = emptyMap()
)

@Serializable
data class SwaggerSecurityScheme(
    val type: String = "",
    val scheme: String = "",
    val bearerFormat: String = "",
    @SerialName("in") val location: String = "",
    val name: String = ""
)
