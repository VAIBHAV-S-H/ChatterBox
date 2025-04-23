import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.example.messagingapp.details.User

// This is a utility script to add existing authenticated users to the Realtime Database
// You can copy and run this code directly in your app's HomeActivity or in a separate utility activity

fun addExistingUsersToDatabase() {
    val database = FirebaseDatabase.getInstance().getReference("users")
    
    // Sample users that need to be added to the database
    // Note: Replace these with your actual user IDs, emails, and names
    val users = listOf(
        User(
            id = "7P4pilOp0TVgrMatr1jyYRYnLFaT2", // From your Firebase Auth console
            email = "eonicsimurgh@gmail.com",
            name = "Simurgh", // Or whatever display name you want
            publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzJU1KCzvVHa/VkDgKE3NnPslXQneFXtqDv0xCQQiDX5WK1z2NBUfMJORvO9MixRpbRJP8Jgp4wGUu6o8W/8zJDQ4WgqBUR4mLzujtUM1KQ5vxE07K3YcWnGJHM3Kt0Gc8QlzkGxbaTWc0oX9KWQ1vVyInwHcWaZRMAk+L0pLJrWsJ3SCJqSJ7RjBJ0ck3NMXBB9D2+uDQouF7v3g+r4hD8EIRZ2hIvVOBxBbxjHZgx95Hsk5LHCw5UfJCJxm9s6o8p75bEbMdAn28V5QHD4oKvZ+UlDQM7UuYy5BL1U8uBa3ZSXX4HCj1xnbYIIyVVA4TJxQtXqX7XLbK3UiOwIDAQAB"
        ),
        User(
            id = "yWV8r3DSEgd5M2nuGydFhIL31OD2", // From your Firebase Auth console
            email = "1nt22cs211.vaibhav@nmit.ac.in",
            name = "Vaibhav", // Or whatever display name you want  
            publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzJU1KCzvVHa/VkDgKE3NnPslXQneFXtqDv0xCQQiDX5WK1z2NBUfMJORvO9MixRpbRJP8Jgp4wGUu6o8W/8zJDQ4WgqBUR4mLzujtUM1KQ5vxE07K3YcWnGJHM3Kt0Gc8QlzkGxbaTWc0oX9KWQ1vVyInwHcWaZRMAk+L0pLJrWsJ3SCJqSJ7RjBJ0ck3NMXBB9D2+uDQouF7v3g+r4hD8EIRZ2hIvVOBxBbxjHZgx95Hsk5LHCw5UfJCJxm9s6o8p75bEbMdAn28V5QHD4oKvZ+UlDQM7UuYy5BL1U8uBa3ZSXX4HCj1xnbYIIyVVA4TJxQtXqX7XLbK3UiOwIDAQAB"
        )
    )
    
    // Add each user to the database
    for (user in users) {
        if (user.id != null) {
            database.child(user.id).setValue(user)
                .addOnSuccessListener {
                    println("Added user ${user.name} to database")
                }
                .addOnFailureListener {
                    println("Failed to add user ${user.name}: ${it.message}")
                }
        }
    }
}

// To use this script:
// 1. Add this function to your project (e.g., in HomeActivity.kt)
// 2. Call this function once, e.g., from a button click or menu option
// After running it once, your users should appear in the Firebase Database 