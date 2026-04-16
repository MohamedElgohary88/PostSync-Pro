package com.postsyncpro.data.repository

import com.postsyncpro.data.parser.PostmanCollectionParser
import com.postsyncpro.data.parser.SwaggerParser
import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.PostmanSyncRepository
import java.util.UUID

class PostmanSyncRepositoryImpl : PostmanSyncRepository {

    override suspend fun parsePostmanCollection(jsonContent: String): PostmanCollection {
        return PostmanCollectionParser.parse(jsonContent)
    }

    override suspend fun parseSwaggerSpec(jsonContent: String): SwaggerSpec {
        return SwaggerParser.parse(jsonContent)
    }

    override suspend fun mergeCollections(oldCollection: PostmanCollection, newSource: Any): MergeResult {
        val logs = mutableListOf<MergeLog>()
        val newCollection = when (newSource) {
            is PostmanCollection -> {
                logs.add(MergeLog(LogLevel.INFO, "Merging with Postman Collection: ${newSource.info.name}"))
                newSource
            }
            is SwaggerSpec -> {
                logs.add(MergeLog(LogLevel.INFO, "Merging with Swagger/OpenAPI spec: ${newSource.info.title}"))
                swaggerToPostmanCollection(newSource)
            }
            else -> throw IllegalArgumentException("Unsupported source type: ${newSource::class.simpleName}")
        }

        val oldItemsFlat = flattenItems(oldCollection.item)
        val oldItemMap = oldItemsFlat.associateBy { getEndpointKey(it) }

        val newItemsFlat = flattenItems(newCollection.item)
        val newItemKeys = newItemsFlat.map { getEndpointKey(it) }.toSet()

        var preservedBodies = 0
        var preservedScripts = 0
        var preservedAuth = 0
        var newEndpointsAdded = 0
        var updatedEndpoints = 0

        val mergedItems = newItemsFlat.map { newItem ->
            val key = getEndpointKey(newItem)
            val oldItem = oldItemMap[key]

            if (oldItem != null) {
                updatedEndpoints++
                logs.add(MergeLog(LogLevel.SUCCESS, "Merged endpoint: $key"))

                val oldRequest = oldItem.request
                val newRequest = newItem.request

                val mergedRequest = if (oldRequest != null && newRequest != null) {
                    var merged = newRequest.copy(
                        url = newRequest.url,
                        description = newRequest.description.ifEmpty { oldRequest.description }
                    )

                    if (oldRequest.body != null) {
                        merged = merged.copy(body = oldRequest.body)
                        preservedBodies++
                        logs.add(MergeLog(LogLevel.INFO, "Preserved request body for: $key"))
                    }

                    if (oldRequest.auth != null) {
                        merged = merged.copy(auth = oldRequest.auth)
                        preservedAuth++
                        logs.add(MergeLog(LogLevel.INFO, "Preserved auth for: $key"))
                    }

                    if (oldRequest.header.isNotEmpty()) {
                        val newHeaderKeys = newRequest.header.map { it.key.lowercase() }.toSet()
                        val preservedHeaders = oldRequest.header.filter { it.key.lowercase() !in newHeaderKeys }
                        merged = merged.copy(header = newRequest.header + preservedHeaders)
                    }

                    merged
                } else {
                    newRequest ?: oldRequest
                }

                val mergedEvents = if (oldItem.event.isNotEmpty()) {
                    preservedScripts++
                    logs.add(MergeLog(LogLevel.INFO, "Preserved scripts for: $key"))
                    oldItem.event
                } else {
                    newItem.event
                }

                newItem.copy(
                    request = mergedRequest,
                    response = oldItem.response.ifEmpty { newItem.response },
                    event = mergedEvents
                )
            } else {
                newEndpointsAdded++
                logs.add(MergeLog(LogLevel.INFO, "Added new endpoint: $key"))
                newItem
            }
        }

        val removedKeys = oldItemMap.keys - newItemKeys
        val removedEndpoints = removedKeys.size
        removedKeys.forEach { key ->
            logs.add(MergeLog(LogLevel.WARNING, "Endpoint removed from new source: $key"))
        }

        val mergedCollection = oldCollection.copy(
            item = mergedItems,
            variable = (oldCollection.variable + newCollection.variable).distinctBy { it.key }
        )

        val stats = MergeStats(
            totalEndpointsInOld = oldItemsFlat.size,
            totalEndpointsInNew = newItemsFlat.size,
            preservedBodies = preservedBodies,
            preservedScripts = preservedScripts,
            preservedAuth = preservedAuth,
            newEndpointsAdded = newEndpointsAdded,
            removedEndpoints = removedEndpoints,
            updatedEndpoints = updatedEndpoints
        )

        logs.add(MergeLog(LogLevel.SUCCESS, "Merge completed: ${mergedItems.size} endpoints in result"))

        return MergeResult(
            mergedCollection = mergedCollection,
            logs = logs,
            stats = stats
        )
    }

