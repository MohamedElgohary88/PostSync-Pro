package com.postsyncpro.domain.repository

import com.postsyncpro.domain.model.WorkbenchEnvironment
import com.postsyncpro.domain.model.WorkbenchEnvironmentSnapshot
import com.postsyncpro.domain.model.WorkbenchSavedCollection
import com.postsyncpro.domain.model.WorkbenchSavedFolder
import com.postsyncpro.domain.model.WorkbenchSavedRequest
import com.postsyncpro.domain.model.WorkbenchSavedWorkspace

/**
 * The persisted store exists but cannot be trusted. Mutations fail with this
 * exception until the caller explicitly clears or repairs that store.
 */
class WorkbenchStorageCorruptionException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

interface WorkbenchEnvironmentRepository {
    /** Secret values are decrypted in memory; unavailable values are marked for restoration. */
    suspend fun snapshot(): WorkbenchEnvironmentSnapshot
    suspend fun find(id: String): WorkbenchEnvironment?

    /** Creates or replaces one environment by stable [WorkbenchEnvironment.id]. */
    suspend fun upsert(environment: WorkbenchEnvironment): WorkbenchEnvironmentSnapshot

    /**
     * Creates or replaces [environment] and makes it active as one atomic
     * repository transaction. Implementations must not emulate this by calling
     * [upsert] followed by [select], because the intermediate store would be
     * externally visible and the second write could fail.
     *
     * Cancellation observed exactly at the commit boundary is intentionally
     * not reported as a rollback guarantee: callers that need certainty after
     * cancellation must reload [snapshot] and reconcile by stable ID.
     *
     * The fail-closed default keeps existing custom repositories source
     * compatible without silently weakening the atomicity contract.
     */
    suspend fun upsertAndSelectEnvironment(
        environment: WorkbenchEnvironment
    ): WorkbenchEnvironmentSnapshot = throw UnsupportedOperationException(
        "Atomic environment upsert and selection is not supported by this repository"
    )

    /** Idempotently deletes an environment and clears selection when it was active. */
    suspend fun delete(id: String): WorkbenchEnvironmentSnapshot

    /** Selects an existing environment, or clears selection when [id] is null. */
    suspend fun select(id: String?): WorkbenchEnvironmentSnapshot

    /**
     * Moves the unreadable store to a unique sibling backup, then creates an
     * empty valid store. Returns the absolute backup path, or null when no
     * previous store existed. Implementations must fail closed rather than
     * deleting or overwriting the corrupt bytes.
     */
    suspend fun backupCorruptStoreAndReset(): String? = throw UnsupportedOperationException(
        "Corrupt environment store backup and reset is not supported by this repository"
    )

    /** Explicit recovery operation; also replaces a corrupt store with an empty valid envelope. */
    suspend fun clear()
}

interface WorkbenchSavedRequestRepository {
    suspend fun snapshot(): WorkbenchSavedWorkspace
    suspend fun findRequest(id: String): WorkbenchSavedRequest?

    /**
     * Case-insensitive metadata search over name, description, tags, method and
     * URL. Blank [query] returns the newest matching requests.
     */
    suspend fun search(
        query: String,
        collectionId: String? = null,
        folderId: String? = null
    ): List<WorkbenchSavedRequest>

    suspend fun upsertCollection(collection: WorkbenchSavedCollection): WorkbenchSavedWorkspace

    /**
     * Creates or replaces [collection] and [request] in one atomic repository
     * transaction. [WorkbenchSavedRequest.collectionId] must equal
     * [WorkbenchSavedCollection.id].
     *
     * Cancellation observed exactly at the commit boundary can mean either the
     * complete old or complete new store is present. Callers must reload
     * [snapshot] before retrying or reporting the final state.
     *
     * The fail-closed default prevents a legacy/custom implementation from
     * falling back to two writes and leaving an orphan or a partial update.
     */
    suspend fun upsertCollectionWithRequest(
        collection: WorkbenchSavedCollection,
        request: WorkbenchSavedRequest
    ): WorkbenchSavedWorkspace = throw UnsupportedOperationException(
        "Atomic collection and request upsert is not supported by this repository"
    )

    suspend fun deleteCollection(id: String): WorkbenchSavedWorkspace
    suspend fun upsertFolder(folder: WorkbenchSavedFolder): WorkbenchSavedWorkspace
    suspend fun deleteFolder(id: String): WorkbenchSavedWorkspace
    /** Persists a sanitized copy; credential values are never retained in the saved draft. */
    suspend fun upsertRequest(request: WorkbenchSavedRequest): WorkbenchSavedWorkspace
    suspend fun deleteRequest(id: String): WorkbenchSavedWorkspace

    /**
     * Moves the unreadable store to a unique sibling backup, then creates an
     * empty valid store. Returns the absolute backup path, or null when no
     * previous store existed. Implementations must fail closed rather than
     * deleting or overwriting the corrupt bytes.
     */
    suspend fun backupCorruptStoreAndReset(): String? = throw UnsupportedOperationException(
        "Corrupt saved-request store backup and reset is not supported by this repository"
    )

    /** Explicit recovery operation; also replaces a corrupt store with an empty valid envelope. */
    suspend fun clear()
}
