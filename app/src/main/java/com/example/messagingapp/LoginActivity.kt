package com.example.messagingapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        // Check if user is already logged in
        if (auth.currentUser != null) {
            Log.d(TAG, "User already logged in: ${auth.currentUser?.email}")
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        val email = findViewById<EditText>(R.id.email)
        val password = findViewById<EditText>(R.id.password)
        val loginButton = findViewById<Button>(R.id.login)
        val registerButton = findViewById<Button>(R.id.register)

        loginButton.setOnClickListener {
            val emailText = email.text.toString()
            val passwordText = password.text.toString()
            
            // Basic validation
            if (emailText.isEmpty() || passwordText.isEmpty()) {
                Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show loading state
            loginButton.isEnabled = false
            loginButton.text = "Logging in..."
            
            auth.signInWithEmailAndPassword(emailText, passwordText)
                .addOnCompleteListener(this) { task ->
                    // Reset button state
                    loginButton.isEnabled = true
                    loginButton.text = "Login"
                    
                    if (task.isSuccessful) {
                        Log.d(TAG, "Login successful for user: $emailText")
                        Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    } else {
                        Log.e(TAG, "Login failed: ${task.exception?.message}")
                        Toast.makeText(
                            this,
                            "Authentication failed: ${task.exception?.message ?: "Unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }

        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
