package com.example.messagingapp.details

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.messagingapp.R

class UserAdapter(
    private var userList: List<User>,
    private val clickListener: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private val TAG = "UserAdapter"

    fun updateList(newList: List<User>) {
        Log.d(TAG, "Updating adapter with ${newList.size} messages")
        userList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.user_item, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        
        // Use getDisplayName() as a fallback if name is null or blank
        val displayName = if (user.name.isNullOrBlank()) user.getDisplayName() else user.name
        holder.userName.text = displayName
        
        // Set new message count
        if (user.newMessageCount > 0) {
            holder.newMessageCount.visibility = View.VISIBLE
            holder.newMessageCount.text = user.newMessageCount.toString()
        } else {
            holder.newMessageCount.visibility = View.GONE
        }
        
        // Set click listener
        holder.itemView.setOnClickListener {
            clickListener(user)
        }
    }

    override fun getItemCount() = userList.size

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userName: TextView = itemView.findViewById(R.id.userName)
        val newMessageCount: TextView = itemView.findViewById(R.id.newMessageCount)
    }
}
