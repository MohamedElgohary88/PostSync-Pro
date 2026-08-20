package com.postsyncpro.domain.repository

import com.postsyncpro.domain.model.ResolvedWorkbenchRequest
import com.postsyncpro.domain.model.WorkbenchExecutionResult
import com.postsyncpro.domain.model.WorkbenchHistoryEntry

interface WorkbenchRequestRepository {
    /** Cancellation of the calling coroutine must cancel the in-flight request. */
    suspend fun execute(request: ResolvedWorkbenchRequest): WorkbenchExecutionResult
}

interface WorkbenchHistoryRepository {
    suspend fun list(): List<WorkbenchHistoryEntry>
    suspend fun find(id: String): WorkbenchHistoryEntry?

    /**
     * Persists a sanitized entry and returns the current newest-first snapshot.
     * Implementations are responsible for stripping secrets even if a caller
     * accidentally passes an unsanitized entry.
     */
    suspend fun save(entry: WorkbenchHistoryEntry): List<WorkbenchHistoryEntry>

    suspend fun clear()
}
