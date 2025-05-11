package com.example.messagingapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messagingapp.admin.AdminManager
import com.example.messagingapp.details.User
import com.example.messagingapp.dialogs.AddFriendDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class UserListActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "UserListActivity"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var recyclerView: RecyclerView
    private lateinit var userList: MutableList<User>
    private lateinit var addFriendButton: FloatingActionButton
    private var userAdapter: UserAdapter? = null
    private var addFriendDialog: AddFriendDialog? = null
    private lateinit var adminManager: AdminManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_user_list)
    
            // Set up the action bar
            setSupportActionBar(findViewById(R.id.toolbar))
            supportActionBar?.title = "Messaging App"
    
            auth = FirebaseAuth.getInstance()
            adminManager = AdminManager()
            
            // Verify user is logged in
            if (auth.currentUser == null) {
                Log.e(TAG, "User not logged in, redirecting to MainActivity")
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return
            }
            
            database = FirebaseDatabase.getInstance().reference
    
            recyclerView = findViewById(R.id.recyclerViewUsers)
            addFriendButton = findViewById(R.id.fabAddFriend)
            userList = mutableListOf()
    
            setupRecyclerView()
            setupAddFriendButton()
            loadConnectedUsers()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error initializing: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Refresh connections when returning to this activity
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Refreshing user connections")
        loadConnectedUsers()
    }

    private fun setupRecyclerView() {
        try {
            recyclerView.layoutManager = LinearLayoutManager(this)
            userAdapter = UserAdapter(userList) { user ->
                startChat(user)
            }
            recyclerView.adapter = userAdapter
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up RecyclerView", e)
        }
    }

    private fun setupAddFriendButton() {
        try {
            addFriendButton.setOnClickListener {
                try {
                    showAddFriendDialog()
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing add friend dialog", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up add friend button", e)
        }
    }

    private fun showAddFriendDialog() {
        try {
            addFriendDialog = AddFriendDialog(this)
            addFriendDialog?.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in showAddFriendDialog", e)
            Toast.makeText(this, "Cannot open dialog: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadConnectedUsers() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            try {
                val connectionsRef = FirebaseDatabase.getInstance().getReference("connections")
                val connectedUserIds = mutableSetOf<String>()
                
                // First check connections where current user is userId
                connectionsRef.orderByChild("userId").equalTo(currentUser.uid)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            // Process each connection
                            for (connectionSnapshot in snapshot.children) {
                                val connection = connectionSnapshot.getValue(com.example.messagingapp.details.UserConnection::class.java)
                                if (connection != null && connection.status == "active") {
                                    // Add the friend's ID to our set
                                    connection.friendId?.let { connectedUserIds.add(it) }
                                }
                            }
                            
                            Log.d(TAG, "Found ${connectedUserIds.size} connections where user is userId")
                            
                            // Now check connections where current user is friendId
                            connectionsRef.orderByChild("friendId").equalTo(currentUser.uid)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(friendSnapshot: DataSnapshot) {
                                        // Process each connection
                                        for (connectionSnapshot in friendSnapshot.children) {
                                            val connection = connectionSnapshot.getValue(com.example.messagingapp.details.UserConnection::class.java)
                                            if (connection != null && connection.status == "active") {
                                                // Add the user's ID to our set
                                                connection.userId?.let { connectedUserIds.add(it) }
                                            }
                                        }
                                        
                                        Log.d(TAG, "Total connections found: ${connectedUserIds.size}")
                                        
                                        // Now fetch the actual user data for connected users
                                        loadUserDetailsForConnections(connectedUserIds)
                                    }
                                    
                                    override fun onCancelled(error: DatabaseError) {
                                        Log.e(TAG, "Database error loading friendId connections: ${error.message}")
                                        Toast.makeText(this@UserListActivity, "Error loading connections", Toast.LENGTH_SHORT).show()
                                    }
                                })
                        }
                        
                        override fun onCancelled(error: DatabaseError) {
                            Log.e(TAG, "Database error loading userId connections: ${error.message}")
                            Toast.makeText(this@UserListActivity, "Error loading connections", Toast.LENGTH_SHORT).show()
                        }
                    })
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up database listener", e)
                Toast.makeText(this, "Error connecting to database", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "Current user is null in loadConnectedUsers")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
    
    private fun loadUserDetailsForConnections(connectedUserIds: Set<String>) {
        try {
            if (connectedUserIds.isEmpty()) {
                userList.clear()
                userAdapter?.notifyDataSetChanged()
                return
            }
            
            database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        userList.clear()
                        
                        // Only add users who are in our connectedUserIds set
                        for (userSnapshot in snapshot.children) {
                            val userId = userSnapshot.key
                            if (userId != null && connectedUserIds.contains(userId)) {
                                val user = userSnapshot.getValue(User::class.java)
                                if (user != null) {
                                    // Ensure all required fields are populated
                                    if (user.uid.isNullOrEmpty()) {
                                        user.uid = userId
                                    }
                                    if (user.name.isNullOrEmpty()) {
                                        user.name = user.getDisplayName()
                                    }
                                    userList.add(user)
                                    Log.d(TAG, "Added connected user to display list: ${user.name}")
                                }
                            }
                        }
                        
                        userAdapter?.notifyDataSetChanged()
                        Log.d(TAG, "Loaded ${userList.size} connected users")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing user data", e)
                        Toast.makeText(this@UserListActivity, "Error loading users", Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Database error: ${error.message}", error.toException())
                    Toast.makeText(this@UserListActivity, "Error loading users: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user details", e)
            Toast.makeText(this@UserListActivity, "Error loading user details", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startChat(user: User) {
        try {
            // Extra validation to prevent "Invalid user selected" errors
            val validUserId = if (user.uid.isNullOrEmpty()) {
                Log.e(TAG, "User ID is null or empty, cannot start chat")
                Toast.makeText(this, "Cannot chat with this user (missing ID)", Toast.LENGTH_SHORT).show()
                return
            } else {
                user.uid!!
            }
            
            val userName = if (user.name.isNullOrEmpty()) {
                user.email?.substringBefore('@') ?: "Unknown User"
            } else {
                user.name!!
            }
            
            Log.d(TAG, "Starting chat with user: $validUserId, name: $userName")
            
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("userId", validUserId)
                putExtra("userName", userName)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting chat", e)
            Toast.makeText(this, "Cannot start chat: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        try {
            menuInflater.inflate(R.menu.menu_user_list, menu)
            
            // Add admin-only options if the current user is an admin
            if (adminManager.isCurrentUserAdmin()) {
                menu.add(Menu.NONE, 9999, Menu.NONE, "Reset Database")
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating options menu", e)
            return false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return try {
            when (item.itemId) {
                R.id.action_logout -> {
                    // Detach database listeners
                    FirebaseDatabase.getInstance().getReference("connections").keepSynced(false)
                    database.keepSynced(false)
                    
                    // Clear auth state
                    auth.signOut()
                    
                    // Redirect to main activity
                    startActivity(Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                    true
                }
                9999 -> {
                    // Admin-only: Reset database
                    if (adminManager.isCurrentUserAdmin()) {
                        resetDatabase()
                    } else {
                        Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling menu item selection", e)
            false
        }
    }
    
    private fun resetDatabase() {
        if (adminManager.isCurrentUserAdmin()) {
            database.child("users").removeValue()
                .addOnSuccessListener {
                    Toast.makeText(this, "Database has been reset", Toast.LENGTH_SHORT).show()
                    
                    // Make sure to add current user back
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        val newUser = User(
                            uid = currentUser.uid,
                            email = currentUser.email,
                            name = currentUser.displayName ?: "User"
                        )
                        database.child("users").child(currentUser.uid).setValue(newUser)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to reset database: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "Only admin can reset the database", Toast.LENGTH_SHORT).show()
        }
    }
} 