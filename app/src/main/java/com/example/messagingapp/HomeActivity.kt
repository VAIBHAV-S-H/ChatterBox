package com.example.messagingapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messagingapp.admin.AdminEncryptionVisualizerActivity
import com.example.messagingapp.admin.AdminManager
import com.example.messagingapp.crypto.CryptoManager
import com.example.messagingapp.details.User
import com.example.messagingapp.details.UserAdapter
import com.example.messagingapp.dialogs.UserProfileDialog
import com.example.messagingapp.dialogs.AddFriendDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.BarcodeEncoder
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class HomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var userRecyclerView: RecyclerView
    private lateinit var searchBar: EditText
    private lateinit var noUsersText: TextView
    private lateinit var btnAddUsers: Button
    private lateinit var userList: MutableList<User>
    private lateinit var userAdapter: UserAdapter
    private lateinit var adminManager: AdminManager
    private val TAG = "HomeActivity"

    // Keep a cached copy of the current user profile
    private var cachedUserProfile: User? = null

    // Constants for permission requests
    private val CAMERA_PERMISSION_REQUEST_CODE = 100

    // Add a reference to the add friend dialog to be used for permissions
    private var addFriendDialog: AddFriendDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Set up the toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        // Force the overflow icon to be white
        setOverflowButtonColor(toolbar)

        // Initialize Firebase with persistence
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable persistence, may already be enabled: ${e.message}")
        }
        
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("users")
        adminManager = AdminManager()
        
        // Database debug info - important
        database.keepSynced(true)
        Log.d(TAG, "Database reference initialized: ${database.toString()}")
        
        // Output the full path for connections to help debug
        val connectionsRef = FirebaseDatabase.getInstance().getReference("connections")
        Log.d(TAG, "Connections reference path: ${connectionsRef.toString()}")
        
        val userId = auth.currentUser?.uid
        if (userId != null) {
            // Check if any connections exist by listing them directly
            connectionsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d(TAG, "Found ${snapshot.childrenCount} total connections in database")
                    for (connectionSnapshot in snapshot.children) {
                        Log.d(TAG, "Connection key: ${connectionSnapshot.key}")
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error checking connections: ${error.message}")
                }
            })
        }
        
        // Redirect to login if not authenticated
        if (auth.currentUser == null) {
            Log.d(TAG, "User not authenticated, redirecting to login")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        // Make sure current user is in database
        addCurrentUserToDatabase()

        userRecyclerView = findViewById(R.id.userRecyclerView)
        searchBar = findViewById(R.id.searchBar)
        btnAddUsers = findViewById(R.id.btnAddUsers)
        
        // Add a TextView to display when no users are found
        noUsersText = findViewById(R.id.noUsersText)
        
        // Set up the Connect button
        btnAddUsers.setOnClickListener {
            showFriendCodeDialog()
        }
        
        userList = mutableListOf()
        userAdapter = UserAdapter(userList) { user ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("userId", user.uid)
            intent.putExtra("userName", user.name)
            startActivity(intent)
        }

        userRecyclerView.layoutManager = LinearLayoutManager(this)
        userRecyclerView.adapter = userAdapter

        loadUsers()

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterUsers(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu
        menuInflater.inflate(R.menu.home_menu, menu)
        Log.d(TAG, "Menu inflated with ${menu.size()} items")
        
        // Show admin-only items conditionally
        val adminItem = menu.findItem(R.id.action_add_existing_users)
        adminItem.isVisible = adminManager.isCurrentUserAdmin()
        
        // Add admin-only options if the current user is an admin
        if (adminManager.isCurrentUserAdmin()) {
            Log.d(TAG, "Adding admin-only menu items")
            menu.add(Menu.NONE, 9999, Menu.NONE, "Reset Database")
            menu.add(Menu.NONE, 9998, Menu.NONE, "Reset with Test Users")
            menu.add(Menu.NONE, 9997, Menu.NONE, "Admin Dashboard")
        }
        
        // Debug: Print all menu items
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            Log.d(TAG, "Menu item ${i}: id=${item.itemId}, title=${item.title}")
        }
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "Menu item selected: id=${item.itemId}, title=${item.title}")
        
        return when (item.itemId) {
            R.id.action_refresh -> {
                // Manually refresh connections
                Toast.makeText(this, "Refreshing connections...", Toast.LENGTH_SHORT).show()
                loadUsers()
                true
            }
            R.id.action_user_profile -> {
                Log.d(TAG, "User profile menu item clicked")
                showUserProfile()
                true
            }
            R.id.action_logout -> {
                // Log out the user
                Log.d(TAG, "Logout menu item clicked")
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            R.id.action_friend_code -> {
                // Handle friend code exchange
                Log.d(TAG, "Friend code menu item clicked")
                showFriendCodeDialog()
                true
            }
            R.id.action_add_existing_users -> {
                // Add existing authenticated users to the database
                if (adminManager.isCurrentUserAdmin()) {
                    Log.d(TAG, "Add existing users menu item clicked")
                    addExistingUsersToDatabase()
                } else {
                    Log.d(TAG, "Non-admin tried to add existing users")
                    Toast.makeText(this, "Admin access required for this operation", Toast.LENGTH_SHORT).show()
                }
                true
            }
            9999 -> {
                // Clean database - admin only
                if (adminManager.isCurrentUserAdmin()) {
                    cleanDatabase()
                } else {
                    Toast.makeText(this, "Admin access required for this operation", Toast.LENGTH_SHORT).show()
                }
                true
            }
            9998 -> {
                // Reset with test users - admin only
                if (adminManager.isCurrentUserAdmin()) {
                    resetWithTestUsers()
                } else {
                    Toast.makeText(this, "Admin access required for this operation", Toast.LENGTH_SHORT).show()
                }
                true
            }
            9997 -> {
                // Admin dashboard - admin only
                if (adminManager.isCurrentUserAdmin()) {
                    // Open admin dashboard
                    startActivity(Intent(this, AdminEncryptionVisualizerActivity::class.java))
                } else {
                    Toast.makeText(this, "Admin access required for this operation", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> {
                Log.d(TAG, "Unknown menu item clicked: ${item.itemId}")
                super.onOptionsItemSelected(item)
            }
        }
    }
    
    private fun addExistingUsersToDatabase() {
        database.get().addOnSuccessListener { snapshot ->
            for (userSnapshot in snapshot.children) {
                val user = userSnapshot.getValue(User::class.java)
                if (user != null) {
                    val userRef = database.child(user.uid!!)
                    userRef.setValue(user)
                }
            }
        }
    }

    // Override onResume to refresh user connections when returning to this activity
    override fun onResume() {
        super.onResume()
        // Reload the connected users when returning to this screen
        loadUsers()
        Log.d(TAG, "onResume: Refreshing user connections")
    }

    fun loadUsers() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.e(TAG, "Current user ID is null, cannot load connections")
            return
        }
        
        // Start with an empty list
        userList.clear()
        userAdapter.updateList(ArrayList(userList))
        
        // Show loading state
        Toast.makeText(this, "Loading connections...", Toast.LENGTH_SHORT).show()
        
        // Get a list of users that the current user is connected to
        val connectionsRef = FirebaseDatabase.getInstance().getReference("connections")
        
        // Add debugging
        Log.d(TAG, "Database path: ${connectionsRef.toString()}")
        
        // Set to store the connected user IDs
        val connectedUserIds = mutableSetOf<String>()
        
        // Log the current user ID to debug
        Log.d(TAG, "Checking connections for current user: $currentUserId")
        
        // Look for connections where the current user is the userId
        connectionsRef.orderByChild("userId").equalTo(currentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Debug log the snapshot
                    Log.d(TAG, "userId query snapshot count: ${snapshot.childrenCount}")
                    for (child in snapshot.children) {
                        Log.d(TAG, "Connection found with key: ${child.key}")
                    }
                    
                    // Process each connection where current user is userId
                    for (connectionSnapshot in snapshot.children) {
                        val connection = connectionSnapshot.getValue(com.example.messagingapp.details.UserConnection::class.java)
                        Log.d(TAG, "Connection data: $connection")
                        if (connection != null && connection.status == "active") {
                            // Add the friend's ID to our set
                            connection.friendId?.let { 
                                connectedUserIds.add(it)
                                Log.d(TAG, "Added friendId to connections: $it") 
                            }
                        }
                    }
                    
                    Log.d(TAG, "Found ${connectedUserIds.size} connections where user is userId")
                    
                    // Now check for connections where the current user is the friendId
                    connectionsRef.orderByChild("friendId").equalTo(currentUserId)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(friendSnapshot: DataSnapshot) {
                                // Debug log the snapshot
                                Log.d(TAG, "friendId query snapshot count: ${friendSnapshot.childrenCount}")
                                for (child in friendSnapshot.children) {
                                    Log.d(TAG, "Reverse connection found with key: ${child.key}")
                                }
                                
                                // Process each connection where current user is friendId
                                for (connectionSnapshot in friendSnapshot.children) {
                                    val connection = connectionSnapshot.getValue(com.example.messagingapp.details.UserConnection::class.java)
                                    Log.d(TAG, "Reverse connection data: $connection")
                                    if (connection != null && connection.status == "active") {
                                        // Add the user's ID to our set
                                        connection.userId?.let { 
                                            connectedUserIds.add(it) 
                                            Log.d(TAG, "Added userId to connections: $it")
                                        }
                                    }
                                }
                                
                                Log.d(TAG, "Total connections found: ${connectedUserIds.size}, IDs: $connectedUserIds")
                                
                                if (connectedUserIds.isEmpty()) {
                                    // No connections found, update UI
                                    userList.clear()
                                    userAdapter.updateList(ArrayList(userList))
                                    updateNoUsersVisibility()
                                    return
                                }
                                
                                // Now fetch the actual user data for these connected users
                                userList.clear() // Clear again to be safe
                                
                                // Log what we're looking for
                                Log.d(TAG, "Fetching user details for connected IDs: $connectedUserIds")
                                
                                // Create a transaction to get all users at once
                                database.get().addOnSuccessListener { usersSnapshot ->
                                    Log.d(TAG, "Got users database snapshot with ${usersSnapshot.childrenCount} children")
                                    
                                    val tempUserList = mutableListOf<User>()
                                    
                                    // For each connected user ID, find the user data
                                    for (friendId in connectedUserIds) {
                                        val userSnapshot = usersSnapshot.child(friendId)
                                        Log.d(TAG, "Checking for user with ID: $friendId, exists: ${userSnapshot.exists()}")
                                        
                                        if (userSnapshot.exists()) {
                                            val user = userSnapshot.getValue(User::class.java)
                                            if (user != null) {
                                                // Ensure all required fields are populated
                                                if (user.uid.isNullOrEmpty()) {
                                                    user.uid = friendId
                                                }
                                                if (user.name.isNullOrEmpty()) {
                                                    user.name = user.getDisplayName()
                                                }
                                                tempUserList.add(user)
                                                Log.d(TAG, "Added connected user to display list: ${user.name}")
                                            } else {
                                                Log.e(TAG, "User data is null for ID: $friendId")
                                            }
                                        } else {
                                            Log.w(TAG, "User $friendId not found in database")
                                        }
                                    }
                                    
                                    // Update the UI with the complete list
                                    userList.addAll(tempUserList)
                                    userAdapter.updateList(ArrayList(userList))
                                    updateNoUsersVisibility()
                                    
                                    Log.d(TAG, "Loaded ${userList.size} connected users")
                                }.addOnFailureListener { e ->
                                    Log.e(TAG, "Error loading user data: ${e.message}")
                                    Toast.makeText(this@HomeActivity, "Error loading user data", Toast.LENGTH_SHORT).show()
                                    updateNoUsersVisibility()
                                }
                            }
                            
                            override fun onCancelled(error: DatabaseError) {
                                Log.e(TAG, "Database error loading friendId connections: ${error.message}")
                                Toast.makeText(this@HomeActivity, "Error loading connections", Toast.LENGTH_SHORT).show()
                                updateNoUsersVisibility()
                            }
                        })
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Database error loading userId connections: ${error.message}")
                    Toast.makeText(this@HomeActivity, "Error loading connections", Toast.LENGTH_SHORT).show()
                    updateNoUsersVisibility()
                }
            })
    }
    
    private fun updateNoUsersVisibility() {
        if (userAdapter.itemCount == 0) {
            noUsersText.visibility = View.VISIBLE
        } else {
            noUsersText.visibility = View.GONE
        }
    }

    private fun filterUsers(query: String) {
        val filteredList = if (query.isEmpty()) {
            ArrayList(userList)
        } else {
            userList.filter { user ->
                user.name?.contains(query, ignoreCase = true) == true ||
                user.email?.contains(query, ignoreCase = true) == true
            }.toMutableList()
        }
        userAdapter.updateList(filteredList)
        updateNoUsersVisibility()
    }

    // Helper method to force overflow icon to be white
    private fun setOverflowButtonColor(toolbar: Toolbar) {
        try {
            val overflowIcon = toolbar.overflowIcon
            if (overflowIcon != null) {
                overflowIcon.setTint(resources.getColor(android.R.color.white, theme))
                toolbar.overflowIcon = overflowIcon
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting overflow icon color: ${e.message}")
        }
    }

    // Helper method to create a spannable with white text
    private fun createWhiteSpannable(text: String): CharSequence {
        val spannable = android.text.SpannableString(text)
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(android.graphics.Color.WHITE),
            0, spannable.length, 0
        )
        return spannable
    }

    private fun showUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userRef = database.child(currentUser.uid)
            userRef.get().addOnSuccessListener { snapshot ->
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    // Show user profile dialog
                    val dialog = UserProfileDialog(this, user)
                    dialog.show()
                }
            }
        }
    }

    // Add current user to database to ensure they exist
    private fun addCurrentUserToDatabase() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userRef = database.child(currentUser.uid)
            userRef.get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    val newUser = User(
                        uid = currentUser.uid,
                        email = currentUser.email,
                        name = currentUser.displayName
                    )
                    userRef.setValue(newUser)
                }
            }
        }
    }

    // Update the show friend code dialog method
    private fun showFriendCodeDialog() {
        addFriendDialog = AddFriendDialog(this)
        addFriendDialog?.show()
    }

    // Add this new method to clean the database - admin only
    private fun cleanDatabase() {
        if (adminManager.isCurrentUserAdmin()) {
            database.removeValue().addOnSuccessListener {
                Toast.makeText(this, "Database has been reset", Toast.LENGTH_SHORT).show()
                // Make sure to re-add the current user to the database
                addCurrentUserToDatabase()
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to reset database", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Only admin can reset the database", Toast.LENGTH_SHORT).show()
        }
    }

    // Add a method to completely clear all test users and add back only the hardcoded ones - admin only
    private fun resetWithTestUsers() {
        if (adminManager.isCurrentUserAdmin()) {
            database.removeValue().addOnSuccessListener {
                val testUsers = listOf(
                    User(
                        uid = "test1",
                        email = "test1@example.com",
                        name = "Test User 1"
                    ),
                    User(
                        uid = "test2",
                        email = "test2@example.com",
                        name = "Test User 2"
                    ),
                    User(
                        uid = "test3",
                        email = "test3@example.com",
                        name = "Test User 3"
                    )
                )

                for (user in testUsers) {
                    database.child(user.uid!!).setValue(user)
                }
                
                // Make sure to re-add the current user to the database
                addCurrentUserToDatabase()
                
                Toast.makeText(this, "Database reset with test users", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to reset database", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Only admin can reset the database", Toast.LENGTH_SHORT).show()
        }
    }
}
