package com.postsyncpro.data.security

import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ApiKeyEncryption {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val KEY_SIZE = 256

    private val keystorePath: String
        get() {
            val home = System.getProperty("user.home")
            return "$home/.postsyncpro/.keystore"
        }

    fun encrypt(plainText: String): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val salt = getMachineSalt()
        cipher.updateAAD(salt)

        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Prepend IV to ciphertext before Base64 encoding
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encryptedText: String): String {
        val key = getOrCreateKey()
        val combined = Base64.getDecoder().decode(encryptedText)

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val salt = getMachineSalt()
        cipher.updateAAD(salt)

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val file = File(keystorePath)

        if (file.exists()) {
            val keyBytes = Base64.getDecoder().decode(file.readText().trim())
            return SecretKeySpec(keyBytes, ALGORITHM)
        }

        return generateKey()
    }

    private fun generateKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(KEY_SIZE, SecureRandom())
        val key = keyGen.generateKey()

        val file = File(keystorePath)
        file.parentFile?.mkdirs()
        file.writeText(Base64.getEncoder().encodeToString(key.encoded))

        // Restrict file permissions on Unix-like systems
        try {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        } catch (_: Exception) {
            // Permission setting may not be supported on all platforms
        }

        return key
    }

    private fun getMachineSalt(): ByteArray {
        val saltSource = buildString {
            append(System.getProperty("os.name", ""))
            append(System.getProperty("os.arch", ""))
            append(System.getProperty("user.name", ""))
            append(System.getProperty("user.home", ""))
        }

        // Use a simple hash of machine properties as additional authenticated data
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(saltSource.toByteArray(Charsets.UTF_8))
    }
}
