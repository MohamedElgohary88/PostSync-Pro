package com.postsyncpro.domain.repository

import com.postsyncpro.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface MockServerRepository {
    val requestLogs: StateFlow<List<MockRequestLog>>
    suspend fun startServer(config: MockServerConfig, routes: List<MockRoute>)
    suspend fun stopServer()
    fun isRunning(): Boolean
    fun clearLogs()
}
