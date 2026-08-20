package com.postsyncpro.data.repository

import com.postsyncpro.data.security.ApiKeyEncryption
import com.postsyncpro.domain.model.AiConfig
import com.postsyncpro.domain.model.AiMode
import com.postsyncpro.domain.model.CloudAiConfig
import com.postsyncpro.domain.model.CloudProvider
import com.postsyncpro.domain.model.LocalAiConfig
import com.postsyncpro.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions

class SettingsRepositoryImpl(
    private val settingsDirOverride: File? = null,
    private val encryptValue: (String) -> String = ApiKeyEncryption::encrypt,
    private val decryptValue: (String) -> String = ApiKeyEncryption::decrypt,
    private val clearEncryptionKey: () -> Unit = ApiKeyEncryption::clearKey
) : SettingsRepository {

    private companion object {
        const val MAX_SETTINGS_BYTES = 1024 * 1024
        const val MAX_PROVIDER_LENGTH = 128
    }

    private val settingsMutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
    }

    private val settingsDir: File
        get() = settingsDirOverride ?: File(
            System.getProperty("user.home") ?: throw IllegalStateException("User home is not available"),
            ".postsyncpro"
        )

    private val settingsFile: File
        get() = File(settingsDir, "settings.json")

    private val backupFile: File
        get() = File(settingsDir, "settings.json.bak")

    override suspend fun saveAiConfig(config: AiConfig) = withSettingsLock {
        val settings = loadSettingsUnlocked()
        val encryptedCloudConfig = config.cloudConfig?.let { cloud ->
            SerializableCloudConfig(
                provider = cloud.provider.name,
                apiKey = if (cloud.apiKey.isNotEmpty()) encryptValue(cloud.apiKey) else "",
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
        saveSettingsUnlocked(
            settings.copy(
                aiMode = config.mode.name,
                cloudConfig = encryptedCloudConfig,
                localConfig = localConfigData
            )
        )
    }

    override suspend fun loadAiConfig(): AiConfig? = withSettingsLock {
        val settings = loadSettingsUnlocked()
        if (settings.aiMode.isEmpty()) return@withSettingsLock null

        val mode = runCatching { AiMode.valueOf(settings.aiMode) }.getOrNull()
            ?: return@withSettingsLock null
        val cloudConfig = settings.cloudConfig?.let { cloud ->
            val provider = runCatching { CloudProvider.valueOf(cloud.provider) }.getOrNull()
                ?: return@let null
            CloudAiConfig(
                provider = provider,
                apiKey = if (cloud.apiKey.isNotEmpty()) {
                    runCatching { decryptValue(cloud.apiKey) }.getOrDefault("")
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
        AiConfig(mode = mode, cloudConfig = cloudConfig, localConfig = localConfig)
    }

    override suspend fun saveApiKey(provider: String, encryptedKey: String) = withSettingsLock {
        require(provider.isNotBlank() && provider.length <= MAX_PROVIDER_LENGTH) {
            "Provider name is invalid"
        }
        val settings = loadSettingsUnlocked()
        val updatedKeys = settings.apiKeys.toMutableMap().apply {
            put(provider, encryptValue(encryptedKey))
        }
        saveSettingsUnlocked(settings.copy(apiKeys = updatedKeys))
    }

    override suspend fun loadApiKey(provider: String): String? = withSettingsLock {
        require(provider.isNotBlank() && provider.length <= MAX_PROVIDER_LENGTH) {
            "Provider name is invalid"
        }
        val encrypted = loadSettingsUnlocked().apiKeys[provider] ?: return@withSettingsLock null
        runCatching { decryptValue(encrypted) }.getOrNull()
    }

    override suspend fun clearAllSettings() = withSettingsLock {
        rejectSymbolicLink(settingsFile)
        rejectSymbolicLink(backupFile)
        Files.deleteIfExists(settingsFile.toPath())
        Files.deleteIfExists(backupFile.toPath())
        clearEncryptionKey()
    }

    private suspend fun <T> withSettingsLock(block: () -> T): T = withContext(Dispatchers.IO) {
        settingsMutex.withLock { block() }
    }

    private fun loadSettingsUnlocked(): AppSettings {
        rejectSymbolicLink(settingsFile)
        if (!settingsFile.exists()) return AppSettings()

        return try {
            decodeSettings(settingsFile)
        } catch (primaryError: Exception) {
            rejectSymbolicLink(backupFile)
            if (!backupFile.exists()) {
                throw IllegalStateException("Settings file is corrupted and no backup is available", primaryError)
            }
            try {
                decodeSettings(backupFile)
            } catch (backupError: Exception) {
                primaryError.addSuppressed(backupError)
                throw IllegalStateException("Settings file and backup are corrupted", primaryError)
            }
        }
    }

    private fun decodeSettings(file: File): AppSettings {
        require(file.isFile) { "Settings path is not a regular file" }
        require(file.length() in 0L..MAX_SETTINGS_BYTES.toLong()) { "Settings file is too large" }
        return json.decodeFromString(file.readText(Charsets.UTF_8))
    }

    private fun saveSettingsUnlocked(settings: AppSettings) {
        ensurePrivateDirectory(settingsDir)
        val serialized = json.encodeToString(settings).toByteArray(Charsets.UTF_8)
        require(serialized.size <= MAX_SETTINGS_BYTES) { "Settings are too large" }

        rejectSymbolicLink(settingsFile)
        rejectSymbolicLink(backupFile)
        if (settingsFile.exists()) {
            require(settingsFile.isFile && settingsFile.length() <= MAX_SETTINGS_BYTES.toLong()) {
                "Existing settings file is invalid"
            }
            atomicWrite(backupFile, settingsFile.readBytes())
        }
        atomicWrite(settingsFile, serialized)
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        ensurePrivateDirectory(target.parentFile ?: throw IllegalStateException("Missing settings directory"))
        val temporary = Files.createTempFile(settingsDir.toPath(), ".settings-", ".tmp").toFile()
        try {
            temporary.writeBytes(bytes)
            secureFile(temporary)
            FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            secureFile(target)
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
    }

    private fun ensurePrivateDirectory(directory: File) {
        val path = directory.toPath()
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymbolicLink(directory)
            require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                "Settings parent is not a directory"
            }
        } else {
            Files.createDirectories(path)
        }
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"))
        } catch (_: UnsupportedOperationException) {
            directory.setReadable(false, false)
            directory.setReadable(true, true)
            directory.setWritable(false, false)
            directory.setWritable(true, true)
            directory.setExecutable(false, false)
            directory.setExecutable(true, true)
        }
    }

    private fun secureFile(file: File) {
        try {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-------"))
        } catch (_: UnsupportedOperationException) {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
            file.setExecutable(false, false)
        }
    }

    private fun rejectSymbolicLink(file: File) {
        require(!Files.isSymbolicLink(file.toPath())) { "Symbolic links are not allowed for settings" }
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
