package com.postsyncpro.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsRepositorySecurityTest {

    private fun repository(
        directory: java.io.File,
        cleared: AtomicBoolean = AtomicBoolean(false)
    ) = SettingsRepositoryImpl(
        settingsDirOverride = directory,
        encryptValue = { "encrypted::$it" },
        decryptValue = { value ->
            require(value.startsWith("encrypted::"))
            value.removePrefix("encrypted::")
        },
        clearEncryptionKey = { cleared.set(true) }
    )

    @Test
    fun `concurrent updates do not lose providers and files are private`(): Unit = runBlocking {
        val directory = createTempDirectory("postsync-settings-").toFile()
        try {
            val repository = repository(directory)
            (1..30).map { index ->
                async(Dispatchers.Default) { repository.saveApiKey("provider-$index", "key-$index") }
            }.awaitAll()

            (1..30).forEach { index ->
                assertEquals("key-$index", repository.loadApiKey("provider-$index"))
            }
            assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })

            runCatching { Files.getPosixFilePermissions(directory.resolve("settings.json").toPath()) }
                .getOrNull()
                ?.let { permissions ->
                    assertEquals(
                        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                        permissions
                    )
                }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `corrupt primary settings recover from the last valid backup`(): Unit = runBlocking {
        val directory = createTempDirectory("postsync-settings-backup-").toFile()
        try {
            val repository = repository(directory)
            repository.saveApiKey("first", "one")
            repository.saveApiKey("second", "two")
            directory.resolve("settings.json").writeText("{not-json")

            assertEquals("one", repository.loadApiKey("first"))
            assertNull(repository.loadApiKey("second"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `corrupt settings without backup fail explicitly`(): Unit = runBlocking {
        val directory = createTempDirectory("postsync-settings-corrupt-").toFile()
        try {
            directory.resolve("settings.json").writeText("{not-json")
            val repository = repository(directory)

            assertFailsWith<IllegalStateException> { repository.loadApiKey("provider") }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `clear removes primary backup and encryption key`(): Unit = runBlocking {
        val directory = createTempDirectory("postsync-settings-clear-").toFile()
        val cleared = AtomicBoolean(false)
        try {
            val repository = repository(directory, cleared)
            repository.saveApiKey("first", "one")
            repository.saveApiKey("second", "two")

            repository.clearAllSettings()

            assertFalse(directory.resolve("settings.json").exists())
            assertFalse(directory.resolve("settings.json.bak").exists())
            assertTrue(cleared.get())
        } finally {
            directory.deleteRecursively()
        }
    }
}
