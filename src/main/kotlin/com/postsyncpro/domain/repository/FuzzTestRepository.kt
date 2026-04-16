package com.postsyncpro.domain.repository

import com.postsyncpro.domain.model.*
import kotlinx.coroutines.flow.Flow

interface FuzzTestRepository {
    suspend fun generateTestCases(config: FuzzTestConfig): List<FuzzTestCase>
    fun executeTests(testCases: List<FuzzTestCase>, config: FuzzTestConfig): Flow<FuzzTestResult>
    suspend fun cancelTests()
}
