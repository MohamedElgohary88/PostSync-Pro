package com.postsyncpro.domain.model

/**
 * Module 9: API Breaking Change Detector models
 */
data class SwaggerDiffResult(
    val oldVersion: String,
    val newVersion: String,
    val addedEndpoints: List<EndpointChange>,
    val removedEndpoints: List<EndpointChange>,
    val modifiedEndpoints: List<EndpointModification>,
    val breakingChangesCount: Int,
    val nonBreakingChangesCount: Int,
    val severity: DiffSeverity
)

data class EndpointChange(
    val method: String,
    val path: String,
    val summary: String = ""
)

data class EndpointModification(
    val method: String,
    val path: String,
    val changes: List<FieldChange>
)

data class FieldChange(
    val field: String,
    val changeType: ChangeType,
    val oldValue: String?,
    val newValue: String?,
    val isBreaking: Boolean,
    val description: String
)

enum class ChangeType {
    FIELD_ADDED,
    FIELD_REMOVED,
    TYPE_CHANGED,
    REQUIRED_ADDED,      // field became required
    REQUIRED_REMOVED,    // field became optional
    ENUM_VALUE_REMOVED,
    FORMAT_CHANGED,
    CONSTRAINT_CHANGED
}

enum class DiffSeverity {
    NONE,
    LOW,       // Non-breaking additions only
    MEDIUM,    // Some breaking changes in optional endpoints
    HIGH,      // Breaking changes in core endpoints
    CRITICAL   // Removed endpoints or security changes
}