    override suspend fun exportCollection(collection: PostmanCollection): String {
        return PostmanCollectionParser.serialize(collection)
    }

    private fun getEndpointKey(item: PostmanItem): String {
        val request = item.request ?: return "UNKNOWN ${item.name}"
        val method = request.method.uppercase()
        val path = request.url?.let { url ->
            if (url.path.isNotEmpty()) {
                "/" + url.path.joinToString("/")
            } else {
                url.raw
            }
        } ?: item.name
        return "$method $path"
    }

    private fun swaggerToPostmanCollection(spec: SwaggerSpec): PostmanCollection {
        val baseUrl = spec.servers.firstOrNull()?.url ?: "{{baseUrl}}"
        val items = mutableListOf<PostmanItem>()

        for ((path, methods) in spec.paths) {
            for ((method, operation) in methods) {
                val fullUrl = "$baseUrl$path"
                val urlParts = path.trimStart('/').split("/")

                val queryParams = operation.parameters
                    .filter { it.location == "query" }
                    .map { param ->
                        PostmanQueryParam(
                            key = param.name,
                            value = "",
                            description = param.description,
                            disabled = !param.required
                        )
                    }

                val pathVariables = operation.parameters
                    .filter { it.location == "path" }
                    .map { param ->
                        PostmanPathVariable(
                            key = param.name,
                            value = "",
                            description = param.description
                        )
                    }

                val headerParams = operation.parameters
                    .filter { it.location == "header" }
                    .map { param ->
                        PostmanHeader(
                            key = param.name,
                            value = "",
                            description = param.description,
                            disabled = !param.required
                        )
                    }

                val headers = headerParams.toMutableList()

                val body = operation.requestBody?.let { reqBody ->
                    val jsonContent = reqBody.content["application/json"]
                    if (jsonContent != null) {
                        headers.add(PostmanHeader(key = "Content-Type", value = "application/json"))
                        val schema = jsonContent.schema?.let { SwaggerParser.resolveSchemaRef(it, spec) }
                        val rawBody = schema?.let { generateSampleJson(it) } ?: "{}"
                        PostmanBody(
                            mode = "raw",
                            raw = rawBody,
                            options = PostmanBodyOptions(raw = PostmanRawLanguage(language = "json"))
                        )
                    } else {
                        null
                    }
                }

                val host = baseUrl.removePrefix("https://").removePrefix("http://").split("/").first()
                val hostParts = host.split(".")
                val basePath = baseUrl.removePrefix("https://").removePrefix("http://")
                    .split("/").drop(1)

                val postmanUrl = PostmanUrl(
                    raw = fullUrl,
                    protocol = if (baseUrl.startsWith("https")) "https" else "http",
                    host = hostParts,
                    path = basePath + urlParts,
                    query = queryParams,
                    variable = pathVariables
                )

                val itemName = operation.summary.ifEmpty {
                    operation.operationId.ifEmpty { "${method.uppercase()} $path" }
                }

                items.add(
                    PostmanItem(
                        name = itemName,
                        request = PostmanRequest(
                            method = method.uppercase(),
                            header = headers,
                            body = body,
                            url = postmanUrl,
                            description = operation.description
                        ),
                        description = operation.description
                    )
                )
            }
        }

        return PostmanCollection(
            info = CollectionInfo(
                name = spec.info.title.ifEmpty { "Imported API" },
                description = spec.info.description,
                postmanId = UUID.randomUUID().toString(),
                schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
            ),
            item = items
        )
    }

    private fun flattenItems(items: List<PostmanItem>): List<PostmanItem> {
        val result = mutableListOf<PostmanItem>()
        for (item in items) {
            if (item.request != null) {
                result.add(item)
            }
            if (item.item.isNotEmpty()) {
                result.addAll(flattenItems(item.item))
            }
        }
        return result
    }

    private fun generateSampleJson(schema: SwaggerSchema): String {
        return buildString {
            appendSchemaValue(schema)
        }
    }

    private fun StringBuilder.appendSchemaValue(schema: SwaggerSchema) {
        when (schema.type) {
            "object" -> {
                append("{")
                val props = schema.properties.entries.toList()
                props.forEachIndexed { index, (name, propSchema) ->
                    append("\"$name\": ")
                    appendSchemaValue(propSchema)
                    if (index < props.size - 1) append(", ")
                }
                append("}")
            }
            "array" -> {
                append("[")
                schema.items?.let { appendSchemaValue(it) }
                append("]")
            }
            "string" -> {
                val example = when (schema.format) {
                    "date-time" -> "\"2024-01-01T00:00:00Z\""
                    "date" -> "\"2024-01-01\""
                    "email" -> "\"user@example.com\""
                    "uuid" -> "\"00000000-0000-0000-0000-000000000000\""
                    "uri", "url" -> "\"https://example.com\""
                    else -> "\"string\""
                }
                append(example)
            }
            "integer", "number" -> append("0")
            "boolean" -> append("true")
            else -> append("null")
        }
    }
}
