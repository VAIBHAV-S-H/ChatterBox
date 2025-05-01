package com.example.messagingapp.friends

import android.graphics.Bitmap
import android.graphics.Color
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.security.SecureRandom
import java.util.*

class FriendConnection {
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val FRIEND_CODE_LENGTH = 8
        private const val FRIEND_CODES_PATH = "friend_codes"
        private const val CONNECTIONS_PATH = "connections"
    }
    
    /**
     * Generate a unique friend code for the current user
     */
    fun generateFriendCode(onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        
        val random = SecureRandom()
        val code = StringBuilder()
        repeat(FRIEND_CODE_LENGTH) {
            code.append((random.nextInt(36)).toString(36).uppercase())
        }
        
        // Store the code in Firebase
        database.reference
            .child(FRIEND_CODES_PATH)
            .child(code.toString())
            .setValue(userId)
            .addOnSuccessListener { onSuccess(code.toString()) }
            .addOnFailureListener { onError(it) }
    }
    
    /**
     * Generate a QR code containing the friend code
     */
    fun generateQRCode(friendCode: String, size: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(friendCode, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        
        return bitmap
    }
    
    /**
     * Connect with a friend using their friend code
     */
    fun connectWithFriend(friendCode: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        database.reference
            .child(FRIEND_CODES_PATH)
            .child(friendCode)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val friendId = snapshot.getValue(String::class.java)
                    if (friendId == null) {
                        onError(Exception("Invalid friend code"))
                        return
                    }
                    
                    // Create bidirectional connection
                    val connections = hashMapOf<String, Any>()
                    connections["/$CONNECTIONS_PATH/$currentUserId/$friendId"] = true
                    connections["/$CONNECTIONS_PATH/$friendId/$currentUserId"] = true
                    
                    database.reference.updateChildren(connections)
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { onError(it) }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    onError(error.toException())
                }
            })
    }
    
    /**
     * Get list of connected friend IDs
     */
    fun getConnectedFriends(onSuccess: (List<String>) -> Unit, onError: (Exception) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return
        
        database.reference
            .child(CONNECTIONS_PATH)
            .child(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val friendIds = snapshot.children.mapNotNull { it.key }
                    onSuccess(friendIds)
                }
                
                override fun onCancelled(error: DatabaseError) {
                    onError(error.toException())
                }
            })
    }
} 