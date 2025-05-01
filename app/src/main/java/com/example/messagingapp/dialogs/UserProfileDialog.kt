package com.example.messagingapp.dialogs

import android.app.AlertDialog
import android.content.Context
import com.example.messagingapp.details.User

class UserProfileDialog(context: Context, private val user: User) : AlertDialog(context) {

    override fun show() {
        val message = buildString {
            appendLine("Email: ${user.email ?: "Not available"}")
            appendLine("\nUsername: ${user.name ?: user.email?.substringBefore('@') ?: "Not available"}")
            appendLine("\nUID: ${user.uid ?: "Not available"}")
            appendLine("\nLast seen: ${if (user.lastSeen > 0) java.util.Date(user.lastSeen) else "Never"}")
        }

        setTitle("User Profile")
        setMessage(message)
        setButton(BUTTON_POSITIVE, "OK") { dialog, _ -> dialog.dismiss() }

        super.show()
    }
} 