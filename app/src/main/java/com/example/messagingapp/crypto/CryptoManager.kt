package com.example.messagingapp.crypto

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Comprehensive cryptography manager that implements:
 * - Symmetric encryption (AES)
 */
class CryptoManager {

    companion object {
        private const val AES_ALGORITHM = "AES"
        private const val AES_BLOCK_MODE = "CBC"
        private const val AES_PADDING = "PKCS7Padding"
        private const val AES_TRANSFORMATION = "$AES_ALGORITHM/$AES_BLOCK_MODE/$AES_PADDING"
        private const val KEY_SIZE_AES = 256
    }

    /**
     * Generate a new AES key for symmetric encryption
     */
    fun generateAESKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM)
        keyGenerator.init(KEY_SIZE_AES)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt data using AES symmetric encryption
     */
    fun encryptAES(data: ByteArray, key: SecretKey): EncryptedData {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data)
        
        return EncryptedData(
            data = encryptedData,
            iv = iv
        )
    }

    /**
     * Decrypt data using AES symmetric encryption
     */
    fun decryptAES(encryptedData: EncryptedData, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val ivSpec = IvParameterSpec(encryptedData.iv)
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
        return cipher.doFinal(encryptedData.data)
    }

    /**
     * Encode bytes to Base64 string for storage/transmission
     */
    fun encodeToBase64(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    /**
     * Decode Base64 string to bytes
     */
    fun decodeFromBase64(data: String): ByteArray {
        return Base64.decode(data, Base64.NO_WRAP)
    }

    /**
     * Convert a SecretKey to a string representation (for storage)
     */
    fun secretKeyToString(secretKey: SecretKey): String {
        return encodeToBase64(secretKey.encoded)
    }

    /**
     * Create a SecretKey from a string representation
     */
    fun stringToSecretKey(keyStr: String): SecretKey {
        val keyBytes = decodeFromBase64(keyStr)
        return SecretKeySpec(keyBytes, AES_ALGORITHM)
    }

    /**
     * Data class to hold encrypted data and its IV
     */
    data class EncryptedData(
        val data: ByteArray,
        val iv: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as EncryptedData

            if (!data.contentEquals(other.data)) return false
            if (!iv.contentEquals(other.iv)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }
    }
} 