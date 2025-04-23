package com.example.messagingapp.details

import com.google.firebase.database.IgnoreExtraProperties
import java.io.Serializable

@IgnoreExtraProperties
data class User(
    var id: String? = null,
    var email: String? = null,
    var name: String? = null,
    var publicKey: String? = null,        // Base64 encoded public key for asymmetric encryption
    var newMessageCount: Int = 0
) : Serializable {
    // Default constructor required for Firebase
    constructor() : this(null, null, null, null, 0)
    
    // Get a display name, with fallbacks
    fun getDisplayName(): String {
        return when {
            !name.isNullOrBlank() -> name!!
            !email.isNullOrBlank() -> email!!.substringBefore('@')
            !id.isNullOrBlank() -> "User-${id!!.take(5)}"
            else -> "Unknown User"
        }
    }
    
    // Override toString for better logging
    override fun toString(): String {
        return "User(id=$id, name=$name, email=$email, msgCount=$newMessageCount)"
    }
}
