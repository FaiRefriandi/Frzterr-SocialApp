package com.frzterr.app.ui.explore

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.frzterr.app.R
import com.frzterr.app.data.local.RecentSearch
import com.frzterr.app.data.repository.user.AppUser
import com.google.android.material.imageview.ShapeableImageView

sealed class SearchItem {
    data class UserItem(val user: AppUser) : SearchItem()
    data class RecentItem(val recent: RecentSearch) : SearchItem()
}

class UserSearchAdapter(
    private val onUserClick: (String, String?) -> Unit,
    private val onRemoveClick: ((String) -> Unit)? = null
) : ListAdapter<SearchItem, UserSearchAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SearchItem.UserItem -> holder.bindUser(item.user)
            is SearchItem.RecentItem -> holder.bindRecent(item.recent)
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ShapeableImageView = itemView.findViewById(R.id.imgAvatar)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvFullName: TextView = itemView.findViewById(R.id.tvFullName)
        private val btnRemove: ImageView = itemView.findViewById(R.id.btnRemove)

        fun bindUser(user: AppUser) {
            tvUsername.text = user.username ?: ""
            tvFullName.text = user.fullName ?: ""
            
            imgAvatar.load(user.avatarUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_user_placeholder)
                error(R.drawable.ic_user_placeholder)
            }
            
            btnRemove.visibility = View.GONE
            
            itemView.setOnClickListener {
                onUserClick(user.id, user.avatarUrl)
            }
        }

        fun bindRecent(recent: RecentSearch) {
            tvUsername.text = recent.username
            tvFullName.text = recent.fullName ?: ""
            
            imgAvatar.load(recent.avatarUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_user_placeholder)
                error(R.drawable.ic_user_placeholder)
            }
            
            // Show remove button for recent searches
            btnRemove.visibility = if (onRemoveClick != null) View.VISIBLE else View.GONE
            btnRemove.setOnClickListener {
                onRemoveClick?.invoke(recent.userId)
            }
            
            itemView.setOnClickListener {
                onUserClick(recent.userId, recent.avatarUrl)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SearchItem>() {
        override fun areItemsTheSame(oldItem: SearchItem, newItem: SearchItem): Boolean {
            return when {
                oldItem is SearchItem.UserItem && newItem is SearchItem.UserItem ->
                    oldItem.user.id == newItem.user.id
                oldItem is SearchItem.RecentItem && newItem is SearchItem.RecentItem ->
                    oldItem.recent.userId == newItem.recent.userId
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: SearchItem, newItem: SearchItem): Boolean {
            return oldItem == newItem
        }
    }
}
