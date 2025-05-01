package com.example.messagingapp.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.messagingapp.R

/**
 * Adapter for displaying messages in admin visualization.
 * Shows both encrypted and original text for each message.
 */
class AdminMessageAdapter(private val messageList: List<AdminEncryptionVisualizerActivity.AdminMessageItem>) :
    RecyclerView.Adapter<AdminMessageAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val senderText: TextView = itemView.findViewById(R.id.textSender)
        private val originalText: TextView = itemView.findViewById(R.id.textOriginal)
        private val encryptedText: TextView = itemView.findViewById(R.id.textEncrypted)
        private val timestampText: TextView = itemView.findViewById(R.id.textTimestamp)

        fun bind(message: AdminEncryptionVisualizerActivity.AdminMessageItem) {
            // Set sender and timestamp
            senderText.text = "Sender: ${message.sender}"
            timestampText.text = message.getFormattedTimestamp()
            
            // Set original text (for admin viewing)
            originalText.text = "Original: ${message.originalText}"
            
            // Set encrypted text (truncated for display)
            val encryptedShort = if (message.encryptedText.length > 30) {
                message.encryptedText.substring(0, 30) + "..."
            } else {
                message.encryptedText
            }
            encryptedText.text = "Encrypted: $encryptedShort"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messageList[position])
    }

    override fun getItemCount(): Int = messageList.size
} 