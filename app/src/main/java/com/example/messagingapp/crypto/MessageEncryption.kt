package com.example.messagingapp.crypto

import android.util.Log
import com.example.messagingapp.details.Message
import javax.crypto.SecretKey

class MessageEncryption(private val cryptoManager: CryptoManager) {
    
    /**
     * Encrypts a message using AES encryption
     * @param message The message to encrypt
     * @param key The AES key to use for encryption
     * @return The fully populated encrypted message object
     */
    fun encryptMessage(message: Message, key: SecretKey): Message {
        try {
            val originalText = message.originalText ?: return message
            
            // Encrypt the message with AES
            val encryptedData = cryptoManager.encryptAES(originalText.toByteArray(), key)
            
            // Update the message with encrypted data
            message.encryptedText = cryptoManager.encodeToBase64(encryptedData.data)
            message.iv = cryptoManager.encodeToBase64(encryptedData.iv)
            
            return message
        } catch (e: Exception) {
            Log.e("MessageEncryption", "Error encrypting message", e)
            throw e
        }
    }
    
    /**
     * Decrypts a message using AES
     * @param message The encrypted message
     * @param key The AES key to use for decryption
     * @return Decrypted message
     */
    fun decryptMessage(message: Message, key: SecretKey): Message {
        try {
            // If the message has original text and it belongs to the same user, return it
            if (message.originalText != null) {
                message.decryptedText = message.originalText
                return message
            }
            
            // Otherwise, decrypt the encrypted text
            if (message.encryptedText == null || message.iv == null) {
                throw IllegalArgumentException("Message is missing encrypted text or IV")
            }
            
            // Decode the encrypted components
            val encryptedData = cryptoManager.decodeFromBase64(message.encryptedText!!)
            val iv = cryptoManager.decodeFromBase64(message.iv!!)
            
            // Decrypt the message using the AES key
            val decryptedBytes = cryptoManager.decryptAES(
                CryptoManager.EncryptedData(encryptedData, iv),
                key
            )
            
            // Set the decrypted text
            message.decryptedText = String(decryptedBytes)
            return message
        } catch (e: Exception) {
            Log.e("MessageEncryption", "Error decrypting message", e)
            throw e
        }
    }
} 