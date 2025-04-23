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
import com.example.messagingapp.crypto.CryptoManager
import com.example.messagingapp.details.User
import com.example.messagingapp.details.UserAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.*
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
    private val TAG = "HomeActivity"

    // Keep a cached copy of the current user profile
    private var cachedUserProfile: User? = null

    // Constants for permission requests
    private val CAMERA_PERMISSION_REQUEST_CODE = 100

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
        
        // Database debug info - important
        database.keepSynced(true)
        Log.d(TAG, "Database reference initialized: ${database.toString()}")
        
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
        if (noUsersText == null) {
            // If the layout doesn't have this TextView yet, we'll handle visibility differently
            Log.d(TAG, "noUsersText not found in layout")
        }
        
        // Set up the Connect button
        btnAddUsers.setOnClickListener {
            showFriendCodeDialog()
        }
        
        userList = mutableListOf()
        userAdapter = UserAdapter(userList) { user ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("userId", user.id)
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
        
        // Debug: Print all menu items
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            Log.d(TAG, "Menu item ${i}: id=${item.itemId}, title=${item.title}")
        }
        
        // Force menu item text to be white
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            item.title = createWhiteSpannable(item.title.toString())
        }
        
        // Add database management options
        menu.add(Menu.NONE, 9999, Menu.NONE, createWhiteSpannable("Reset Database"))
        menu.add(Menu.NONE, 9998, Menu.NONE, createWhiteSpannable("Reset with Test Users"))
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "Menu item selected: id=${item.itemId}, title=${item.title}")
        
        return when (item.itemId) {
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
                Log.d(TAG, "Add existing users menu item clicked")
                addExistingUsersToDatabase()
                true
            }
            9999 -> {
                // Clean database
                cleanDatabase()
                true
            }
            9998 -> {
                resetWithTestUsers()
                true
            }
            else -> {
                Log.d(TAG, "Unknown menu item clicked: ${item.itemId}")
                super.onOptionsItemSelected(item)
            }
        }
    }
    
    private fun addExistingUsersToDatabase() {
        // Add existing authenticated users to the database
        // Use a simple dummy key that won't cause parsing errors
        val dummyPublicKey = "dummy_public_key_for_testing"
        
        Log.d(TAG, "Starting to add existing users to database")
        
        // Show a progress dialog
        val progressDialog = android.app.ProgressDialog(this)
        progressDialog.setMessage("Adding users to database...")
        progressDialog.setCancelable(false)
        progressDialog.show()
        
        // Add current user first to ensure it exists
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val user = User(
                id = currentUser.uid,
                email = currentUser.email,
                name = currentUser.displayName ?: currentUser.email?.substringBefore('@') ?: "User",
                publicKey = dummyPublicKey
            )
            
            Log.d(TAG, "Adding current user: ${user.name} (${user.id})")
            
            database.child(user.id!!).setValue(user)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully added current user to database")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to add current user: ${e.message}")
                }
        }
        
        // Users from your Firebase Auth console (or test users)
        val users = listOf(
            User(
                id = "test1", 
                email = "test1@example.com",
                name = "Test User 1", 
                publicKey = dummyPublicKey
            ),
            User(
                id = "test2", 
                email = "test2@example.com",
                name = "Test User 2",  
                publicKey = dummyPublicKey
            )
        )
        
        // Add each user to the database
        var successCount = 0
        for (user in users) {
            Log.d(TAG, "Adding user: ${user.name} (${user.id})")
            
            database.child(user.id!!).setValue(user)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Added user ${user.name} to database")
                        successCount++
                        
                        if (successCount >= users.size) {
                            progressDialog.dismiss()
                            Toast.makeText(this, "Added ${successCount} users to database", Toast.LENGTH_SHORT).show()
                            // Reload users to show the newly added ones
                            loadUsers()
                        }
                    } else {
                        Log.e(TAG, "Failed to add user ${user.name}: ${task.exception?.message}")
                        Toast.makeText(this, "Failed to add user ${user.name}", Toast.LENGTH_SHORT).show()
                        
                        if (successCount + 1 >= users.size) {
                            progressDialog.dismiss()
                        }
                    }
                }
        }
    }

    private fun loadUsers() {
        // Show a loading message
        Toast.makeText(this, "Loading users...", Toast.LENGTH_SHORT).show()
        
        // Clear existing listeners to avoid duplicates
        database.removeEventListener(userListener)
        
        // Listen for all users
        database.addValueEventListener(userListener)
        
        // Log that we're loading users
        Log.d(TAG, "Started loading users from database")
    }
    
    // Define the user value event listener at class level to avoid duplications
    private val userListener = object : ValueEventListener {
        @SuppressLint("NotifyDataSetChanged")
        override fun onDataChange(snapshot: DataSnapshot) {
            userList.clear()
            Log.d(TAG, "Data snapshot received: ${snapshot.childrenCount} users")
            
            try {
                // Manually check if we have users
                var hasUsers = false
                var totalUsers = 0
                
                for (dataSnapshot in snapshot.children) {
                    val user = dataSnapshot.getValue(User::class.java)
                    totalUsers++
                    if (user != null && user.id != auth.currentUser?.uid) {
                        // Make sure user has a proper display name
                        if (user.name.isNullOrBlank()) {
                            user.name = user.getDisplayName()
                        }
                        
                        userList.add(user)
                        hasUsers = true
                        Log.d(TAG, "Added user to display list: ${user.name} (${user.email})")
                    } else if (user != null) {
                        Log.d(TAG, "Skipping current user: ${user.name} (${user.email})")
                    }
                }
                
                Log.d(TAG, "Processed $totalUsers total users, displaying ${userList.size} users (excluding current user)")
                
                // Sort the userList in alphabetical order based on username, case-insensitive
                userList.sortBy { it.name?.lowercase(Locale.ROOT) }
                
                // If no real users found, show an informative message instead of "No users found"
                if (!hasUsers) {
                    if (totalUsers <= 1) {
                        // Only the current user exists
                        Toast.makeText(
                            this@HomeActivity, 
                            "No other users have registered yet. Invite friends to join!", 
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        // Strange case where we have users in the database but none to display
                        Toast.makeText(
                            this@HomeActivity, 
                            "Users found but none are available to chat with.", 
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    // Users were found and added to the list
                    Toast.makeText(
                        this@HomeActivity,
                        "Found ${userList.size} users",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                userAdapter.notifyDataSetChanged()
                
                // Update UI based on whether users were found
                updateUI()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing users: ${e.message}", e)
                Toast.makeText(this@HomeActivity, "Error loading users: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e(TAG, "Database error: ${error.message}")
            Toast.makeText(this@HomeActivity, "Failed to load users: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        if (userList.isEmpty()) {
            // No users found - show empty state
            try {
                noUsersText?.visibility = View.VISIBLE
                userRecyclerView.visibility = View.GONE
                Toast.makeText(this, "No users found. Please check your connection or add users.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating UI: ${e.message}")
            }
        } else {
            // Users found - show the list
            try {
                noUsersText?.visibility = View.GONE
                userRecyclerView.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e(TAG, "Error updating UI: ${e.message}")
            }
        }
    }

    private fun filterUsers(query: String) {
        Log.d(TAG, "Filtering users with query: '$query', total users: ${userList.size}")
        
        try {
            // If query is empty, show all users
            if (query.isEmpty()) {
                userAdapter.updateList(userList)
                updateUI()
                return
            }
            
            val lowercaseQuery = query.lowercase(Locale.ROOT).trim()
            
            val filteredList = userList.filter { user -> 
                val matchesName = user.name?.lowercase(Locale.ROOT)?.contains(lowercaseQuery) == true
                val matchesEmail = user.email?.lowercase(Locale.ROOT)?.contains(lowercaseQuery) == true
                
                Log.d(TAG, "User ${user.name} (${user.email}) matches name: $matchesName, matches email: $matchesEmail")
                
                matchesName || matchesEmail
            }
            
            Log.d(TAG, "Filtered to ${filteredList.size} users")
            userAdapter.updateList(filteredList)
            
            // Update empty state based on filtered results
            if (filteredList.isEmpty()) {
                try {
                    noUsersText.visibility = View.VISIBLE
                    userRecyclerView.visibility = View.GONE
                    
                    // Show a helpful message when search returns no results
                    Toast.makeText(
                        this,
                        "No users found matching '$query'. Try a different search.",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating filtered UI: ${e.message}")
                }
            } else {
                try {
                    noUsersText.visibility = View.GONE
                    userRecyclerView.visibility = View.VISIBLE
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating filtered UI: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error filtering users: ${e.message}", e)
            Toast.makeText(this, "Error while searching: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
            // Simple direct dialog with no database calls
            val email = currentUser.email ?: "Not available"
            val uid = currentUser.uid
            val name = currentUser.displayName ?: email.substringBefore('@')
            
            val message = "Email: $email\n\nUsername: $name\n\nUID: $uid"
            
            // Use Android's built-in AlertDialog
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("User Profile")
            builder.setMessage(message)
            builder.setPositiveButton("OK", null)
            
            // Show dialog immediately
            builder.create().show()
            
            // Log for debugging
            Log.d(TAG, "Displayed simple profile dialog for user: $email")
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Attempted to show profile but user is not logged in")
        }
    }

    // Add current user to database to ensure they exist
    private fun addCurrentUserToDatabase(callback: (Boolean) -> Unit = {}) {
        val currentUser = auth.currentUser
        
        if (currentUser == null) {
            Log.e(TAG, "Cannot add user to database: No user is logged in")
            callback(false)
            return
        }
        
        // Create a display name if not available
        val displayName = currentUser.displayName 
            ?: currentUser.email?.substringBefore('@') 
            ?: "User-${currentUser.uid.substring(0, 5)}"
            
        Log.d(TAG, "Checking current user in database: $displayName (${currentUser.email})")
        
        // First check if we need to update by retrieving the current user data
        database.child(currentUser.uid).get().addOnSuccessListener { snapshot ->
            val existingUser = snapshot.getValue(User::class.java)
            val needsUpdate = existingUser == null || 
                              existingUser.name != displayName || 
                              existingUser.email != currentUser.email ||
                              existingUser.publicKey.isNullOrEmpty()
            
            if (needsUpdate) {
                Log.d(TAG, "User needs update in database")
                
                // Create new user object with updated information
                val user = User(
                    id = currentUser.uid,
                    email = currentUser.email,
                    name = displayName,
                    publicKey = existingUser?.publicKey ?: "dummy_public_key_for_testing"
                )
                
                // Maintain new message count if previously set
                if (existingUser != null) {
                    user.newMessageCount = existingUser.newMessageCount
                }
                
                database.child(currentUser.uid).setValue(user)
                    .addOnSuccessListener {
                        Log.d(TAG, "Successfully updated current user in database: ${user.name}")
                        
                        // Cache the updated user profile
                        cachedUserProfile = user
                        
                        // Signal success
                        callback(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update current user in database: ${e.message}", e)
                        callback(false)
                    }
            } else {
                Log.d(TAG, "User data is already up to date in database")
                
                // Cache the existing user profile
                cachedUserProfile = existingUser
                
                // Signal success
                callback(true)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to retrieve current user data: ${e.message}", e)
            
            // If we can't retrieve the current user, create a new one anyway
            val user = User(
                id = currentUser.uid,
                email = currentUser.email,
                name = displayName,
                publicKey = "dummy_public_key_for_testing"
            )
            
            database.child(currentUser.uid).setValue(user)
                .addOnSuccessListener {
                    Log.d(TAG, "Added current user to database after failed retrieval: ${user.name}")
                    
                    // Cache the user profile
                    cachedUserProfile = user
                    
                    // Signal success
                    callback(true)
                }
                .addOnFailureListener { ex ->
                    Log.e(TAG, "Failed to add current user to database: ${ex.message}", ex)
                    callback(false)
                }
        }
    }

    // Add the function to show friend code dialog with options
    private fun showFriendCodeDialog() {
        val options = arrayOf("Show My Friend Code", "Scan QR Code", "Enter Friend Code")
        
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Connect with Friend")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> showMyFriendCode()
                1 -> scanQRCode()
                2 -> enterFriendCode()
            }
        }
        builder.create().show()
    }

    // Show the current user's friend code
    private fun showMyFriendCode() {
        val currentUser = auth.currentUser
        
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to share your friend code", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Generate a friend code based on the user's ID
        val friendCode = generateFriendCode(currentUser.uid)
        
        // Create a custom view for the dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_friend_code, null)
        val qrCodeImageView = dialogView.findViewById<ImageView>(R.id.qrCodeImageView)
        val friendCodeTextView = dialogView.findViewById<TextView>(R.id.friendCodeTextView)
        
        // Generate QR code
        try {
            val multiFormatWriter = MultiFormatWriter()
            val bitMatrix: BitMatrix = multiFormatWriter.encode(
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
            Log.e(TAG, "Error generating QR code: ${e.message}", e)
            // If QR generation fails, still show the text code
            friendCodeTextView.text = friendCode
        }
        
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Your Friend Code")
        builder.setView(dialogView)
        
        // Add a copy button
        builder.setPositiveButton("Copy Code") { _, _ ->
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Friend Code", friendCode)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Friend code copied to clipboard", Toast.LENGTH_SHORT).show()
        }
        
        builder.setNegativeButton("Close", null)
        builder.create().show()
        
        Log.d(TAG, "Showed friend code: $friendCode for user: ${currentUser.email}")
    }

    // Generate a friend code from user ID
    private fun generateFriendCode(userId: String): String {
        // Make sure we have at least 12 characters to work with
        val paddedId = if (userId.length < 12) {
            // Pad with zeros if the ID is too short (shouldn't happen with Firebase UIDs)
            userId.padEnd(12, '0')
        } else {
            // Use first 12 chars if longer
            userId.substring(0, 12)
        }
        
        // Format as XXX-XXX-XXX-XXX
        val code = paddedId.chunked(3).joinToString("-")
        Log.d(TAG, "Generated friend code: $code from user ID: $userId")
        return code.uppercase(Locale.ROOT)
    }

    // Scan a QR code
    private fun scanQRCode() {
        // Check if we have camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Request camera permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
            return
        }
        
        try {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Scan a friend's QR code")
            options.setCameraId(0) // Use back camera
            options.setBeepEnabled(true)
            options.setBarcodeImageEnabled(true)
            options.setOrientationLocked(false)
            
            barcodeLauncher.launch(options)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching scanner: ${e.message}", e)
            Toast.makeText(this, "QR scanner failed to launch: ${e.message}", Toast.LENGTH_SHORT).show()
            // Fallback to manual entry
            enterFriendCode()
        }
    }

    // Register the launcher for QR scanning
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedCode = result.contents
            Log.d(TAG, "Scanned QR code: $scannedCode")
            
            // If it's already in friend code format (has dashes)
            if (scannedCode.contains("-")) {
                // Process as a friend code directly
                connectWithFriendCode(scannedCode)
            } else {
                // Try to format as a friend code if it's a raw ID
                try {
                    // If it's a raw user ID, format it to a friend code
                    val formattedCode = scannedCode.take(12).chunked(3).joinToString("-").uppercase(Locale.ROOT)
                    Log.d(TAG, "Converted raw ID to friend code: $formattedCode")
                    connectWithFriendCode(formattedCode)
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Error processing QR code: ${e.message}", e)
                    // Fallback to manual entry
                    enterFriendCode()
                }
            }
        } else {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // Enter a friend code manually
    private fun enterFriendCode() {
        val input = EditText(this)
        input.hint = "XXX-XXX-XXX-XXX"
        input.filters = arrayOf(android.text.InputFilter.AllCaps(), android.text.InputFilter.LengthFilter(15))
        
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Enter Friend Code")
        builder.setView(input)
        
        builder.setPositiveButton("Connect") { _, _ ->
            val friendCode = input.text.toString().trim()
            if (friendCode.isEmpty()) {
                Toast.makeText(this, "Please enter a valid friend code", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            
            // Process the friend code
            connectWithFriendCode(friendCode)
        }
        
        builder.setNegativeButton("Cancel", null)
        builder.create().show()
    }

    // Process a friend code to connect with a user
    private fun connectWithFriendCode(friendCode: String) {
        // Clean and format the friend code
        val cleanedCode = friendCode.replace("\\s+".toRegex(), "").replace("-", "").lowercase(Locale.ROOT)
        
        if (cleanedCode.length < 8) {
            Toast.makeText(this, "Invalid friend code format", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Invalid friend code format: $friendCode, cleaned: $cleanedCode")
            return
        }
        
        // Show more detail in logs
        Log.d(TAG, "Processing friend code: $friendCode -> cleaned: $cleanedCode")
        Log.d(TAG, "Current user: ${auth.currentUser?.uid}")
        
        // Show a progress dialog
        val progressDialog = android.app.ProgressDialog(this)
        progressDialog.setMessage("Looking for user...")
        progressDialog.setCancelable(true)
        progressDialog.show()
        
        // First check if database reference is initialized correctly
        if (database == null) {
            progressDialog.dismiss()
            Log.e(TAG, "Database reference is null, reinitializing")
            database = FirebaseDatabase.getInstance().getReference("users")
        }
        
        // Get all users and check manually - this is more reliable than queries
        database.get().addOnSuccessListener { snapshot ->
            Log.d(TAG, "Database snapshot received with ${snapshot.childrenCount} users")
            
            if (snapshot.exists()) {
                var matchedUser: User? = null
                var count = 0
                
                // Debug - print all users
                for (userSnapshot in snapshot.children) {
                    count++
                    val userId = userSnapshot.key
                    Log.d(TAG, "User $count: ID = $userId")
                    
                    // Match if userId contains the cleaned code
                    if (userId != null && userId.contains(cleanedCode)) {
                        val user = userSnapshot.getValue(User::class.java)
                        if (user != null && user.id != auth.currentUser?.uid) {
                            matchedUser = user
                            Log.d(TAG, "FOUND MATCH: ${user.name} with ID ${user.id}")
                            break
                        }
                    }
                }
                
                if (matchedUser != null) {
                    progressDialog.dismiss()
                    showUserConnectionConfirmation(matchedUser)
                } else {
                    // If not found, try getting user by exact ID
                    Log.d(TAG, "No match found in first pass, trying direct ID lookup")
                    
                    // Assume the code might be a full ID and try a direct lookup
                    database.child(cleanedCode).get().addOnSuccessListener { directSnapshot ->
                        if (directSnapshot.exists()) {
                            val directUser = directSnapshot.getValue(User::class.java)
                            if (directUser != null && directUser.id != auth.currentUser?.uid) {
                                progressDialog.dismiss()
                                Log.d(TAG, "Found user by direct ID: ${directUser.name}")
                                showUserConnectionConfirmation(directUser)
                            } else {
                                findUserByPrefix(cleanedCode, progressDialog)
                            }
                        } else {
                            findUserByPrefix(cleanedCode, progressDialog)
                        }
                    }.addOnFailureListener { e ->
                        findUserByPrefix(cleanedCode, progressDialog)
                    }
                }
            } else {
                progressDialog.dismiss()
                Toast.makeText(this, "No users found in database", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Database snapshot exists but is empty")
            }
        }.addOnFailureListener { e ->
            progressDialog.dismiss()
            Toast.makeText(this, "Error accessing database: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error getting database snapshot: ${e.message}", e)
        }
    }

    // Helper method to find user by ID prefix
    private fun findUserByPrefix(prefix: String, progressDialog: android.app.ProgressDialog) {
        Log.d(TAG, "Trying to find user by prefix: $prefix")
        
        // Try ordering by ID to find users with matching prefix
        database.orderByKey().startAt(prefix).endAt(prefix + "\uf8ff").get()
            .addOnSuccessListener { prefixSnapshot ->
                progressDialog.dismiss()
                
                if (prefixSnapshot.exists() && prefixSnapshot.childrenCount > 0) {
                    Log.d(TAG, "Found ${prefixSnapshot.childrenCount} users with matching prefix")
                    
                    for (userSnapshot in prefixSnapshot.children) {
                        val user = userSnapshot.getValue(User::class.java)
                        if (user != null && user.id != auth.currentUser?.uid) {
                            Log.d(TAG, "Selected user: ${user.name} with ID ${user.id}")
                            showUserConnectionConfirmation(user)
                            return@addOnSuccessListener
                        }
                    }
                    
                    // If we get here, we didn't find a valid user
                    Toast.makeText(this, "No valid user found with this code", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "All users with matching prefix were invalid")
                } else {
                    Toast.makeText(this, "No user found with this friend code", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "No users found with matching prefix")
                }
            }.addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Error finding user: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error searching by prefix: ${e.message}", e)
            }
    }

    // Show confirmation dialog for connecting with a user
    private fun showUserConnectionConfirmation(user: User) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Connect with User")
        builder.setMessage("Would you like to connect with ${user.name}?\n\nEmail: ${user.email}")
        
        builder.setPositiveButton("Connect") { _, _ ->
            // Open chat with this user
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("userId", user.id)
            intent.putExtra("userName", user.name)
            startActivity(intent)
            
            // Show success message
            Toast.makeText(this, "Connected with ${user.name}!", Toast.LENGTH_SHORT).show()
        }
        
        builder.setNegativeButton("Cancel", null)
        builder.create().show()
    }

    // Handle permission request results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, proceed with scanning
                    scanQRCode()
                } else {
                    // Permission denied, show a message
                    Toast.makeText(
                        this,
                        "Camera permission is required to scan QR codes",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Fall back to manual entry
                    enterFriendCode()
                }
            }
        }
    }

    // Add this new method to clean the database
    private fun cleanDatabase() {
        // Show a confirmation dialog with stronger warning
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Reset Database")
        builder.setMessage("WARNING: This will completely remove ALL users from the database except your current account. This action cannot be undone. Continue?")
        
        builder.setPositiveButton("Yes, Reset Everything") { _, _ ->
            // Show a progress dialog
            val progressDialog = android.app.ProgressDialog(this)
            progressDialog.setMessage("Resetting database...")
            progressDialog.setCancelable(false)
            progressDialog.show()
            
            // Get current user ID
            val currentUserId = auth.currentUser?.uid
            
            if (currentUserId == null) {
                progressDialog.dismiss()
                Toast.makeText(this, "You must be logged in to reset the database", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            
            Log.d(TAG, "Current authenticated user: $currentUserId")
            
            // First, save the current user's data
            database.child(currentUserId).get().addOnSuccessListener { currentUserSnapshot ->
                val currentUserData = currentUserSnapshot.getValue(User::class.java)
                
                // Now completely clear the users database
                database.removeValue().addOnSuccessListener {
                    Log.d(TAG, "Database completely cleared")
                    
                    // Re-add only the current user
                    if (currentUserData != null) {
                        database.child(currentUserId).setValue(currentUserData)
                            .addOnSuccessListener {
                                progressDialog.dismiss()
                                Toast.makeText(this, "Database reset successful", Toast.LENGTH_SHORT).show()
                                loadUsers() // Reload the user list
                            }
                            .addOnFailureListener { e ->
                                progressDialog.dismiss()
                                Log.e(TAG, "Failed to restore current user: ${e.message}")
                                Toast.makeText(this, "Failed to restore your account: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        // If we couldn't get the current user data, create a new user
                        val displayName = auth.currentUser?.displayName 
                            ?: auth.currentUser?.email?.substringBefore('@') 
                            ?: "User-${currentUserId.substring(0, 5)}"
                            
                        val newUser = User(
                            id = currentUserId,
                            email = auth.currentUser?.email,
                            name = displayName,
                            publicKey = "dummy_public_key_for_testing"
                        )
                        
                        database.child(currentUserId).setValue(newUser)
                            .addOnSuccessListener {
                                progressDialog.dismiss()
                                Toast.makeText(this, "Database reset successful", Toast.LENGTH_SHORT).show()
                                loadUsers() // Reload the user list
                            }
                            .addOnFailureListener { e ->
                                progressDialog.dismiss()
                                Log.e(TAG, "Failed to restore current user: ${e.message}")
                                Toast.makeText(this, "Failed to restore your account: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }.addOnFailureListener { e ->
                    progressDialog.dismiss()
                    Log.e(TAG, "Failed to clear database: ${e.message}")
                    Toast.makeText(this, "Failed to reset database: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                progressDialog.dismiss()
                Log.e(TAG, "Failed to get current user data: ${e.message}")
                Toast.makeText(this, "Failed to get your account data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        builder.setNegativeButton("Cancel", null)
        builder.create().show()
    }

    // Add a method to completely clear all test users and add back only the hardcoded ones
    private fun resetWithTestUsers() {
        // Show a confirmation dialog
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Reset with Test Users")
        builder.setMessage("This will completely reset the database and add test users. Continue?")
        
        builder.setPositiveButton("Yes") { _, _ ->
            // Show a progress dialog
            val progressDialog = android.app.ProgressDialog(this)
            progressDialog.setMessage("Resetting database and adding test users...")
            progressDialog.setCancelable(false)
            progressDialog.show()
            
            // First, completely clear the database
            database.removeValue().addOnSuccessListener {
                Log.d(TAG, "Database completely cleared")
                
                // Use a simple dummy key that won't cause parsing errors
                val dummyPublicKey = "dummy_public_key_for_testing"
                
                // Add current user first
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val user = User(
                        id = currentUser.uid,
                        email = currentUser.email,
                        name = currentUser.displayName ?: currentUser.email?.substringBefore('@') ?: "User",
                        publicKey = dummyPublicKey
                    )
                    
                    database.child(user.id!!).setValue(user)
                        .addOnSuccessListener {
                            Log.d(TAG, "Successfully added current user to database")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to add current user: ${e.message}")
                        }
                }
                
                // Add test users
                val testUsers = listOf(
                    User(
                        id = "test1", 
                        email = "test1@example.com",
                        name = "Test User 1", 
                        publicKey = dummyPublicKey
                    ),
                    User(
                        id = "test2", 
                        email = "test2@example.com",
                        name = "Test User 2",  
                        publicKey = dummyPublicKey
                    )
                )
                
                var successCount = 0
                for (user in testUsers) {
                    database.child(user.id!!).setValue(user)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d(TAG, "Added test user ${user.name} to database")
                                successCount++
                                
                                if (successCount >= testUsers.size) {
                                    progressDialog.dismiss()
                                    Toast.makeText(this, "Database reset with test users", Toast.LENGTH_SHORT).show()
                                    loadUsers() // Reload the user list
                                }
                            } else {
                                Log.e(TAG, "Failed to add test user ${user.name}: ${task.exception?.message}")
                            }
                        }
                }
            }.addOnFailureListener { e ->
                progressDialog.dismiss()
                Log.e(TAG, "Failed to clear database: ${e.message}")
                Toast.makeText(this, "Failed to reset database: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        builder.setNegativeButton("No", null)
        builder.create().show()
    }
}
