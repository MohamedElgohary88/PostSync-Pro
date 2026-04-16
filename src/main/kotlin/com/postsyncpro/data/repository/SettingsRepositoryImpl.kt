package com.postsyncpro.data.repository

import com.postsyncpro.data.security.ApiKeyEncryption
import com.postsyncpro.domain.model.AiConfig
import com.postsyncpro.domain.model.AiMode
import com.postsyncpro.domain.model.CloudAiConfig
import com.postsyncpro.domain.model.CloudProvider
import com.postsyncpro.domain.model.LocalAiConfig
import com.postsyncpro.domain.repository.SettingsRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SettingsRepositoryImpl : SettingsRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
    }

    private val settingsDir: File
        get() {
            val home = System.getProperty("user.home")
            return File("$home/.postsyncpro")
        }

    private val settingsFile: File
        get() = File(settingsDir, "settings.json")

    override suspend fun saveAiConfig(config: AiConfig) {
        val settings = loadSettings()

        val encryptedCloudConfig = config.cloudConfig?.let { cloud ->
            SerializableCloudConfig(
                provider = cloud.provider.name,
                apiKey = if (cloud.apiKey.isNotEmpty()) ApiKeyEncryption.encrypt(cloud.apiKey) else "",
                model = cloud.model
            )
        }

        val localConfigData = config.localConfig?.let { local ->
            SerializableLocalConfig(
                modelPath = local.modelPath,
                modelName = local.modelName,
                contextSize = local.contextSize,
                threads = local.threads
            )
        }

        val updated = settings.copy(
            aiMode = config.mode.name,
            cloudConfig = encryptedCloudConfig,
            localConfig = localConfigData
        )

        saveSettings(updated)
    }

    override suspend fun loadAiConfig(): AiConfig? {
        val settings = loadSettings()

        if (settings.aiMode.isEmpty()) return null

        val mode = try {
            AiMode.valueOf(settings.aiMode)
        } catch (e: IllegalArgumentException) {
            return null
        }

        val cloudConfig = settings.cloudConfig?.let { cloud ->
            val provider = try {
                CloudProvider.valueOf(cloud.provider)
            } catch (e: IllegalArgumentException) {
                return@let null
            }
            CloudAiConfig(
                provider = provider,
                apiKey = if (cloud.apiKey.isNotEmpty()) {
                    try {
                        ApiKeyEncryption.decrypt(cloud.apiKey)
                    } catch (e: Exception) {
                        ""
                    }
                } else "",
                model = cloud.model
            )
        }

        val localConfig = settings.localConfig?.let { local ->
            LocalAiConfig(
                modelPath = local.modelPath,
                modelName = local.modelName,
                contextSize = local.contextSize,
                threads = local.threads
            )
        }

        return AiConfig(
            mode = mode,
            cloudConfig = cloudConfig,
            localConfig = localConfig
        )
    }

    override suspend fun saveApiKey(provider: String, encryptedKey: String) {
        val settings = loadSettings()
        val updatedKeys = settings.apiKeys.toMutableMap()
        updatedKeys[provider] = ApiKeyEncryption.encrypt(encryptedKey)
        saveSettings(settings.copy(apiKeys = updatedKeys))
    }

    override suspend fun loadApiKey(provider: String): String? {
        val settings = loadSettings()
        val encrypted = settings.apiKeys[provider] ?: return null
        return try {
            ApiKeyEncryption.decrypt(encrypted)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun clearAllSettings() {
        if (settingsFile.exists()) {
            settingsFile.delete()
        }
    }

    private fun loadSettings(): AppSettings {
        if (!settingsFile.exists()) return AppSettings()
        return try {
            json.decodeFromString<AppSettings>(settingsFile.readText())
        } catch (e: Exception) {
            AppSettings()
        }
    }

    private fun saveSettings(settings: AppSettings) {
        settingsDir.mkdirs()
        settingsFile.writeText(json.encodeToString(settings))
    }
}

@Serializable
internal data class AppSettings(
    val aiMode: String = "",
    val cloudConfig: SerializableCloudConfig? = null,
    val localConfig: SerializableLocalConfig? = null,
    val apiKeys: Map<String, String> = emptyMap()
)

@Serializable
internal data class SerializableCloudConfig(
    val provider: String = "",
    val apiKey: String = "",
    val model: String = ""
)

@Serializable
internal data class SerializableLocalConfig(
    val modelPath: String = "",
    val modelName: String = "",
    val contextSize: Int = 2048,
    val threads: Int = 4
)
