package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.ResolvedWorkbenchRequest
import com.postsyncpro.domain.model.WorkbenchExecutionResult
import com.postsyncpro.domain.repository.WorkbenchRequestRepository

class ExecuteWorkbenchRequestUseCase(
    private val repository: WorkbenchRequestRepository
) {
    suspend operator fun invoke(request: ResolvedWorkbenchRequest): WorkbenchExecutionResult =
        repository.execute(request)
}
