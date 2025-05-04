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
            uid = "7P4pilOp0TVgrMatr1jyYRYnLFaT2", // From your Firebase Auth console
            email = "simsimsim@gmail.com",
            name = "sisi" // Or whatever display name you want
        ),
        User(
            uid = "yWV8r3DSEgd5M2nuGydFhIL31OD2", // From your Firebase Auth console
            email = "yoyo@gmail.com",
            name = "vibes" // Or whatever display name you want  
        )
    )
    
    // Add each user to the database
    for (user in users) {
        if (user.uid != null) {
            database.child(user.uid!!).setValue(user)
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
