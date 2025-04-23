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
import com.example.messagingapp.details.Message
import com.example.messagingapp.details.MessageAdapter
import com.example.messagingapp.details.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ChatActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageList: MutableList<Message>
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var chatId: String
    private lateinit var chatHeader: TextView
    
    private var userId: String? = null
    private var userName: String? = null

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("chats")
        userId = intent.getStringExtra("userId")
        userName = intent.getStringExtra("userName")
        
        // Generate a unique chat ID for these two users
        chatId = if (auth.currentUser!!.uid < userId!!) {
            "${auth.currentUser!!.uid}_${userId}"
        } else {
            "${userId}_${auth.currentUser!!.uid}"
        }
        
        Log.d("ChatActivity", "Starting chat with ID: $chatId")
        Log.d("ChatActivity", "Chat with user: $userId, name: $userName")

        chatHeader = findViewById(R.id.chatHeader)
        
        // Ensure username is not blank by checking all possible sources
        if (userName.isNullOrBlank()) {
            // If username is blank, try to load it from database
            val usersRef = FirebaseDatabase.getInstance().getReference("users")
            usersRef.child(userId!!).get().addOnSuccessListener { userSnapshot ->
                val user = userSnapshot.getValue(User::class.java)
                if (user != null && !user.name.isNullOrBlank()) {
                    userName = user.name
                    chatHeader.text = "Chatting with $userName"
                    Log.d("ChatActivity", "Updated username from database: $userName")
                } else {
                    // If still blank, use email or user ID as fallback
                    userName = user?.email ?: "User-${userId!!.take(5)}"
                    chatHeader.text = "Chatting with $userName"
                    Log.d("ChatActivity", "Using fallback username: $userName")
                }
            }.addOnFailureListener { e ->
                Log.e("ChatActivity", "Error getting user data", e)
                // Fallback if database fails
                userName = "User-${userId!!.take(5)}"
                chatHeader.text = "Chatting with $userName"
            }
        } else {
            chatHeader.text = "Chatting with $userName"
        }

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
    }

    private fun sendMessage() {
        val messageText = messageInput.text.toString()
        if (!TextUtils.isEmpty(messageText)) {
            try {
                // Create a simple message without encryption
                val message = Message(
                    senderId = auth.currentUser!!.uid,
                    text = messageText,
                    timestamp = System.currentTimeMillis()
                )
                
                Log.d("ChatActivity", "Sending message: $message")
                
                // Add the message to the database
                val messageRef = database.child(chatId).push()
                messageRef.setValue(message)
                    .addOnSuccessListener {
                        Log.d("ChatActivity", "Message sent successfully with key: ${messageRef.key}")
                        messageInput.setText("")
                        
                        // Don't add to local list anymore - the listener will handle it
                        // This prevents duplicate messages
                        
                        incrementNewMessageCount()
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatActivity", "Failed to send message", e)
                        Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Error sending message", e)
                Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun listenForMessages() {
        Log.d("ChatActivity", "Starting to listen for messages in chat: $chatId")
        
        // Clear any existing messages
        messageList.clear()
        messageAdapter.updateList(ArrayList(messageList))
        
        database.child(chatId).addChildEventListener(object : ChildEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                try {
                    Log.d("ChatActivity", "New message received: ${snapshot.key}")
                    val message = snapshot.getValue(Message::class.java)
                    
                    if (message != null) {
                        Log.d("ChatActivity", "Message details - Sender: ${message.senderId}, Text: ${message.text}")
                        
                        // Add to the list and update the adapter
                        messageList.add(message)
                        runOnUiThread {
                            messageAdapter.updateList(ArrayList(messageList))
                            scrollToBottom()
                        }
                        
                        // Mark messages as read if they're not from us
                        if (message.senderId != auth.currentUser!!.uid) {
                            Log.d("ChatActivity", "Message is from other user, marking as read")
                            markMessagesAsRead()
                        }
                    } else {
                        Log.e("ChatActivity", "Failed to parse message from snapshot: ${snapshot.value}")
                    }
                } catch (e: Exception) {
                    Log.e("ChatActivity", "Error processing message", e)
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                Log.d("ChatActivity", "Message changed: ${snapshot.key}")
            }
            
            override fun onChildRemoved(snapshot: DataSnapshot) {
                Log.d("ChatActivity", "Message removed: ${snapshot.key}")
            }
            
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                Log.d("ChatActivity", "Message moved: ${snapshot.key}")
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatActivity", "Database error: ${error.message}")
            }
        })
    }

    private fun markMessagesAsRead() {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(auth.currentUser!!.uid)
        userRef.child("newMessageCount").setValue(0)
    }

    private fun incrementNewMessageCount() {
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId!!)
        userRef.child("newMessageCount").get().addOnSuccessListener {
            val currentCount = it.getValue(Int::class.java) ?: 0
            userRef.child("newMessageCount").setValue(currentCount + 1)
        }
    }

    private fun scrollToBottom() {
        if (messageList.isNotEmpty()) {
            recyclerView.post {
                recyclerView.smoothScrollToPosition(messageList.size - 1)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Ensure the adapter has the latest data
        if (::messageAdapter.isInitialized && ::messageList.isInitialized) {
            messageAdapter.updateList(ArrayList(messageList))
        }
    }

    override fun onStart() {
        super.onStart()
        
        // Load any existing messages
        loadInitialMessages()
    }

    private fun loadInitialMessages() {
        Log.d("ChatActivity", "Loading initial messages from chat: $chatId")
        database.child(chatId).get().addOnSuccessListener { snapshot ->
            Log.d("ChatActivity", "Got ${snapshot.childrenCount} messages from database")
            
            messageList.clear()
            
            snapshot.children.forEach { messageSnapshot ->
                val message = messageSnapshot.getValue(Message::class.java)
                if (message != null) {
                    Log.d("ChatActivity", "Loaded message: $message")
                    messageList.add(message)
                } else {
                    Log.e("ChatActivity", "Could not parse message: ${messageSnapshot.value}")
                }
            }
            
            // Sort messages by timestamp
            messageList.sortBy { it.timestamp }
            
            // Update the adapter
            messageAdapter.updateList(ArrayList(messageList))
            scrollToBottom()
        }.addOnFailureListener { e ->
            Log.e("ChatActivity", "Failed to load initial messages", e)
        }
    }
}
