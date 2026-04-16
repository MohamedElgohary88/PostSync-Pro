package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.SrsExtractionResult
import com.postsyncpro.domain.repository.AiAnalyzerRepository

class ExtractRulesUseCase(
    private val aiRepository: AiAnalyzerRepository
) {
    suspend operator fun invoke(text: String, targetEndpoint: String = ""): SrsExtractionResult {
        return aiRepository.extractRulesFromText(text, targetEndpoint)
    }

    suspend fun isAiAvailable(): Boolean = aiRepository.isAvailable()
    fun getProviderName(): String = aiRepository.getProviderName()
}
