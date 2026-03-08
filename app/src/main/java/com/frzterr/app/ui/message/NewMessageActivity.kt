package com.frzterr.app.ui.message

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.frzterr.app.R
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.data.repository.message.MessageRepository
import com.frzterr.app.data.repository.user.AppUser
import com.frzterr.app.data.repository.user.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.google.android.material.imageview.ShapeableImageView

class NewMessageActivity : AppCompatActivity() {

    companion object {
        const val RESULT_CONVERSATION_ID = "result_conversation_id"
        const val RESULT_OTHER_USER_ID = "result_other_user_id"
        const val RESULT_OTHER_USERNAME = "result_other_username"
        const val RESULT_OTHER_AVATAR_URL = "result_other_avatar_url"
    }

    private val authRepo = AuthRepository()
    private val userRepo = UserRepository()
    private val msgRepo = MessageRepository()
    private lateinit var adapter: NewMessageUserAdapter
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_message)

        // ===== STATUS BAR SPACING =====
        val toolbar = findViewById<View>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, statusBarHeight, v.paddingRight, v.paddingBottom)
            v.layoutParams.height = v.layoutParams.height + statusBarHeight
            v.layoutParams = v.layoutParams
            insets
        }

        // ===== VIEWS =====
        val ivBack = findViewById<View>(R.id.ivBack)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val rvUsers = findViewById<RecyclerView>(R.id.rvUsers)
        val tvSuggestedHeader = findViewById<TextView>(R.id.tvSuggestedHeader)
        val tvNoResults = findViewById<TextView>(R.id.tvNoResults)

        // ===== BACK =====
        ivBack.setOnClickListener { finish() }

        // ===== ADAPTER =====
        adapter = NewMessageUserAdapter { user ->
            openConversationWith(user)
        }
        rvUsers.adapter = adapter

        // ===== LOAD SUGGESTED (all users except self) =====
        val currentUserId = authRepo.getCurrentUser()?.id ?: ""
        lifecycleScope.launch {
            val suggested = userRepo.searchUsers("", limit = 20, excludeUserId = currentUserId)
            adapter.submitList(suggested)
            tvSuggestedHeader.visibility = if (suggested.isNotEmpty()) View.VISIBLE else View.GONE
            tvNoResults.visibility = if (suggested.isEmpty()) View.VISIBLE else View.GONE
        }

        // ===== SEARCH =====
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // debounce
                    val results = if (query.isBlank()) {
                        userRepo.searchUsers("", limit = 20, excludeUserId = currentUserId)
                    } else {
                        userRepo.searchUsers(query, limit = 20, excludeUserId = currentUserId)
                    }
                    adapter.submitList(results)
                    tvSuggestedHeader.text = if (query.isBlank()) "Suggested" else "Results"
                    tvSuggestedHeader.visibility = if (results.isNotEmpty()) View.VISIBLE else View.GONE
                    tvNoResults.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Focus search immediately
        etSearch.requestFocus()
    }

    private fun openConversationWith(user: AppUser) {
        val currentUserId = authRepo.getCurrentUser()?.id ?: return
        lifecycleScope.launch {
            val conv = msgRepo.getOrCreateConversation(currentUserId, user.id) ?: return@launch
            val intent = Intent(this@NewMessageActivity, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conv.id)
                putExtra(ChatActivity.EXTRA_OTHER_USER_ID, user.id)
                putExtra(ChatActivity.EXTRA_OTHER_USERNAME, user.fullName?.takeIf { it.isNotBlank() } ?: "@${user.username}")
                putExtra(ChatActivity.EXTRA_OTHER_AVATAR_URL, user.avatarUrl)
            }
            startActivity(intent)
            finish()
        }
    }
}

// ============================================================================
// ADAPTER (inner adapter for the user list)
// ============================================================================

class NewMessageUserAdapter(
    private val onUserClick: (AppUser) -> Unit
) : ListAdapter<AppUser, NewMessageUserAdapter.ViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<AppUser>() {
        override fun areItemsTheSame(a: AppUser, b: AppUser) = a.id == b.id
        override fun areContentsTheSame(a: AppUser, b: AppUser) = a == b
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ShapeableImageView = view.findViewById(R.id.imgAvatar)
        val tvName: TextView = view.findViewById(R.id.tvUsername)
        val tvUsername: TextView = view.findViewById(R.id.tvFullName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_search, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = getItem(position)

        // Display: show full name as primary, username as secondary (matching reference image)
        holder.tvName.text = user.fullName?.takeIf { it.isNotBlank() } ?: user.username
        holder.tvUsername.text = "@${user.username}"

        Glide.with(holder.ivAvatar)
            .load(user.avatarUrl)
            .placeholder(R.drawable.ic_user_placeholder)
            .circleCrop()
            .into(holder.ivAvatar)

        holder.itemView.setOnClickListener { onUserClick(user) }
    }
}
