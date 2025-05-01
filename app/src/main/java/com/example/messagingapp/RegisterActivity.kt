package com.example.messagingapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.messagingapp.crypto.CryptoManager
import com.example.messagingapp.crypto.KeyManager
import com.example.messagingapp.details.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var usernameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var registerButton: Button
    private lateinit var keyManager: KeyManager
    private lateinit var cryptoManager: CryptoManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("users")
        keyManager = KeyManager(this)
        cryptoManager = CryptoManager()

        usernameInput = findViewById(R.id.username)
        emailInput = findViewById(R.id.email)
        passwordInput = findViewById(R.id.password)
        registerButton = findViewById(R.id.register)

        registerButton.setOnClickListener {
            val usernameText = usernameInput.text.toString()
            val emailText = emailInput.text.toString()
            val passwordText = passwordInput.text.toString()

            if (usernameText.isNotEmpty() && emailText.isNotEmpty() && passwordText.isNotEmpty()) {
                auth.createUserWithEmailAndPassword(emailText, passwordText)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            // Generate a key for this user
                            val secretKey = cryptoManager.generateAESKey()
                            
                            // Create user
                            val user = User(
                                uid = auth.currentUser!!.uid,
                                email = emailText,
                                name = usernameText
                            )
                            
                            database.child(auth.currentUser!!.uid).setValue(user)
                                .addOnCompleteListener {
                                    if (it.isSuccessful) {
                                        startActivity(Intent(this, HomeActivity::class.java))
                                        finish()
                                    } else {
                                        Toast.makeText(this, "Failed to save user data.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        } else {
                            Toast.makeText(this, "Registration failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Please fill all fields.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
