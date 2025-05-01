package com.example.messagingapp.details

import com.google.firebase.database.IgnoreExtraProperties
import java.io.Serializable

@IgnoreExtraProperties
data class User(
    var uid: String? = null,
    var email: String? = null,
    var name: String? = null,
    var lastSeen: Long = 0,
    var newMessageCount: Int = 0
) : Serializable {
    // Required empty constructor for Firebase
    constructor() : this(null, null, null, 0, 0)
    
    // Get a display name, with fallbacks
    fun getDisplayName(): String {
        return when {
            !name.isNullOrBlank() -> name!!
            !email.isNullOrBlank() -> email!!.substringBefore('@')
            !uid.isNullOrBlank() -> "User-${uid!!.take(5)}"
            else -> "Unknown User"
        }
    }
    
    // Override toString for better logging
    override fun toString(): String {
        return "User(uid=$uid, name=$name, email=$email, msgCount=$newMessageCount)"
    }
}
