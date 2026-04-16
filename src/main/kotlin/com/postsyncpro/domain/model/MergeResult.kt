package com.postsyncpro.domain.model

data class MergeResult(
    val mergedCollection: PostmanCollection,
    val logs: List<MergeLog>,
    val stats: MergeStats
)

data class MergeLog(
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LogLevel {
    INFO, WARNING, SUCCESS, ERROR
}

data class MergeStats(
    val totalEndpointsInOld: Int,
    val totalEndpointsInNew: Int,
    val preservedBodies: Int,
    val preservedScripts: Int,
    val preservedAuth: Int,
    val newEndpointsAdded: Int,
    val removedEndpoints: Int,
    val updatedEndpoints: Int
)
