package com.postsyncpro.domain.model

import kotlinx.serialization.Serializable

/**
 * Module 7: API Test Chain Builder models
 */
@Serializable
data class TestChain(
    val id: String,
    val name: String,
    val nodes: List<TestChainNode>,
    val connections: List<NodeConnection>
)

@Serializable
data class TestChainNode(
    val id: String,
    val endpoint: String,         // e.g., "POST /auth/register"
    val method: String,
    val path: String,
    val label: String,            // User-facing name
    val posX: Float = 0f,        // Canvas position
    val posY: Float = 0f,
    val headers: Map<String, String> = emptyMap(),
    val bodyTemplate: String = "", // JSON body with ${variable} placeholders
    val extractors: List<ResponseExtractor> = emptyList()
)

@Serializable
data class ResponseExtractor(
    val variableName: String,     // e.g., "login_token"
    val jsonPath: String          // e.g., "access_token" or "data.user.id"
)

@Serializable
data class NodeConnection(
    val fromNodeId: String,
    val toNodeId: String,
    val mappings: List<VariableMapping> = emptyList()
)

@Serializable
data class VariableMapping(
    val sourceVariable: String,   // e.g., "${register.response.id}"
    val targetField: String       // e.g., "user_id" in request body, or "Authorization" in headers
)

data class ChainExecutionState(
    val currentNodeIndex: Int = -1,
    val nodeResults: Map<String, ChainNodeResult> = emptyMap(),
    val variables: Map<String, String> = emptyMap(),
    val isRunning: Boolean = false,
    val error: String? = null
)

data class ChainNodeResult(
    val nodeId: String,
    val httpStatus: Int,
    val responseBody: String,
    val responseTimeMs: Long,
    val passed: Boolean,
    val extractedValues: Map<String, String> = emptyMap(),
    val error: String? = null
)
