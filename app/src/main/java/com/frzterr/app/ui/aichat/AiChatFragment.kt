package com.frzterr.app.ui.aichat

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.frzterr.app.R
import com.frzterr.app.databinding.FragmentAiChatBinding

class AiChatFragment : Fragment(R.layout.fragment_ai_chat) {

    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AiChatViewModel by viewModels()
    private lateinit var chatAdapter: AiChatAdapter
    private lateinit var historyAdapter: ChatHistoryAdapter

    /** True kalau user sengaja scroll ke atas — pause auto-scroll sementara */
    private var userScrolledUp = false

    // ── Drawer views (accessed via findViewById on drawer's child view) ──
    private var btnNewChatDrawer: ImageButton? = null
    private var rvChatHistory: RecyclerView? = null
    private var btnClearAll: LinearLayout? = null

    // ─────────────────────────────────────────────────────────────────────


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAiChatBinding.bind(view)

        // Bind drawer views from the second child of DrawerLayout
        val drawerView = binding.drawerLayout.getChildAt(1)
        btnNewChatDrawer = drawerView?.findViewById<ImageButton>(R.id.btnNewChatDrawer)
        rvChatHistory = drawerView?.findViewById<RecyclerView>(R.id.rvChatHistory)
        btnClearAll = drawerView?.findViewById<LinearLayout>(R.id.btnClearAll)

        applyInsets()
        setupChatRecyclerView()
        setupHistoryDrawer()
        setupListeners()
        setupObservers()
    }

    private fun applyInsets() {
        val bottomNavPx = (70 * resources.displayMetrics.density).toInt()
        val mainContent = binding.drawerLayout.getChildAt(0)

        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime  = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Saat keyboard muncul: ime.bottom >> bars.bottom, jadi pakai ime.bottom
            // Saat keyboard tutup: ime.bottom == 0, pakai bars.bottom + tinggi nav bar bawah
            val bottomPadding = if (ime.bottom > bars.bottom) ime.bottom else bars.bottom + bottomNavPx

            mainContent?.setPadding(bars.left, bars.top, bars.right, bottomPadding)
            insets
        }
    }

    private fun setupChatRecyclerView() {
        chatAdapter = AiChatAdapter()
        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = chatAdapter

        // Animasi fade-in ala ChatGPT saat item baru muncul
        val animator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
            addDuration    = 250
            changeDuration = 0   // Hindari flicker saat chunk streaming update
        }
        binding.rvMessages.itemAnimator = animator

        // Deteksi scroll manual user: kalau scroll ke atas → tahan auto-scroll
        binding.rvMessages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val lastVisible = lm.findLastCompletelyVisibleItemPosition()
                val lastIndex   = chatAdapter.itemCount - 1
                // Kalau user scroll ke atas (dy < 0) dan belum di posisi paling bawah
                userScrolledUp = dy < 0 && lastVisible < lastIndex
            }
        })
    }

    private fun setupHistoryDrawer() {
        historyAdapter = ChatHistoryAdapter(
            sessions = mutableListOf(),
            onSessionClick = { session ->
                viewModel.switchToSession(session.id)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                // Re-populate chat view with switched session's messages
                chatAdapter = AiChatAdapter()
                viewModel.messages.value?.let { chatAdapter.setMessages(it) }
                binding.rvMessages.adapter = chatAdapter
                showMessages()
            },
            onDeleteClick = { session ->
                viewModel.deleteSession(session.id)
            }
        )

        rvChatHistory?.layoutManager = LinearLayoutManager(requireContext())
        rvChatHistory?.adapter = historyAdapter

        btnNewChatDrawer?.setOnClickListener {
            resetToNewChat()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        btnClearAll?.setOnClickListener {
            viewModel.clearAllHistory()
            resetToNewChat()
        }
    }

    private fun resetToNewChat() {
        viewModel.startNewChat()
        chatAdapter = AiChatAdapter()
        binding.rvMessages.adapter = chatAdapter
        binding.layoutEmptyState.visibility = View.VISIBLE
        binding.rvMessages.visibility = View.GONE
    }

    private fun setupListeners() {
        // Open drawer
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // New chat (header button)
        binding.btnNewChat.setOnClickListener { resetToNewChat() }

        // Model selector → custom bottom sheet
        binding.layoutModelPill.setOnClickListener {
            val currentModelId = viewModel.selectedModel.value?.modelId
                ?: NVIDIA_MODELS[0].modelId
            ModelSelectorBottomSheet(
                currentModelId = currentModelId,
                onModelSelected = { model -> viewModel.selectModel(model) }
            ).show(requireActivity().supportFragmentManager, ModelSelectorBottomSheet.TAG)
        }

        // Send
        binding.btnSend.setOnClickListener { sendMessage() }
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }
    }

    private fun setupObservers() {
        viewModel.messages.observe(viewLifecycleOwner) { msgs ->
            if (msgs.isEmpty()) return@observe
            chatAdapter.setMessages(msgs)
            // Auto-scroll ke bawah selama user belum scroll ke atas
            if (!userScrolledUp) {
                val lastIndex = chatAdapter.itemCount - 1
                if (lastIndex >= 0) {
                    binding.rvMessages.post {
                        binding.rvMessages.smoothScrollToPosition(lastIndex)
                    }
                }
            }
        }

        viewModel.sessions.observe(viewLifecycleOwner) { sessions ->
            historyAdapter.updateSessions(sessions)
        }

        viewModel.selectedModel.observe(viewLifecycleOwner) { model ->
            binding.tvModelName.text = model.displayName
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.layoutTyping.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnSend.isEnabled = !loading
            binding.btnSend.alpha = if (loading) 0.5f else 1f
            // Reset scroll flag tiap kali mulai generate — biar streaming selalu ke bawah
            if (loading) userScrolledUp = false
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            if (err != null) {
                Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text?.toString() ?: return
        if (text.isBlank()) return
        binding.etMessage.setText("")
        viewModel.sendMessage(text)
        showMessages()
    }

    private fun showMessages() {
        if (binding.layoutEmptyState.visibility == View.VISIBLE) {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvMessages.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
