package com.postsyncpro.data.security

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ApiKeyEncryption {
    private val cipher by lazy {
        val home = System.getProperty("user.home")
            ?: throw IllegalStateException("User home is not available")
        ApiKeyCipher(File(home, ".postsyncpro/.keystore"))
    }

    fun encrypt(plainText: String): String = cipher.encrypt(plainText)

    fun decrypt(encryptedText: String): String = cipher.decrypt(encryptedText)

    fun clearKey() = cipher.clearKey()
}

/**
 * File-backed AES-GCM cipher. The format and machine-bound AAD intentionally match
 * earlier PostSync Pro versions so existing encrypted settings remain readable.
 */
internal class ApiKeyCipher(private val keyFile: File) {

    private companion object {
        const val ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8
        const val GCM_IV_LENGTH = 12
        const val KEY_SIZE_BITS = 256
        const val KEY_SIZE_BYTES = KEY_SIZE_BITS / 8
        const val MAX_PLAIN_TEXT_BYTES = 64 * 1024
        const val MAX_ENCODED_TEXT_CHARS = 128 * 1024

        val PATH_LOCKS = ConcurrentHashMap<String, Any>()
    }

    fun encrypt(plainText: String): String {
        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        require(plainBytes.size <= MAX_PLAIN_TEXT_BYTES) { "API key is too large" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH).also(SecureRandom()::nextBytes)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(machineSalt())
        val encryptedBytes = cipher.doFinal(plainBytes)

        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encryptedText: String): String {
        require(encryptedText.isNotBlank()) { "Encrypted API key is empty" }
        require(encryptedText.length <= MAX_ENCODED_TEXT_CHARS) { "Encrypted API key is too large" }

        val combined = try {
            Base64.getDecoder().decode(encryptedText)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Encrypted API key is not valid Base64", error)
        }
        require(combined.size >= GCM_IV_LENGTH + GCM_TAG_LENGTH_BYTES) {
            "Encrypted API key is truncated"
        }
        require(combined.size <= MAX_PLAIN_TEXT_BYTES + GCM_IV_LENGTH + GCM_TAG_LENGTH_BYTES) {
            "Encrypted API key is too large"
        }

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(machineSalt())
        return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
    }

    fun clearKey() = withExclusiveKeyLock {
        val path = keyPath()
        rejectSymbolicLink(path)
        Files.deleteIfExists(path)
    }

    private fun getOrCreateKey(): SecretKey = withExclusiveKeyLock {
        val path = keyPath()
        rejectSymbolicLink(path)
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return@withExclusiveKeyLock readExistingKey(path.toFile())
        }

        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        keyGenerator.init(KEY_SIZE_BITS, SecureRandom())
        val key = keyGenerator.generateKey()
        atomicWriteKey(path.toFile(), Base64.getEncoder().encodeToString(key.encoded))
        key
    }

    private fun readExistingKey(file: File): SecretKey {
        require(file.isFile) { "API key store is not a regular file" }
        require(file.length() in 1L..256L) { "API key store has an invalid size" }
        val keyBytes = try {
            Base64.getDecoder().decode(file.readText(Charsets.US_ASCII).trim())
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("API key store is corrupted", error)
        }
        check(keyBytes.size == KEY_SIZE_BYTES) { "API key store contains an invalid key" }
        secureFile(file)
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    private fun atomicWriteKey(target: File, encodedKey: String) {
        val parent = target.parentFile
            ?: throw IllegalStateException("API key store has no parent directory")
        ensurePrivateDirectory(parent)
        val temporary = Files.createTempFile(parent.toPath(), ".keystore-", ".tmp").toFile()
        try {
            temporary.writeText(encodedKey, Charsets.US_ASCII)
            secureFile(temporary)
            FileChannel.open(temporary.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            try {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath())
            }
            secureFile(target)
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
    }

    private inline fun <T> withExclusiveKeyLock(block: () -> T): T {
        val path = keyPath()
        val parent = path.parent.toFile()
        ensurePrivateDirectory(parent)
        val processLock = PATH_LOCKS.computeIfAbsent(path.toString()) { Any() }
        return synchronized(processLock) {
            val lockPath = parent.toPath().resolve(".${path.fileName}.lock")
            rejectSymbolicLink(lockPath)
            FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                secureFile(lockPath.toFile())
                channel.lock().use { block() }
            }
        }
    }

    private fun keyPath() = keyFile.toPath().toAbsolutePath().normalize()

    private fun ensurePrivateDirectory(directory: File) {
        val path = directory.toPath()
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymbolicLink(path)
            require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                "API key store parent is not a directory"
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

    private fun rejectSymbolicLink(path: java.nio.file.Path) {
        require(!Files.isSymbolicLink(path)) { "Symbolic links are not allowed for the API key store" }
    }

    private fun machineSalt(): ByteArray {
        val saltSource = buildString {
            append(System.getProperty("os.name", ""))
            append(System.getProperty("os.arch", ""))
            append(System.getProperty("user.name", ""))
            append(System.getProperty("user.home", ""))
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(saltSource.toByteArray(Charsets.UTF_8))
    }
}
