package com.postsyncpro.domain.repository

import com.postsyncpro.domain.model.*

interface TestChainRepository {
    suspend fun executeNode(
        node: TestChainNode,
        baseUrl: String,
        variables: Map<String, String>
    ): ChainNodeResult
}
