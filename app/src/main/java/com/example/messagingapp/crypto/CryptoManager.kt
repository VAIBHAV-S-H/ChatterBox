package com.example.messagingapp.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Comprehensive cryptography manager that implements:
 * - Symmetric encryption (AES)
 * - Asymmetric encryption (RSA)
 * - Hashing (SHA-256)
 * - HMAC (SHA-256)
 * - Digital signatures (RSA)
 */
class CryptoManager {

    companion object {
        private const val AES_ALGORITHM = "AES"
        private const val AES_BLOCK_MODE = "CBC"
        private const val AES_PADDING = "PKCS7Padding"
        private const val AES_TRANSFORMATION = "$AES_ALGORITHM/$AES_BLOCK_MODE/$AES_PADDING"
        private const val RSA_ALGORITHM = "RSA"
        private const val RSA_MODE = "ECB"
        private const val RSA_PADDING = "PKCS1Padding"
        private const val RSA_TRANSFORMATION = "$RSA_ALGORITHM/$RSA_MODE/$RSA_PADDING"
        private const val HASH_ALGORITHM = "SHA-256"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
        private const val KEY_SIZE_AES = 256
        private const val KEY_SIZE_RSA = 2048
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    // ========== Symmetric Encryption (AES) ==========

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

    // ========== Asymmetric Encryption (RSA) ==========

    /**
     * Generate RSA key pair for asymmetric encryption
     */
    fun generateRSAKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM)
        keyPairGenerator.initialize(KEY_SIZE_RSA)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Encrypt data using RSA public key
     * Note: RSA has size limitations for encryption, use for small data or keys only
     */
    fun encryptRSA(data: ByteArray, publicKey: PublicKey): ByteArray {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }

    /**
     * Decrypt data using RSA private key
     */
    fun decryptRSA(encryptedData: ByteArray, privateKey: PrivateKey): ByteArray {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(encryptedData)
    }

    // ========== Hashing (SHA-256) ==========

    /**
     * Create a hash of the data using SHA-256
     */
    fun hash(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        return digest.digest(data)
    }

    // ========== HMAC (SHA-256) ==========

    /**
     * Generate an HMAC key
     */
    fun generateHMACKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(HMAC_ALGORITHM)
        return keyGenerator.generateKey()
    }

    /**
     * Create an HMAC for the data
     */
    fun createHMAC(data: ByteArray, key: SecretKey): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(key)
        return mac.doFinal(data)
    }

    /**
     * Verify an HMAC value
     */
    fun verifyHMAC(data: ByteArray, mac: ByteArray, key: SecretKey): Boolean {
        val calculatedMac = createHMAC(data, key)
        return calculatedMac.contentEquals(mac)
    }

    // ========== Digital Signature (RSA) ==========

    /**
     * Sign data using a private key
     */
    fun sign(data: ByteArray, privateKey: PrivateKey): ByteArray {
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    /**
     * Verify a signature using a public key
     */
    fun verifySignature(data: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean {
        val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
        sig.initVerify(publicKey)
        sig.update(data)
        return sig.verify(signature)
    }

    // ========== Utility Methods ==========

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
    fun stringToSecretKey(keyStr: String, algorithm: String = AES_ALGORITHM): SecretKey {
        val keyBytes = decodeFromBase64(keyStr)
        return SecretKeySpec(keyBytes, algorithm)
    }

    /**
     * Convert a PublicKey to a string representation (for storage)
     */
    fun publicKeyToString(publicKey: PublicKey): String {
        return encodeToBase64(publicKey.encoded)
    }

    /**
     * Create a PublicKey from a string representation
     */
    fun stringToPublicKey(keyStr: String): PublicKey {
        val keyBytes = decodeFromBase64(keyStr)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance(RSA_ALGORITHM)
        return keyFactory.generatePublic(keySpec)
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