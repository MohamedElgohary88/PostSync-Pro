package com.postsyncpro.data.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiKeyCipherSecurityTest {

    @Test
    fun `round trip uses one atomically created private key under concurrency`(): Unit = runBlocking {
        val directory = createTempDirectory("postsync-cipher-").toFile()
        try {
            val keyFile = directory.resolve("nested/.keystore")
            val cipher = ApiKeyCipher(keyFile)
            val values = (1..24).map { "secret-$it" }

            val encrypted = values.map { value ->
                async(Dispatchers.Default) { cipher.encrypt(value) }
            }.awaitAll()

            assertEquals(values, encrypted.map(cipher::decrypt))
            assertTrue(keyFile.isFile)
            assertFalse(directory.resolve("nested").listFiles().orEmpty().any { it.name.endsWith(".tmp") })

            runCatching { Files.getPosixFilePermissions(keyFile.toPath()) }.getOrNull()?.let { permissions ->
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
    fun `decrypt rejects malformed and truncated ciphertext before indexing`() {
        val directory = createTempDirectory("postsync-cipher-invalid-").toFile()
        try {
            val cipher = ApiKeyCipher(directory.resolve(".keystore"))
            assertFailsWith<IllegalArgumentException> { cipher.decrypt("not base64!") }
            assertFailsWith<IllegalArgumentException> { cipher.decrypt("AA==") }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `corrupted key is rejected instead of silently replaced`() {
        val directory = createTempDirectory("postsync-cipher-corrupt-").toFile()
        try {
            val keyFile = directory.resolve(".keystore").apply { writeText("broken") }
            val cipher = ApiKeyCipher(keyFile)

            assertFailsWith<IllegalStateException> { cipher.encrypt("secret") }
            assertEquals("broken", keyFile.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
