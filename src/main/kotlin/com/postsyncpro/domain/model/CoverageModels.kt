package com.postsyncpro.domain.model

/**
 * Module 8: SRS Coverage Heatmap models
 */
data class CoverageMatrix(
    val requirements: List<SrsRequirement>,
    val endpoints: List<String>,
    val cells: List<CoverageCell>,
    val overallCoverage: Float
)

data class SrsRequirement(
    val id: String,
    val section: String,
    val description: String,
    val fields: List<String>
)

data class CoverageCell(
    val requirementId: String,
    val endpoint: String,
    val status: CoverageStatus,
    val score: Float,
    val details: String = ""
)

enum class CoverageStatus {
    FULLY_COVERED,      // Green — all fields matched
    PARTIALLY_COVERED,  // Yellow — some fields matched
    NOT_COVERED,        // Red — no match
    NOT_APPLICABLE      // Gray — unrelated
}
