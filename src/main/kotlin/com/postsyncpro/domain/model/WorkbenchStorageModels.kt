package com.postsyncpro.domain.model

import kotlinx.serialization.Serializable

/**
 * A reusable variable set for the request workbench.
 *
 * Secret variable values are available in memory after an environment is
 * unlocked, but file repositories encrypt them before they cross the storage
 * boundary. A key in [secretsRequiringRestore] has no usable value and must be
 * supplied again before executing a request that references it.
 */
@Serializable
data class WorkbenchEnvironment(
    val id: String,
    val name: String,
    val variables: Map<String, WorkbenchVariable> = emptyMap(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val secretsRequiringRestore: Set<String> = emptySet()
) {
    override fun toString(): String =
        "WorkbenchEnvironment(id=$id, variableCount=${variables.size}, " +
            "createdAtEpochMillis=$createdAtEpochMillis, updatedAtEpochMillis=$updatedAtEpochMillis, " +
            "secretsRequiringRestoreCount=${secretsRequiringRestore.size})"
}

@Serializable
data class WorkbenchEnvironmentSnapshot(
    val activeEnvironmentId: String? = null,
    val environments: List<WorkbenchEnvironment> = emptyList()
) {
    val activeEnvironment: WorkbenchEnvironment?
        get() = environments.firstOrNull { it.id == activeEnvironmentId }
}

@Serializable
data class WorkbenchSavedCollection(
    val id: String,
    val name: String,
    val description: String = "",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Serializable
data class WorkbenchSavedFolder(
    val id: String,
    val collectionId: String,
    val parentFolderId: String? = null,
    val name: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Serializable
enum class WorkbenchSecretLocation {
    AUTH,
    VARIABLE,
    HEADER,
    QUERY,
    URL,
    BODY,
    ASSERTION
}

/** Safe metadata describing a value that was deliberately omitted on disk. */
@Serializable
data class WorkbenchRequiredSecret(
    /** Stable within a saved request, for example `variable:apiToken`. */
    val id: String,
    val location: WorkbenchSecretLocation,
    val name: String
)

/**
 * A sanitized, reusable request draft.
 *
 * [draft] and [variables] never contain persisted credentials. Callers must
 * restore every item in [requiredSecrets] before sending when it is relevant
 * to the selected authentication or request section.
 */
@Serializable
data class WorkbenchSavedRequest(
    val id: String,
    val collectionId: String,
    val folderId: String? = null,
    val name: String,
    val description: String = "",
    val tags: Set<String> = emptySet(),
    val draft: WorkbenchRequestDraft,
    val variables: Map<String, WorkbenchVariable> = emptyMap(),
    /** Declarative checks saved with the request; unsafe comparisons are disabled on persistence. */
    val assertions: List<WorkbenchAssertion> = emptyList(),
    val requiredSecrets: List<WorkbenchRequiredSecret> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    /** Deliberately omits the draft, variables, assertions and free-form metadata. */
    override fun toString(): String =
        "WorkbenchSavedRequest(id=$id, collectionId=$collectionId, folderId=$folderId, " +
            "method=${draft.method}, requiredSecretCount=${requiredSecrets.size}, " +
            "createdAtEpochMillis=$createdAtEpochMillis, updatedAtEpochMillis=$updatedAtEpochMillis)"
}

@Serializable
data class WorkbenchSavedWorkspace(
    val collections: List<WorkbenchSavedCollection> = emptyList(),
    val folders: List<WorkbenchSavedFolder> = emptyList(),
    val requests: List<WorkbenchSavedRequest> = emptyList()
)
