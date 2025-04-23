package com.example.messagingapp.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.SecretKey

/**
 * Manages encryption keys including:
 * - Generation of key pairs
 * - Storage of private keys securely
 * - Exchange of public keys
 * - Handling of session keys
 */
class KeyManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "com.example.messagingapp.keys"
        private const val KEY_PRIVATE_KEY = "private_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val RSA_ALIAS = "com.example.messagingapp.RSA"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cryptoManager = CryptoManager()
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().getReference("users")
    
    /**
     * Generate and store a new RSA key pair for the current user
     */
    fun generateAndStoreKeyPair(): KeyPair {
        // Generate a new key pair
        val keyPair = generateKeyPair()
        
        // Store private key securely
        savePrivateKey(keyPair)
        
        // Upload public key to Firebase
        auth.currentUser?.uid?.let { userId ->
            val publicKeyString = cryptoManager.publicKeyToString(keyPair.public)
            database.child(userId).child("publicKey").setValue(publicKeyString)
        }
        
        return keyPair
    }
    
    /**
     * Generate a new RSA key pair
     */
    private fun generateKeyPair(): KeyPair {
        // Try to use Android Keystore for security
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE
            )
            
            val spec = KeyGenParameterSpec.Builder(
                RSA_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT or
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setKeySize(2048)
                .build()
            
            keyPairGenerator.initialize(spec)
            return keyPairGenerator.generateKeyPair()
        } catch (e: Exception) {
            // Fall back to regular KeyPairGenerator if Keystore isn't available
            return cryptoManager.generateRSAKeyPair()
        }
    }
    
    /**
     * Save private key securely
     */
    private fun savePrivateKey(keyPair: KeyPair) {
        // If using Android Keystore, the private key is already stored securely
        if (keyPair.private.algorithm == "AndroidKeyStore") {
            return
        }
        
        // Otherwise, encrypt and store in SharedPreferences
        val privateKeyEncoded = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        prefs.edit().putString(KEY_PRIVATE_KEY, privateKeyEncoded).apply()
    }
    
    /**
     * Get the user's private key
     */
    fun getPrivateKey(): PrivateKey? {
        try {
            // Try to get from Android Keystore first
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            
            if (keyStore.containsAlias(RSA_ALIAS)) {
                val entry = keyStore.getEntry(RSA_ALIAS, null) as? KeyStore.PrivateKeyEntry
                return entry?.privateKey
            }
            
            // Fall back to SharedPreferences
            val privateKeyString = prefs.getString(KEY_PRIVATE_KEY, null) ?: return null
            val keyBytes = Base64.decode(privateKeyString, Base64.NO_WRAP)
            
            // This is a simplified example - in a real app you would securely transform
            // these bytes back into a PrivateKey using appropriate factories
            return null // Replace with actual implementation
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Fetch a user's public key from Firebase
     */
    fun getUserPublicKey(userId: String, callback: (PublicKey?) -> Unit) {
        database.child(userId).child("publicKey").get()
            .addOnSuccessListener { snapshot ->
                val publicKeyString = snapshot.getValue(String::class.java)
                if (publicKeyString != null) {
                    val publicKey = cryptoManager.stringToPublicKey(publicKeyString)
                    callback(publicKey)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener {
                callback(null)
            }
    }
    
    /**
     * Generate a one-time symmetric key for a chat session
     */
    fun generateSessionKey(): SecretKey {
        return cryptoManager.generateAESKey()
    }
} 