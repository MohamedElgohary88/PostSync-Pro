package com.postsyncpro.domain.model

/**
 * Module 11: Dependency Graph Visualizer models
 */
data class DependencyGraph(
    val nodes: List<DependencyNode>,
    val edges: List<DependencyEdge>,
    val clusters: List<EndpointCluster>
)

data class DependencyNode(
    val id: String,
    val method: String,
    val path: String,
    val label: String,         // Short display name
    val category: String,      // e.g., "auth", "products", "orders"
    val inputFields: List<String>,
    val outputFields: List<String>,
    val posX: Float = 0f,
    val posY: Float = 0f
)

data class DependencyEdge(
    val fromId: String,
    val toId: String,
    val field: String,         // The field that creates the dependency
    val type: DependencyType,
    val description: String
)

enum class DependencyType {
    AUTH_TOKEN,       // Endpoint requires auth from another endpoint
    PATH_PARAM,       // Uses ID from another endpoint's response
    BODY_REFERENCE,   // Request body references data from another endpoint
    IMPLICIT          // Logical dependency (e.g., must create before delete)
}

data class EndpointCluster(
    val name: String,          // e.g., "Authentication", "Products"
    val nodeIds: List<String>
)
