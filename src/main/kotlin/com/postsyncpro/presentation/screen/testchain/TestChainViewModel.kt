package com.postsyncpro.presentation.screen.testchain

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.TestChainRepository
import com.postsyncpro.domain.usecase.MergeCollectionsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

data class TestChainState(
    val endpoints: List<EndpointInfo> = emptyList(),
    val swaggerFileName: String = "",
    val baseUrl: String = "",
    val nodes: List<TestChainNode> = emptyList(),
    val connections: List<NodeConnection> = emptyList(),
    val selectedNodeId: String? = null,
    val executionState: ChainExecutionState = ChainExecutionState(),
    val draggedNodeId: String? = null,
    val canvasOffsetX: Float = 0f,
    val canvasOffsetY: Float = 0f,
    val zoom: Float = 1f,
    val isConnecting: Boolean = false,
    val connectFromNodeId: String? = null,
    val error: String? = null
)

class TestChainViewModel(
    private val chainRepository: TestChainRepository,
    private val mergeUseCase: MergeCollectionsUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(TestChainState())
    val state: StateFlow<TestChainState> = _state.asStateFlow()

    fun loadSwaggerFile(file: File) {
        scope.launch {
            try {
                val (endpoints, baseUrl) = withContext(Dispatchers.IO) {
                    val json = file.readText()
                    val spec = mergeUseCase.parseSwaggerSpec(json)
                    val eps = mutableListOf<EndpointInfo>()
                    spec.paths.forEach { (path, methods) ->
                        methods.forEach { (method, op) ->
                            eps.add(EndpointInfo(
                                path = path, method = method.uppercase(),
                                parameters = op.parameters, requestBody = op.requestBody,
                                expectedSchema = op.requestBody?.content?.values?.firstOrNull()?.schema
                            ))
                        }
                    }
                    val baseUrl = spec.servers.firstOrNull()?.url ?: ""
                    eps to baseUrl
                }
                _state.value = _state.value.copy(
                    endpoints = endpoints, baseUrl = baseUrl,
                    swaggerFileName = file.name, error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Parse error: ${e.message}")
            }
        }
    }

    fun updateBaseUrl(url: String) { _state.value = _state.value.copy(baseUrl = url) }

    fun addNode(endpoint: EndpointInfo) {
        val existingNodes = _state.value.nodes
        val x = 100f + existingNodes.size * 280f
        val y = 200f

        // Auto-generate body template from request schema
        val bodyTemplate = endpoint.requestBody?.content?.get("application/json")?.schema?.let { schema ->
            buildBodyTemplate(schema)
        } ?: ""

        // Auto-generate extractors for response fields
        val extractors = mutableListOf<ResponseExtractor>()
        endpoint.expectedSchema?.properties?.forEach { (name, _) ->
            if (name.contains("token") || name.contains("id") || name == "access_token") {
                extractors.add(ResponseExtractor(
                    variableName = "${endpoint.path.split("/").last()}_$name",
                    jsonPath = name
                ))
            }
        }

        val node = TestChainNode(
            id = UUID.randomUUID().toString().take(8),
            endpoint = "${endpoint.method} ${endpoint.path}",
            method = endpoint.method,
            path = endpoint.path,
            label = "${endpoint.method} ${endpoint.path.split("/").takeLast(2).joinToString("/")}",
            posX = x, posY = y,
            bodyTemplate = bodyTemplate,
            extractors = extractors
        )

        _state.value = _state.value.copy(
            nodes = existingNodes + node,
            selectedNodeId = node.id
        )
    }

    fun removeNode(nodeId: String) {
        _state.value = _state.value.copy(
            nodes = _state.value.nodes.filter { it.id != nodeId },
            connections = _state.value.connections.filter { it.fromNodeId != nodeId && it.toNodeId != nodeId },
            selectedNodeId = if (_state.value.selectedNodeId == nodeId) null else _state.value.selectedNodeId
        )
    }

    fun selectNode(nodeId: String?) {
        _state.value = _state.value.copy(selectedNodeId = nodeId)
    }

    fun moveNode(nodeId: String, deltaX: Float, deltaY: Float) {
        val nodes = _state.value.nodes.map { node ->
            if (node.id == nodeId) node.copy(posX = node.posX + deltaX, posY = node.posY + deltaY)
            else node
        }
        _state.value = _state.value.copy(nodes = nodes)
    }

    fun startConnecting(fromNodeId: String) {
        _state.value = _state.value.copy(isConnecting = true, connectFromNodeId = fromNodeId)
    }

    fun finishConnecting(toNodeId: String) {
        val fromId = _state.value.connectFromNodeId
        if (fromId != null && fromId != toNodeId) {
            val existing = _state.value.connections.any { it.fromNodeId == fromId && it.toNodeId == toNodeId }
            if (!existing) {
                val connection = NodeConnection(fromNodeId = fromId, toNodeId = toNodeId)
                _state.value = _state.value.copy(
                    connections = _state.value.connections + connection,
                    isConnecting = false, connectFromNodeId = null
                )
            }
        }
        _state.value = _state.value.copy(isConnecting = false, connectFromNodeId = null)
    }

    fun cancelConnecting() {
        _state.value = _state.value.copy(isConnecting = false, connectFromNodeId = null)
    }

    fun removeConnection(fromId: String, toId: String) {
        _state.value = _state.value.copy(
            connections = _state.value.connections.filter { !(it.fromNodeId == fromId && it.toNodeId == toId) }
        )
    }

    fun updateNodeBody(nodeId: String, body: String) {
        _state.value = _state.value.copy(
            nodes = _state.value.nodes.map { if (it.id == nodeId) it.copy(bodyTemplate = body) else it }
        )
    }

    fun updateNodeHeaders(nodeId: String, key: String, value: String) {
        _state.value = _state.value.copy(
            nodes = _state.value.nodes.map {
                if (it.id == nodeId) it.copy(headers = it.headers + (key to value)) else it
            }
        )
    }

    fun addExtractor(nodeId: String, variableName: String, jsonPath: String) {
        _state.value = _state.value.copy(
            nodes = _state.value.nodes.map {
                if (it.id == nodeId) it.copy(
                    extractors = it.extractors + ResponseExtractor(variableName, jsonPath)
                ) else it
            }
        )
    }

    fun executeChain() {
        val nodes = getExecutionOrder()
        if (nodes.isEmpty()) {
            _state.value = _state.value.copy(error = "Add at least one node to the chain")
            return
        }

        scope.launch {
            _state.value = _state.value.copy(
                executionState = ChainExecutionState(isRunning = true),
                error = null
            )

            val variables = mutableMapOf<String, String>()
            val nodeResults = mutableMapOf<String, ChainNodeResult>()

            for ((index, node) in nodes.withIndex()) {
                _state.value = _state.value.copy(
                    executionState = _state.value.executionState.copy(currentNodeIndex = index)
                )

                try {
                    val result = withContext(Dispatchers.IO) {
                        chainRepository.executeNode(node, _state.value.baseUrl, variables)
                    }

                    nodeResults[node.id] = result
                    variables.putAll(result.extractedValues)

                    _state.value = _state.value.copy(
                        executionState = _state.value.executionState.copy(
                            nodeResults = nodeResults.toMap(),
                            variables = variables.toMap()
                        )
                    )

                    if (!result.passed) {
                        _state.value = _state.value.copy(
                            executionState = _state.value.executionState.copy(
                                isRunning = false,
                                error = "Node '${node.label}' failed: ${result.error}"
                            )
                        )
                        return@launch
                    }
                } catch (e: Exception) {
                    nodeResults[node.id] = ChainNodeResult(
                        nodeId = node.id, httpStatus = 0, responseBody = "",
                        responseTimeMs = 0, passed = false, error = e.message
                    )
                    _state.value = _state.value.copy(
                        executionState = ChainExecutionState(
                            nodeResults = nodeResults.toMap(),
                            variables = variables.toMap(),
                            isRunning = false,
                            error = "Error: ${e.message}"
                        )
                    )
                    return@launch
                }
            }

            _state.value = _state.value.copy(
                executionState = _state.value.executionState.copy(isRunning = false)
            )
        }
    }

    private fun getExecutionOrder(): List<TestChainNode> {
        // Topological sort based on connections
        val nodeMap = _state.value.nodes.associateBy { it.id }
        val inDegree = mutableMapOf<String, Int>()
        val adjacency = mutableMapOf<String, MutableList<String>>()

        for (node in _state.value.nodes) {
            inDegree[node.id] = 0
            adjacency[node.id] = mutableListOf()
        }

        for (conn in _state.value.connections) {
            adjacency[conn.fromNodeId]?.add(conn.toNodeId)
            inDegree[conn.toNodeId] = (inDegree[conn.toNodeId] ?: 0) + 1
        }

        val queue = ArrayDeque<String>()
        for ((id, degree) in inDegree) {
            if (degree == 0) queue.add(id)
        }

        val order = mutableListOf<TestChainNode>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            nodeMap[current]?.let { order.add(it) }
            for (next in adjacency[current] ?: emptyList()) {
                inDegree[next] = (inDegree[next] ?: 0) - 1
                if (inDegree[next] == 0) queue.add(next)
            }
        }

        // If no connections, use original node order
        return if (order.size == _state.value.nodes.size) order else _state.value.nodes
    }

    fun clearChain() {
        _state.value = _state.value.copy(
            nodes = emptyList(),
            connections = emptyList(),
            selectedNodeId = null,
            executionState = ChainExecutionState()
        )
    }

    private fun buildBodyTemplate(schema: SwaggerSchema): String {
        val sb = StringBuilder("{\n")
        val props = schema.properties.entries.toList()
        for ((i, entry) in props.withIndex()) {
            val (name, propSchema) = entry
            val value = when (propSchema.type) {
                "string" -> "\"${name}_value\""
                "integer", "number" -> "0"
                "boolean" -> "false"
                else -> "\"${name}_value\""
            }
            sb.append("  \"$name\": $value")
            if (i < props.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("}")
        return sb.toString()
    }

    fun onCleared() { scope.cancel() }
}
