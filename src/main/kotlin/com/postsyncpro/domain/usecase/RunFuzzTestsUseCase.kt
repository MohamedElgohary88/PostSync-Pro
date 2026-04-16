package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.*
import com.postsyncpro.domain.repository.FuzzTestRepository
import kotlinx.coroutines.flow.Flow

class RunFuzzTestsUseCase(
    private val fuzzRepository: FuzzTestRepository
) {
    suspend fun generateTests(config: FuzzTestConfig): List<FuzzTestCase> {
        return fuzzRepository.generateTestCases(config)
    }

    fun executeTests(testCases: List<FuzzTestCase>, config: FuzzTestConfig): Flow<FuzzTestResult> {
        return fuzzRepository.executeTests(testCases, config)
    }

    suspend fun cancel() {
        fuzzRepository.cancelTests()
    }
}
