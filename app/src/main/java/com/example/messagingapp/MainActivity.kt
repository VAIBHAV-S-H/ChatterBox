package com.example.messagingapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.messagingapp.admin.AdminManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val ADMIN_PASSWORD = "Simurgh" // Admin password as requested
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerText: TextView
    private lateinit var adminManager: AdminManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        adminManager = AdminManager()

        // Check if user is already logged in
        if (auth.currentUser != null) {
            navigateToHome()
            finish()
            return
        }

        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        registerText = findViewById(R.id.registerText)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()
            
            if (email.isNotEmpty() && password.isNotEmpty()) {
                // Check if admin login attempt (using email from AdminManager)
                if (email == AdminManager.ADMIN_EMAIL && password == ADMIN_PASSWORD) {
                    loginAdmin(email, password)
                } else {
                    loginUser(email, password)
                }
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }
        
        registerText.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser(email: String, password: String) {
        try {
            // Show loading state
            loginButton.isEnabled = false
            loginButton.text = "Logging in..."
            
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    // Reset button state
                    loginButton.isEnabled = true
                    loginButton.text = "Login"
                    
                    if (task.isSuccessful) {
                        Log.d(TAG, "signInWithEmail:success")
                        navigateToHome()
                    } else {
                        Log.w(TAG, "signInWithEmail:failure", task.exception)
                        Toast.makeText(this, "Authentication failed: ${task.exception?.message}", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error during login", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            
            // Reset button state if there's an exception
            loginButton.isEnabled = true
            loginButton.text = "Login"
        }
    }
    
    private fun loginAdmin(email: String, password: String) {
        try {
            // Show loading state
            loginButton.isEnabled = false
            loginButton.text = "Logging in..."
            
            // Special handling for admin login
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    // Reset button state
                    loginButton.isEnabled = true
                    loginButton.text = "Login"
                    
                    if (task.isSuccessful) {
                        Log.d(TAG, "Admin signInWithEmail:success")
                        // Ensure admin entry exists in database
                        val database = FirebaseDatabase.getInstance().getReference("users")
                        val user = auth.currentUser
                        
                        if (user != null) {
                            val adminUser = HashMap<String, Any>()
                            adminUser["uid"] = user.uid
                            adminUser["email"] = email
                            adminUser["name"] = "Administrator"
                            adminUser["isAdmin"] = true
                            
                            database.child(user.uid).updateChildren(adminUser)
                                .addOnSuccessListener {
                                    Log.d(TAG, "Admin user data updated")
                                    navigateToHome()
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Error updating admin user", e)
                                    navigateToHome()
                                }
                        } else {
                            navigateToHome()
                        }
                    } else {
                        Log.w(TAG, "Admin signInWithEmail:failure", task.exception)
                        Toast.makeText(this, "Admin authentication failed: ${task.exception?.message}", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error during admin login", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            
            // Reset button state if there's an exception
            loginButton.isEnabled = true
            loginButton.text = "Login"
        }
    }
    
    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
} 