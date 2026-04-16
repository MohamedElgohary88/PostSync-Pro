package com.postsyncpro.data.parser

import com.postsyncpro.domain.model.*
import kotlinx.serialization.json.Json

object SwaggerParser {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
    }

    fun parse(jsonString: String): SwaggerSpec {
        return json.decodeFromString<SwaggerSpec>(jsonString)
    }

    fun extractEndpoints(spec: SwaggerSpec): List<EndpointInfo> {
        val endpoints = mutableListOf<EndpointInfo>()

        for ((path, methods) in spec.paths) {
            for ((method, operation) in methods) {
                val resolvedParams = operation.parameters.map { param ->
                    if (param.schema?.ref?.isNotEmpty() == true) {
                        param.copy(schema = resolveSchemaRef(param.schema, spec))
                    } else {
                        param
                    }
                }

                val resolvedRequestBody = operation.requestBody?.let { body ->
                    val resolvedContent = body.content.mapValues { (_, mediaType) ->
                        mediaType.schema?.let { schema ->
                            SwaggerMediaType(schema = resolveSchemaRef(schema, spec))
                        } ?: mediaType
                    }
                    body.copy(content = resolvedContent)
                }

                val responseSchema = operation.responses["200"]?.content
                    ?.values?.firstOrNull()?.schema?.let { resolveSchemaRef(it, spec) }

                endpoints.add(
                    EndpointInfo(
                        path = path,
                        method = method.uppercase(),
                        parameters = resolvedParams,
                        requestBody = resolvedRequestBody,
                        expectedSchema = responseSchema
                    )
                )
            }
        }

        return endpoints
    }

    fun resolveSchemaRef(schema: SwaggerSchema, spec: SwaggerSpec): SwaggerSchema {
        if (schema.ref.isEmpty()) {
            val resolvedProperties = schema.properties.mapValues { (_, propSchema) ->
                resolveSchemaRef(propSchema, spec)
            }
            val resolvedItems = schema.items?.let { resolveSchemaRef(it, spec) }
            return schema.copy(properties = resolvedProperties, items = resolvedItems)
        }

        val refName = schema.ref.removePrefix("#/components/schemas/")
        val resolved = spec.components?.schemas?.get(refName) ?: return schema

        return resolveSchemaRef(resolved, spec)
    }
}
