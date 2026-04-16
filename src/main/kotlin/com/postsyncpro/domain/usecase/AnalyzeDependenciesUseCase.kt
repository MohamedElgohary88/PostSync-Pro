package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.*

class AnalyzeDependenciesUseCase {

    fun analyze(spec: SwaggerSpec): DependencyGraph {
        val nodes = mutableListOf<DependencyNode>()
        val edges = mutableListOf<DependencyEdge>()

        // Build nodes from all endpoints
        for ((path, methods) in spec.paths) {
            for ((method, op) in methods) {
                val category = path.trimStart('/').split('/').firstOrNull() ?: "other"
                val inputFields = mutableListOf<String>()
                val outputFields = mutableListOf<String>()

                // Collect input fields
                op.parameters.forEach { inputFields.add(it.name) }
                op.requestBody?.content?.get("application/json")?.schema?.let { schema ->
                    val resolved = resolveRef(schema, spec)
                    resolved.properties.keys.forEach { inputFields.add(it) }
                }

                // Collect output fields from response
                val resp = op.responses["200"] ?: op.responses["201"]
                resp?.content?.get("application/json")?.schema?.let { schema ->
                    val resolved = resolveRef(schema, spec)
                    collectOutputFields(resolved, "", outputFields)
                }

                val nodeId = "${method.uppercase()}:$path"
                nodes.add(DependencyNode(
                    id = nodeId,
                    method = method.uppercase(),
                    path = path,
                    label = op.summary.ifEmpty { "$method $path" },
                    category = category,
                    inputFields = inputFields,
                    outputFields = outputFields
                ))
            }
        }

        // Detect auth dependencies
        for (node in nodes) {
            val op = spec.paths[node.path]?.get(node.method.lowercase())
            if (op?.security?.isNotEmpty() == true) {
                // Find auth endpoints (login, token)
                val authNodes = nodes.filter { n ->
                    n.category == "auth" && n.method == "POST" &&
                    (n.path.contains("login") || n.path.contains("token")) &&
                    n.outputFields.any { it.contains("token", ignoreCase = true) }
                }
                for (authNode in authNodes) {
                    if (authNode.id != node.id) {
                        edges.add(DependencyEdge(
                            fromId = authNode.id,
                            toId = node.id,
                            field = "Authorization",
                            type = DependencyType.AUTH_TOKEN,
                            description = "Requires auth token from ${authNode.path}"
                        ))
                    }
                }
            }
        }

        // Detect path parameter dependencies
        for (node in nodes) {
            val pathParams = Regex("\\{(\\w+)}").findAll(node.path).map { it.groupValues[1] }.toList()
            for (param in pathParams) {
                // Find nodes that produce this field
                val producers = nodes.filter { other ->
                    other.id != node.id &&
                    other.outputFields.any { field ->
                        field.equals(param, ignoreCase = true) ||
                        field.equals("id", ignoreCase = true) && node.path.contains(other.category)
                    }
                }
                for (producer in producers) {
                    if (!edges.any { it.fromId == producer.id && it.toId == node.id }) {
                        edges.add(DependencyEdge(
                            fromId = producer.id,
                            toId = node.id,
                            field = param,
                            type = DependencyType.PATH_PARAM,
                            description = "Uses {$param} from ${producer.path}"
                        ))
                    }
                }
            }
        }

        // Detect body reference dependencies (e.g., product_id in orders)
        for (node in nodes) {
            for (inputField in node.inputFields) {
                if (inputField.endsWith("_id") || inputField.endsWith("Id")) {
                    val referencedEntity = inputField.removeSuffix("_id").removeSuffix("Id")
                    val producers = nodes.filter { other ->
                        other.id != node.id &&
                        other.category.contains(referencedEntity, ignoreCase = true) &&
                        other.method == "POST"
                    }
                    for (producer in producers) {
                        if (!edges.any { it.fromId == producer.id && it.toId == node.id && it.field == inputField }) {
                            edges.add(DependencyEdge(
                                fromId = producer.id,
                                toId = node.id,
                                field = inputField,
                                type = DependencyType.BODY_REFERENCE,
                                description = "References '$inputField' created by ${producer.path}"
                            ))
                        }
                    }
                }
            }
        }

        // Detect implicit CRUD dependencies
        val pathGroups = nodes.groupBy { it.path.replace(Regex("\\{[^}]+}"), "*") }
        for ((_, group) in pathGroups) {
            val createNode = group.find { it.method == "POST" }
            val deleteNode = group.find { it.method == "DELETE" }
            val updateNode = group.find { it.method == "PUT" || it.method == "PATCH" }
            val getNode = group.find { it.method == "GET" }

            if (createNode != null) {
                listOfNotNull(deleteNode, updateNode, getNode).forEach { dependent ->
                    if (!edges.any { it.fromId == createNode.id && it.toId == dependent.id }) {
                        edges.add(DependencyEdge(
                            fromId = createNode.id,
                            toId = dependent.id,
                            field = "resource",
                            type = DependencyType.IMPLICIT,
                            description = "Resource must be created before ${dependent.method}"
                        ))
                    }
                }
            }
        }

        // Build clusters
        val clusters = nodes.groupBy { it.category }.map { (name, clusterNodes) ->
            EndpointCluster(name = name.replaceFirstChar { it.uppercase() }, nodeIds = clusterNodes.map { it.id })
        }

        // Auto-layout: position nodes in clusters
        val laidOutNodes = layoutNodes(nodes, clusters, edges)

        return DependencyGraph(nodes = laidOutNodes, edges = edges, clusters = clusters)
    }

    private fun layoutNodes(
        nodes: List<DependencyNode>,
        clusters: List<EndpointCluster>,
        edges: List<DependencyEdge>
    ): List<DependencyNode> {
        val positioned = mutableMapOf<String, Pair<Float, Float>>()
        var clusterX = 100f

        for (cluster in clusters) {
            var nodeY = 100f
            for (nodeId in cluster.nodeIds) {
                positioned[nodeId] = Pair(clusterX, nodeY)
                nodeY += 120f
            }
            clusterX += 300f
        }

        return nodes.map { node ->
            val (x, y) = positioned[node.id] ?: Pair(0f, 0f)
            node.copy(posX = x, posY = y)
        }
    }

    private fun collectOutputFields(schema: SwaggerSchema, prefix: String, fields: MutableList<String>) {
        for ((name, prop) in schema.properties) {
            val fullName = if (prefix.isEmpty()) name else "$prefix.$name"
            fields.add(fullName)
            if (prop.type == "object" && prop.properties.isNotEmpty()) {
                collectOutputFields(prop, fullName, fields)
            }
        }
    }

    private fun resolveRef(schema: SwaggerSchema, spec: SwaggerSpec): SwaggerSchema {
        if (schema.ref.isNotEmpty()) {
            val refName = schema.ref.substringAfterLast("/")
            return spec.components?.schemas?.get(refName) ?: schema
        }
        return schema
    }
}
