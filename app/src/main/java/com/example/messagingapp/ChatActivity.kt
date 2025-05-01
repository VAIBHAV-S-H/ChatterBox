package com.example.messagingapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messagingapp.crypto.CryptoManager
import com.example.messagingapp.crypto.MessageEncryption
import com.example.messagingapp.details.Message
import com.example.messagingapp.details.MessageAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatActivity"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageList: MutableList<Message>
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var chatId: String
    private lateinit var chatHeader: TextView
    private lateinit var cryptoManager: CryptoManager
    private lateinit var messageEncryption: MessageEncryption
    private lateinit var chatKey: SecretKey
    
    private var userId: String? = null
    private var userName: String? = null

    @SuppressLint("SetTextI18s")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        try {
            auth = FirebaseAuth.getInstance()
            database = FirebaseDatabase.getInstance().getReference("chats")
            userId = intent.getStringExtra("userId")
            userName = intent.getStringExtra("userName")
            
            if (auth.currentUser == null || userId.isNullOrEmpty()) {
                Log.e(TAG, "Missing user information: currentUser=${auth.currentUser}, userId=$userId")
                Toast.makeText(this, "Error: Missing user information", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            // Initialize crypto components
            cryptoManager = CryptoManager()
            messageEncryption = MessageEncryption(cryptoManager)
            
            // Generate a unique chat ID for these two users
            chatId = if (auth.currentUser!!.uid < userId!!) {
                "${auth.currentUser!!.uid}_${userId}"
            } else {
                "${userId}_${auth.currentUser!!.uid}"
            }
            
            // Generate a consistent key based on the chat ID
            chatKey = generateKeyFromChatId(chatId)
            
            Log.d(TAG, "Starting chat with ID: $chatId")
            Log.d(TAG, "Chat with user: $userId, name: $userName")
    
            chatHeader = findViewById(R.id.chatHeader)
            chatHeader.text = "Chat with ${userName ?: "User"}"
            
            messageInput = findViewById(R.id.messageInput)
            sendButton = findViewById(R.id.sendButton)
            recyclerView = findViewById(R.id.recyclerView)
            
            messageList = mutableListOf()
            messageAdapter = MessageAdapter(messageList, auth.currentUser!!.uid)
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = messageAdapter
            
            sendButton.setOnClickListener {
                sendMessage()
            }
            
            listenForMessages()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error initializing chat: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // Generate a consistent encryption key based on the chat ID
    private fun generateKeyFromChatId(chatId: String): SecretKey {
        try {
            // Use SHA-256 to get consistent 32 bytes (256 bits) from the chat ID
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(chatId.toByteArray())
            
            // Create an AES key from the hash
            return SecretKeySpec(hash, "AES")
        } catch (e: Exception) {
            Log.e(TAG, "Error generating key from chat ID", e)
            // Fallback to a random key if hash fails
            return cryptoManager.generateAESKey()
        }
    }

    private fun sendMessage() {
        val messageText = messageInput.text.toString()
        if (!TextUtils.isEmpty(messageText)) {
            try {
                // Create message with original text for admin viewing
                val messageToSend = Message(
                    senderId = auth.currentUser!!.uid,
                    encryptedText = null, // Will be set by encryptMessage
                    originalText = messageText, // Store original text for admin viewing
                    iv = null, // Will be set by encryptMessage
                    timestamp = System.currentTimeMillis()
                )
                
                // Encrypt the message
                val encryptedMessage = messageEncryption.encryptMessage(
                    message = messageToSend,
                    key = chatKey
                )
                
                // Set the decrypted text for display (only for sent messages)
                encryptedMessage.decryptedText = messageText
                
                Log.d(TAG, "Sending encrypted message with original text stored")
                
                // Add the message to the database
                val messageRef = database.child(chatId).push()
                messageRef.setValue(encryptedMessage)
                    .addOnSuccessListener {
                        Log.d(TAG, "Message sent successfully with key: ${messageRef.key}")
                        messageInput.setText("")
                        incrementNewMessageCount()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to send message", e)
                        Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun listenForMessages() {
        Log.d(TAG, "Starting to listen for messages in chat: $chatId")
        
        // Clear any existing messages
        messageList.clear()
        messageAdapter.updateList(ArrayList(messageList))
        
        database.child(chatId).addChildEventListener(object : ChildEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    Log.d(TAG, "New message received: ${snapshot.key}")
                    val message = snapshot.getValue(Message::class.java)
                    
                    if (message != null) {
                        try {
                            // Check if this is your own message with original text
                            if (message.senderId == auth.currentUser!!.uid && message.originalText != null) {
                                // For own messages, use the original text
                                message.decryptedText = message.originalText
                                messageList.add(message)
                                Log.d(TAG, "Using original text for own message")
                            }
                            // Otherwise, try to decrypt the message
                            else if (message.encryptedText != null && message.iv != null) {
                                val decryptedMessage = messageEncryption.decryptMessage(message, chatKey)
                                messageList.add(decryptedMessage)
                                Log.d(TAG, "Message decrypted successfully")
                            } else {
                                // If message has missing components, just display as is
                                message.decryptedText = message.originalText ?: "Message cannot be displayed"
                                messageList.add(message)
                                Log.e(TAG, "Message missing encrypted data components")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error decrypting message", e)
                            // Set a placeholder text for failed decryption
                            message.decryptedText = "Message cannot be decrypted"
                            messageList.add(message)
                        }
                        
                        runOnUiThread {
                            messageAdapter.updateList(ArrayList(messageList))
                            scrollToBottom()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing message", e)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database error: ${error.message}")
            }
        })
    }

    private fun scrollToBottom() {
        if (messageList.isNotEmpty()) {
            recyclerView.post {
                recyclerView.smoothScrollToPosition(messageList.size - 1)
            }
        }
    }

    private fun incrementNewMessageCount() {
        try {
            // Update the new message count for the recipient
            if (userId != null) {
                val recipientRef = FirebaseDatabase.getInstance().reference
                    .child("users")
                    .child(userId!!)
                    .child("newMessageCount")
                
                recipientRef.get().addOnSuccessListener { snapshot ->
                    val currentCount = snapshot.getValue(Int::class.java) ?: 0
                    recipientRef.setValue(currentCount + 1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating message count", e)
        }
    }
}
