package com.example.messagingapp.details

import com.google.firebase.database.IgnoreExtraProperties
import java.io.Serializable

@IgnoreExtraProperties
data class Message(
    var senderId: String? = null,
    var text: String? = null,
    var timestamp: Long = 0
) : Serializable {
    // Default constructor required for Firebase
    constructor() : this(null, null, 0)
    
    override fun toString(): String {
        return "Message(senderId=$senderId, text=$text, timestamp=$timestamp)"
    }
}
