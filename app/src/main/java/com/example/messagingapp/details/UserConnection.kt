package com.example.messagingapp.details

import com.google.firebase.database.IgnoreExtraProperties
import java.io.Serializable

/**
 * Represents a connection between two users
 */
@IgnoreExtraProperties
data class UserConnection(
    var userId: String? = null,      // The main user's ID
    var friendId: String? = null,    // The connected friend's ID
    var timestamp: Long = 0,         // When the connection was established
    var status: String = "active"    // Status: active, blocked, etc.
) : Serializable {
    // Required empty constructor for Firebase
    constructor() : this(null, null, 0, "active")
    
    // Override toString for better logging
    override fun toString(): String {
        return "UserConnection(userId=$userId, friendId=$friendId, status=$status)"
    }
} 