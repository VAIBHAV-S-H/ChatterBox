package com.example.messagingapp.admin

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class AdminManager {
    companion object {
        const val ADMIN_EMAIL = "vaibhavninja1234@gmail.com"
        private const val ADMIN_NODE = "admin"
    }

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun isCurrentUserAdmin(): Boolean {
        return auth.currentUser?.email == ADMIN_EMAIL
    }

    fun resetDatabase(onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (!isCurrentUserAdmin()) {
            onError(SecurityException("Only admin can reset database"))
            return
        }

        val rootRef = database.reference
        rootRef.removeValue()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun deleteUser(userId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        if (!isCurrentUserAdmin()) {
            onError(SecurityException("Only admin can delete users"))
            return
        }

        // Delete user data from Realtime Database
        database.reference.child("users").child(userId).removeValue()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun getAllUsersActivity(onSuccess: (List<UserActivity>) -> Unit, onError: (Exception) -> Unit) {
        if (!isCurrentUserAdmin()) {
            onError(SecurityException("Only admin can view all activity"))
            return
        }

        database.reference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val activities = mutableListOf<UserActivity>()
                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key ?: continue
                    val lastSeen = userSnapshot.child("lastSeen").getValue(Long::class.java) ?: 0
                    val email = userSnapshot.child("email").getValue(String::class.java) ?: ""
                    activities.add(UserActivity(userId, email, lastSeen))
                }
                onSuccess(activities)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.toException())
            }
        })
    }

    data class UserActivity(
        val userId: String,
        val email: String,
        val lastSeen: Long
    )
} 