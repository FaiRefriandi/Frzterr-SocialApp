package com.frzterr.app.ui.message

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.frzterr.app.R
import com.frzterr.app.data.model.ConversationWithUser
import com.frzterr.app.data.repository.auth.AuthRepository
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

class MessageFragment : Fragment(R.layout.fragment_message) {

    private val viewModel: MessageViewModel by viewModels()
    private val authRepo = AuthRepository()
    private lateinit var adapter: ConversationAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ===============================
        // 🔹 STATUS BAR SPACING
        // ===============================
        val appBar = view.findViewById<AppBarLayout>(R.id.appBarLayout)
        ViewCompat.setOnApplyWindowInsetsListener(appBar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, statusBarHeight, v.paddingRight, v.paddingBottom)
            insets
        }

        // ===============================
        // 🔹 VIEW REFS
        // ===============================
        val rvConversations = view.findViewById<RecyclerView>(R.id.rvConversations)
        val etSearch = view.findViewById<TextInputEditText>(R.id.etSearch)
        val chipAll = view.findViewById<TextView>(R.id.chipAll)
        val chipUnread = view.findViewById<LinearLayout>(R.id.chipUnread)
        val tvChipUnreadLabel = view.findViewById<TextView>(R.id.tvChipUnreadLabel)
        val tvUnreadBadgeCount = view.findViewById<TextView>(R.id.tvUnreadBadgeCount)
        val emptyState = view.findViewById<View>(R.id.emptyState)
        val ivCompose = view.findViewById<FloatingActionButton>(R.id.fabNewMessage)

        // ===============================
        // 🔹 ADAPTER
        // ===============================
        adapter = ConversationAdapter(
            onItemClick = { item -> openChat(item) },
            onItemLongClick = { item ->
                val name = item.otherUser.fullName ?: "@${item.otherUser.username}"
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Hapus chat")
                    .setMessage("Hapus semua percakapan dengan $name?")
                    .setPositiveButton("Hapus") { _, _ ->
                        viewModel.deleteConversation(item.conversation.id)
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        )
        rvConversations.adapter = adapter
        // Matiin change animation (fade) bawaan RecyclerView → tidak kedip saat refresh
        (rvConversations.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
            ?.supportsChangeAnimations = false

        // ===============================
        // 🔹 STATE
        // ===============================
        var showUnreadOnly = false
        var searchQuery = ""

        fun applyFilter(list: List<ConversationWithUser>) {
            val filtered = list
                .filter { !showUnreadOnly || it.unreadCount > 0 }
                .filter {
                    if (searchQuery.isBlank()) true
                    else (it.otherUser.fullName ?: it.otherUser.username)
                        .contains(searchQuery, ignoreCase = true)
                }
            adapter.submitList(filtered)
            emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE

            // Badge count di chip "Belum dibaca"
            val totalUnread = list.count { it.unreadCount > 0 }
            if (totalUnread > 0) {
                tvUnreadBadgeCount.visibility = View.VISIBLE
                tvUnreadBadgeCount.text = if (totalUnread > 99) "99+" else totalUnread.toString()
            } else {
                tvUnreadBadgeCount.visibility = View.GONE
            }
        }

        // ===============================
        // 🔹 OBSERVERS
        // ===============================
        viewModel.conversations.observe(viewLifecycleOwner) { list ->
            applyFilter(list)
        }

        viewModel.error.observe(viewLifecycleOwner) {
            it?.let { msg -> Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show() }
        }

        viewModel.openConversation.observe(viewLifecycleOwner) { conv ->
            conv ?: return@observe
            val currentUserId = authRepo.getCurrentUser()?.id ?: return@observe
            val otherUserId = if (conv.user1Id == currentUserId) conv.user2Id else conv.user1Id
            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conv.id)
                putExtra(ChatActivity.EXTRA_OTHER_USER_ID, otherUserId)
            }
            startActivity(intent)
            viewModel.clearOpenConversation()
        }

        // ===============================
        // 🔹 SEARCH
        // ===============================
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString()
                viewModel.conversations.value?.let { applyFilter(it) }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // ===============================
        // 🔹 FILTER CHIPS
        // ===============================
        chipAll.setOnClickListener {
            showUnreadOnly = false
            chipAll.setBackgroundResource(R.drawable.bg_chip_selected)
            chipAll.setTextColor(resources.getColor(android.R.color.white, null))
            chipUnread.setBackgroundResource(R.drawable.bg_chip_unselected)
            tvChipUnreadLabel.setTextColor(0xFFAAAAAA.toInt())
            viewModel.conversations.value?.let { applyFilter(it) }
        }

        chipUnread.setOnClickListener {
            showUnreadOnly = true
            chipUnread.setBackgroundResource(R.drawable.bg_chip_selected)
            tvChipUnreadLabel.setTextColor(resources.getColor(android.R.color.white, null))
            chipAll.setBackgroundResource(R.drawable.bg_chip_unselected)
            chipAll.setTextColor(0xFFAAAAAA.toInt())
            viewModel.conversations.value?.let { applyFilter(it) }
        }

        // ===============================
        // 🔹 COMPOSE
        // ===============================
        ivCompose.setOnClickListener {
            startActivity(Intent(requireContext(), NewMessageActivity::class.java))
        }

        // ===============================
        // 🔹 START REALTIME + LOAD
        // ===============================
        viewModel.startRealtimeAndLoad()
    }

    // Refresh langsung pas balik dari ChatActivity → instant
    override fun onResume() {
        super.onResume()
        viewModel.refreshNow()
    }

    private fun openChat(item: ConversationWithUser) {
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_CONVERSATION_ID, item.conversation.id)
            putExtra(ChatActivity.EXTRA_OTHER_USER_ID, item.otherUser.id)
            putExtra(ChatActivity.EXTRA_OTHER_USERNAME, item.otherUser.fullName ?: "@${item.otherUser.username}")
            putExtra(ChatActivity.EXTRA_OTHER_AVATAR_URL, item.otherUser.avatarUrl)
        }
        startActivity(intent)
    }

    private val Int.dp: Int
        get() = (this * Resources.getSystem().displayMetrics.density).toInt()
}
