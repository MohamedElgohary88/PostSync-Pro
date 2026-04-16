package com.postsyncpro.domain.repository

import com.postsyncpro.domain.model.AiConfig

interface SettingsRepository {
    suspend fun saveAiConfig(config: AiConfig)
    suspend fun loadAiConfig(): AiConfig?
    suspend fun saveApiKey(provider: String, encryptedKey: String)
    suspend fun loadApiKey(provider: String): String?
    suspend fun clearAllSettings()
}
