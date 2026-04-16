package com.postsyncpro.domain.repository

import com.postsyncpro.domain.model.SrsExtractionResult

interface AiAnalyzerRepository {
    suspend fun extractRulesFromText(text: String, targetEndpoint: String = ""): SrsExtractionResult
    suspend fun isAvailable(): Boolean
    fun getProviderName(): String
}
