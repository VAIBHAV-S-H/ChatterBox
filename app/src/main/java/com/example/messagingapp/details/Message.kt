package com.example.messagingapp.details

import com.google.firebase.database.IgnoreExtraProperties
import java.io.Serializable

@IgnoreExtraProperties
data class Message(
    var senderId: String? = null,
    var encryptedText: String? = null,  // Base64 encoded encrypted text
    var originalText: String? = null,   // Original unencrypted text (for admin viewing)
    var iv: String? = null,             // Base64 encoded IV for AES
    var timestamp: Long = 0,
    @Transient var decryptedText: String? = null  // Not stored in Firebase, used locally
) : Serializable {
    // Default constructor required for Firebase
    constructor() : this(null, null, null, null, 0)
    
    // Special constructor for creating message with both original and encrypted text
    constructor(senderId: String?, encryptedText: String?, originalText: String?, iv: String?, timestamp: Long)
            : this(senderId, encryptedText, originalText, iv, timestamp, null)
    
    override fun toString(): String {
        return "Message(senderId=$senderId, encryptedText=$encryptedText, originalText=$originalText, timestamp=$timestamp)"
    }
}
