package com.postsyncpro.domain.usecase

import com.postsyncpro.domain.model.AiConfig
import com.postsyncpro.domain.repository.SettingsRepository

class ManageSettingsUseCase(
    private val settingsRepository: SettingsRepository
) {
    suspend fun saveConfig(config: AiConfig) = settingsRepository.saveAiConfig(config)
    suspend fun loadConfig(): AiConfig? = settingsRepository.loadAiConfig()
    suspend fun saveApiKey(provider: String, key: String) = settingsRepository.saveApiKey(provider, key)
    suspend fun loadApiKey(provider: String): String? = settingsRepository.loadApiKey(provider)
}
