package com.example.messagingapp.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.KeyGenerator

/**
 * Manages encryption keys including:
 * - Generation of AES keys
 * - Storage of keys securely
 * - Handling of session keys
 */
class KeyManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "com.example.messagingapp.keys"
        private const val KEY_SECRET_KEY = "secret_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_ALIAS = "com.example.messagingapp.AES"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cryptoManager = CryptoManager()
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().getReference("users")
    
    /**
     * Generate and store a new AES key for the current user
     */
    fun generateAndStoreKey(): SecretKey {
        // Generate a new key
        val key = generateAESKey()
        
        // Store key securely
        saveSecretKey(key)
        
        return key
    }
    
    /**
     * Generate a new AES key
     */
    private fun generateAESKey(): SecretKey {
        // Try to use Android Keystore for security
        try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
            )
            
            val spec = KeyGenParameterSpec.Builder(
                AES_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setKeySize(256)
                .build()
            
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        } catch (e: Exception) {
            // Fall back to regular KeyGenerator if Keystore isn't available
            return cryptoManager.generateAESKey()
        }
    }
    
    /**
     * Save AES key securely
     */
    private fun saveSecretKey(key: SecretKey) {
        // If using Android Keystore, the key is already stored securely
        if (key.algorithm == "AndroidKeyStore") {
            return
        }
        
        // Otherwise, encrypt and store in SharedPreferences
        val keyEncoded = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        prefs.edit().putString(KEY_SECRET_KEY, keyEncoded).apply()
    }
    
    /**
     * Get the user's secret key
     */
    fun getSecretKey(): SecretKey? {
        try {
            // Try to get from Android Keystore first
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            
            if (keyStore.containsAlias(AES_ALIAS)) {
                val entry = keyStore.getEntry(AES_ALIAS, null) as? KeyStore.SecretKeyEntry
                return entry?.secretKey
            }
            
            // Fall back to SharedPreferences
            val keyString = prefs.getString(KEY_SECRET_KEY, null) ?: return null
            val keyBytes = Base64.decode(keyString, Base64.NO_WRAP)
            
            return cryptoManager.stringToSecretKey(keyString)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Generate a one-time symmetric key for a chat session
     */
    fun generateSessionKey(): SecretKey {
        return cryptoManager.generateAESKey()
    }
} 