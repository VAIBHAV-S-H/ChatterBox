package com.example.messagingapp.admin

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.messagingapp.R
import com.example.messagingapp.crypto.CryptoManager
import com.example.messagingapp.crypto.MessageEncryption
import com.example.messagingapp.details.Message
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class AdminEncryptionVisualizerActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "AdminVisualizer"
    }
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adminManager: AdminManager
    private lateinit var cryptoManager: CryptoManager
    private lateinit var messageEncryption: MessageEncryption
    private lateinit var messagesAdapter: AdminMessageAdapter
    private lateinit var messagesList: MutableList<AdminMessageItem>
    private lateinit var userCountText: TextView
    private lateinit var messageCountText: TextView
    private lateinit var noDataText: TextView
    private lateinit var barChart: BarChart
    private lateinit var pieChart: PieChart
    
    private val userStats = HashMap<String, UserStats>()
    private var totalMessages = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_encryption_visualizer)
        
        // Set up ActionBar
        supportActionBar?.title = "Admin Dashboard"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Initialize admin manager
        adminManager = AdminManager()
        if (!adminManager.isCurrentUserAdmin()) {
            Toast.makeText(this, "Only admin users can access this page", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize views
        recyclerView = findViewById(R.id.recyclerViewMessages)
        userCountText = findViewById(R.id.userCountText)
        messageCountText = findViewById(R.id.messageCountText)
        noDataText = findViewById(R.id.noDataText)
        barChart = findViewById(R.id.barChart)
        pieChart = findViewById(R.id.pieChart)
        
        // Initialize crypto components
        cryptoManager = CryptoManager()
        messageEncryption = MessageEncryption(cryptoManager)
        
        // Set up messages adapter
        messagesList = mutableListOf()
        messagesAdapter = AdminMessageAdapter(messagesList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = messagesAdapter
        
        // Load data
        loadUsers()
        loadAllChats()
    }
    
    private fun loadUsers() {
        val database = FirebaseDatabase.getInstance().reference
        database.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userCount = snapshot.childrenCount.toInt()
                userCountText.text = "Users: $userCount"
                
                if (userCount == 0) {
                    noDataText.visibility = View.VISIBLE
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load users", error.toException())
            }
        })
    }
    
    private fun loadAllChats() {
        val database = FirebaseDatabase.getInstance().reference
        database.child("chats").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    noDataText.visibility = View.VISIBLE
                    return
                }
                
                // Process all chat rooms
                for (chatSnapshot in snapshot.children) {
                    val chatId = chatSnapshot.key!!
                    Log.d(TAG, "Processing chat room: $chatId")
                    
                    // Get user IDs from chat ID
                    val userIds = chatId.split("_")
                    if (userIds.size != 2) continue
                    
                    // Generate key for this chat
                    val chatKey = generateKeyFromChatId(chatId)
                    
                    // Process all messages in this chat
                    for (messageSnapshot in chatSnapshot.children) {
                        val message = messageSnapshot.getValue(Message::class.java) ?: continue
                        
                        // Track statistics
                        message.senderId?.let { userId ->
                            // Create user stats if not exists
                            if (!userStats.containsKey(userId)) {
                                userStats[userId] = UserStats(userId)
                            }
                            
                            // Update message count
                            userStats[userId]?.messageCount = (userStats[userId]?.messageCount ?: 0) + 1
                        }
                        
                        totalMessages++
                        
                        // Create admin message item with decrypted text
                        var displayText = message.originalText ?: "Unknown"
                        if (displayText == "Unknown" && message.encryptedText != null && message.iv != null) {
                            try {
                                val decryptedMessage = messageEncryption.decryptMessage(message, chatKey)
                                displayText = decryptedMessage.decryptedText ?: "Decryption failed"
                            } catch (e: Exception) {
                                Log.e(TAG, "Error decrypting message", e)
                                displayText = "[ENCRYPTED]"
                            }
                        }
                        
                        // Create admin message item
                        val item = AdminMessageItem(
                            sender = message.senderId ?: "Unknown",
                            timestamp = message.timestamp,
                            originalText = message.originalText ?: "[Not available]",
                            encryptedText = message.encryptedText ?: "[Not encrypted]",
                            displayText = displayText,
                            chatId = chatId
                        )
                        
                        messagesList.add(item)
                    }
                }
                
                // Update UI
                messageCountText.text = "Messages: $totalMessages"
                
                // Sort messages by timestamp (newest first)
                messagesList.sortByDescending { it.timestamp }
                messagesAdapter.notifyDataSetChanged()
                
                // Generate charts
                if (messagesList.isNotEmpty()) {
                    noDataText.visibility = View.GONE
                    generateCharts()
                } else {
                    noDataText.visibility = View.VISIBLE
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load chat data", error.toException())
                Toast.makeText(this@AdminEncryptionVisualizerActivity, 
                    "Failed to load data: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    
    private fun generateCharts() {
        generateBarChart()
        generatePieChart()
    }
    
    private fun generateBarChart() {
        // Get top 5 users by message count
        val topUsers = userStats.values.sortedByDescending { it.messageCount }.take(5)
        
        // Create bar entries
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        
        for ((index, userStat) in topUsers.withIndex()) {
            entries.add(BarEntry(index.toFloat(), userStat.messageCount.toFloat()))
            labels.add(userStat.userId.takeLast(5))
        }
        
        // Set up bar chart
        val dataSet = BarDataSet(entries, "Messages per User")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        
        val data = BarData(dataSet)
        data.setValueTextSize(12f)
        
        barChart.data = data
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = true
        barChart.setFitBars(true)
        
        // Set X axis labels
        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
        
        barChart.axisLeft.axisMinimum = 0f
        barChart.axisRight.isEnabled = false
        
        barChart.animateY(1000)
        barChart.invalidate()
    }
    
    private fun generatePieChart() {
        // Categorize messages by time of day
        val timeCategories = HashMap<String, Int>()
        timeCategories["Morning"] = 0    // 6am - 12pm
        timeCategories["Afternoon"] = 0  // 12pm - 6pm
        timeCategories["Evening"] = 0    // 6pm - 10pm
        timeCategories["Night"] = 0      // 10pm - 6am
        
        val dateFormat = SimpleDateFormat("HH", Locale.getDefault())
        
        for (message in messagesList) {
            val timestamp = message.timestamp
            val date = Date(timestamp)
            val hour = dateFormat.format(date).toInt()
            
            when {
                hour in 6..11 -> timeCategories["Morning"] = timeCategories["Morning"]!! + 1
                hour in 12..17 -> timeCategories["Afternoon"] = timeCategories["Afternoon"]!! + 1
                hour in 18..21 -> timeCategories["Evening"] = timeCategories["Evening"]!! + 1
                else -> timeCategories["Night"] = timeCategories["Night"]!! + 1
            }
        }
        
        // Create pie entries
        val entries = ArrayList<PieEntry>()
        for ((category, count) in timeCategories) {
            if (count > 0) {
                entries.add(PieEntry(count.toFloat(), category))
            }
        }
        
        // Set up pie chart
        val dataSet = PieDataSet(entries, "Messages by Time of Day")
        dataSet.colors = arrayListOf(
            Color.rgb(158, 193, 255),  // Light blue for Morning
            Color.rgb(158, 218, 255),  // Blue for Afternoon
            Color.rgb(187, 158, 255),  // Purple for Evening
            Color.rgb(115, 98, 187)    // Dark purple for Night
        )
        
        val data = PieData(dataSet)
        data.setValueTextSize(14f)
        data.setValueTextColor(Color.WHITE)
        
        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.setUsePercentValues(true)
        pieChart.setEntryLabelColor(Color.WHITE)
        pieChart.setEntryLabelTextSize(12f)
        pieChart.holeRadius = 40f
        pieChart.transparentCircleRadius = 45f
        
        pieChart.animateY(1000)
        pieChart.invalidate()
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
    
    // Data class for tracking user statistics
    data class UserStats(
        val userId: String,
        var messageCount: Int = 0
    )
    
    // Data class for displaying message in admin view
    data class AdminMessageItem(
        val sender: String,
        val timestamp: Long,
        val originalText: String,
        val encryptedText: String,
        val displayText: String,
        val chatId: String
    ) {
        fun getFormattedTimestamp(): String {
            val dateFormat = SimpleDateFormat("MM/dd/yyyy HH:mm", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 