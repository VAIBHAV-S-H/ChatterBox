package com.example.messagingapp.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.messagingapp.ChatActivity
import com.example.messagingapp.HomeActivity
import com.example.messagingapp.R
import com.example.messagingapp.details.User
import com.example.messagingapp.details.UserConnection
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder

class AddFriendDialog(private val context: Context) {
    companion object {
        private const val TAG = "AddFriendDialog"
    }

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference
    private val usersRef = database.child("users")
    private val connectionsRef = database.child("connections")

    fun show() {
        try {
            val options = arrayOf("Show My Friend Code", "Enter Friend Code")
            
            AlertDialog.Builder(context)
                .setTitle("Connect with Friend")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showMyFriendCode()
                        1 -> enterFriendCode()
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing dialog", e)
            Toast.makeText(context, "Error: Unable to show dialog", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMyFriendCode() {
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Toast.makeText(context, "You must be logged in to share your code", Toast.LENGTH_SHORT).show()
                return
            }
            
            val friendCode = generateFriendCode(currentUser.uid)
    
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_friend_code, null)
            val qrCodeImageView = dialogView.findViewById<ImageView>(R.id.qrCodeImageView)
            val friendCodeTextView = dialogView.findViewById<TextView>(R.id.friendCodeTextView)
    
            try {
                val multiFormatWriter = MultiFormatWriter()
                val bitMatrix = multiFormatWriter.encode(
                    friendCode,
                    BarcodeFormat.QR_CODE,
                    350,
                    350
                )
                val barcodeEncoder = BarcodeEncoder()
                val bitmap: Bitmap = barcodeEncoder.createBitmap(bitMatrix)
                qrCodeImageView.setImageBitmap(bitmap)
                friendCodeTextView.text = friendCode
            } catch (e: Exception) {
                Log.e(TAG, "Error generating QR code", e)
                friendCodeTextView.text = friendCode
            }
    
            AlertDialog.Builder(context)
                .setTitle("Your Friend Code")
                .setView(dialogView)
                .setPositiveButton("Copy Code") { _, _ ->
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Friend Code", friendCode)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Friend code copied to clipboard", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error copying to clipboard", e)
                        Toast.makeText(context, "Failed to copy code", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Close", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing QR code", e)
            Toast.makeText(context, "Error showing friend code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enterFriendCode() {
        try {
            val input = EditText(context)
            input.hint = "XXX-XXX-XXX-XXX"
            input.filters = arrayOf(android.text.InputFilter.AllCaps(), android.text.InputFilter.LengthFilter(15))
    
            AlertDialog.Builder(context)
                .setTitle("Enter Friend Code")
                .setView(input)
                .setPositiveButton("Connect") { _, _ ->
                    val friendCode = input.text.toString().trim()
                    if (friendCode.isNotEmpty()) {
                        connectWithFriendCode(friendCode)
                    } else {
                        Toast.makeText(context, "Please enter a valid friend code", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing code entry dialog", e)
            Toast.makeText(context, "Error: Unable to enter friend code", Toast.LENGTH_SHORT).show()
        }
    }

    // Generate a consistent friend code from a user ID
    private fun generateFriendCode(userId: String): String {
        val paddedId = if (userId.length < 12) {
            userId.padEnd(12, '0')
        } else {
            userId.substring(0, 12)
        }
        return paddedId.chunked(3).joinToString("-").uppercase()
    }

    // Decode a friend code back to a user ID
    private fun decodeFriendCode(friendCode: String): String {
        return friendCode.replace("-", "").lowercase()
    }

    private fun connectWithFriendCode(friendCode: String) {
        try {
            val cleanedCode = decodeFriendCode(friendCode)
            Log.d(TAG, "Looking up user with decoded friend code: $cleanedCode")
            
            val currentUserId = auth.currentUser?.uid
            if (currentUserId == null) {
                Toast.makeText(context, "You must be logged in to connect with others", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (currentUserId.lowercase() == cleanedCode) {
                Toast.makeText(context, "Cannot add yourself as a friend", Toast.LENGTH_SHORT).show()
                return
            }
            
            // First try a direct lookup by UID
            usersRef.child(cleanedCode).get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        Log.d(TAG, "Found user by direct UID lookup")
                        val user = snapshot.getValue(User::class.java)
                        if (user != null) {
                            showUserConnectionConfirmation(user)
                        } else {
                            Toast.makeText(context, "Error: Invalid user data", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // If direct lookup fails, try searching all users
                        Log.d(TAG, "Direct lookup failed, trying to search all users")
                        searchAllUsers(cleanedCode)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Database error in direct lookup", e)
                    Toast.makeText(context, "Failed to connect with user", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing friend code", e)
            Toast.makeText(context, "Error connecting with user", Toast.LENGTH_SHORT).show()
        }
    }

    private fun searchAllUsers(cleanedCode: String) {
        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var foundUser: User? = null
                
                // Log all users for debugging
                Log.d(TAG, "Searching through all users (${snapshot.childrenCount}):")
                
                for (userSnapshot in snapshot.children) {
                    val user = userSnapshot.getValue(User::class.java)
                    Log.d(TAG, "Checking user: ${user?.uid}")
                    
                    if (user != null) {
                        // Check if the user ID matches the cleaned code
                        if (user.uid?.lowercase() == cleanedCode || 
                            decodeFriendCode(generateFriendCode(user.uid ?: "")) == cleanedCode) {
                            foundUser = user
                            break
                        }
                    }
                }
                
                if (foundUser != null) {
                    Log.d(TAG, "Found user by full search: ${foundUser.uid}")
                    showUserConnectionConfirmation(foundUser)
                } else {
                    Log.e(TAG, "User not found with code: $cleanedCode")
                    Toast.makeText(context, "User not found. Please check the friend code.", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database error in full search", error.toException())
                Toast.makeText(context, "Failed to search for user", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showUserConnectionConfirmation(user: User) {
        try {
            AlertDialog.Builder(context)
                .setTitle("Connect with User")
                .setMessage("Would you like to connect with ${user.name ?: "Unknown User"}?\n\nEmail: ${user.email ?: "No email"}")
                .setPositiveButton("Connect") { _, _ ->
                    try {
                        // Create a bi-directional connection between users
                        createConnection(auth.currentUser!!.uid, user.uid!!)
                        Toast.makeText(context, "Connected with ${user.name ?: "User"}", Toast.LENGTH_SHORT).show()
                        
                        // Verify connections were created by directly querying Firebase
                        Handler(Looper.getMainLooper()).postDelayed({
                            verifyConnectionsCreated(auth.currentUser!!.uid, user.uid!!)
                        }, 1000)  // Wait 1 second before checking
                        
                        // Open chat with this user
                        val intent = Intent(context, ChatActivity::class.java)
                        intent.putExtra("userId", user.uid)
                        intent.putExtra("userName", user.name)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error establishing connection", e)
                        Toast.makeText(context, "Error connecting with user", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing confirmation dialog", e)
            Toast.makeText(context, "Error connecting with user", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Create a bi-directional connection between two users
    private fun createConnection(userId1: String, userId2: String) {
        val timestamp = System.currentTimeMillis()
        
        // Connection from user1 to user2
        val connection1 = UserConnection(
            userId = userId1,
            friendId = userId2,
            timestamp = timestamp,
            status = "active"
        )
        
        // Connection from user2 to user1
        val connection2 = UserConnection(
            userId = userId2,
            friendId = userId1,
            timestamp = timestamp,
            status = "active"
        )
        
        // Create a unique ID for each connection
        val connectionId1 = "${userId1}_${userId2}"
        val connectionId2 = "${userId2}_${userId1}"
        
        Log.d(TAG, "Creating connection with IDs: $connectionId1 and $connectionId2")
        Log.d(TAG, "Connection ref path: ${connectionsRef.toString()}")
        
        // Save connections with completion listeners to ensure they are saved
        connectionsRef.child(connectionId1).setValue(connection1)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully saved connection from $userId1 to $userId2")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save connection from $userId1 to $userId2: ${e.message}")
                Toast.makeText(context, "Error saving connection", Toast.LENGTH_SHORT).show()
            }
        
        connectionsRef.child(connectionId2).setValue(connection2)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully saved connection from $userId2 to $userId1")
                
                // Trigger an immediate refresh by forcing the app to update the view
                if (context is Activity) {
                    try {
                        // Notify that data has changed
                        Toast.makeText(context, "Connection successful! Refreshing...", Toast.LENGTH_SHORT).show()
                        
                        // Force a UI reload when returning to HomeActivity
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (context is HomeActivity) {
                                (context as HomeActivity).loadUsers()
                            }
                        }, 500)  // Small delay to ensure the data is written
                    } catch (e: Exception) {
                        Log.e(TAG, "Error triggering refresh", e)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save connection from $userId2 to $userId1: ${e.message}")
                Toast.makeText(context, "Error saving reverse connection", Toast.LENGTH_SHORT).show()
            }
        
        Log.d(TAG, "Created bi-directional connection between $userId1 and $userId2")
    }

    private fun verifyConnectionsCreated(userId1: String, userId2: String) {
        // Create the connection IDs
        val connectionId1 = "${userId1}_${userId2}"
        val connectionId2 = "${userId2}_${userId1}"
        
        Log.d(TAG, "Verifying connections with IDs: $connectionId1 and $connectionId2")
        
        // Check if the first connection exists
        connectionsRef.child(connectionId1).get()
            .addOnSuccessListener { dataSnapshot ->
                if (dataSnapshot.exists()) {
                    Log.d(TAG, "Connection $connectionId1 verified - EXISTS in Firebase!")
                    val connection = dataSnapshot.getValue(UserConnection::class.java)
                    Log.d(TAG, "Connection data: $connection")
                } else {
                    Log.e(TAG, "Connection $connectionId1 MISSING from Firebase!")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error verifying connection $connectionId1: ${e.message}")
            }
        
        // Check if the second connection exists
        connectionsRef.child(connectionId2).get()
            .addOnSuccessListener { dataSnapshot ->
                if (dataSnapshot.exists()) {
                    Log.d(TAG, "Connection $connectionId2 verified - EXISTS in Firebase!")
                    val connection = dataSnapshot.getValue(UserConnection::class.java)
                    Log.d(TAG, "Connection data: $connection")
                } else {
                    Log.e(TAG, "Connection $connectionId2 MISSING from Firebase!")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error verifying connection $connectionId2: ${e.message}")
            }
        
        // Also check if we can find it in a general query
        connectionsRef.orderByChild("userId").equalTo(userId1)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d(TAG, "Found ${snapshot.childrenCount} connections for userId $userId1")
                    for (connectionSnapshot in snapshot.children) {
                        val connection = connectionSnapshot.getValue(UserConnection::class.java)
                        Log.d(TAG, "Connection from query: $connection")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Database error verifying connections: ${error.message}")
                }
            })
    }
} 