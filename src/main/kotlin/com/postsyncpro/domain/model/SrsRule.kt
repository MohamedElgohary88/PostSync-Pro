package com.postsyncpro.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SrsRule(
    val fieldName: String,
    val dataType: String,
    val isRequired: Boolean,
    val constraints: List<String> = emptyList(),
    val description: String = "",
    val relatedEndpoint: String = "",
    val direction: String = "both" // "request", "response", or "both"
)

@Serializable
data class SrsExtractionResult(
    val rules: List<SrsRule>,
    val suggestedEndpoints: List<String> = emptyList(),
    val businessLogic: List<String> = emptyList(),
    val rawAiResponse: String = ""
)
