package com.frzterr.app.ui.explore

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.frzterr.app.R
import com.frzterr.app.data.local.SearchHistoryStore
import com.frzterr.app.data.repository.user.AppUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ExploreFragment : Fragment(R.layout.fragment_explore) {
    
    private val viewModel: ExploreViewModel by viewModels()
    
    private lateinit var etSearch: EditText
    private lateinit var btnBack: ImageView
    private lateinit var btnClear: ImageView
    private lateinit var recentSection: LinearLayout
    private lateinit var rvRecent: RecyclerView
    private lateinit var emptyRecent: TextView
    private lateinit var tvSeeAll: TextView
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var emptySearch: View
    
    private lateinit var searchAdapter: UserSearchAdapter
    private lateinit var recentAdapter: UserSearchAdapter
    
    private var searchJob: Job? = null
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        etSearch = view.findViewById(R.id.etSearch)
        btnBack = view.findViewById(R.id.btnBack)
        btnClear = view.findViewById(R.id.btnClear)
        recentSection = view.findViewById(R.id.recentSection)
        rvRecent = view.findViewById(R.id.rvRecent)
        emptyRecent = view.findViewById(R.id.emptyRecent)
        tvSeeAll = view.findViewById(R.id.tvSeeAll)
        rvSearchResults = view.findViewById(R.id.rvSearchResults)
        emptySearch = view.findViewById(R.id.emptySearch)
        
        setupAdapters()
        setupListeners()
        setupObservers()
        
        loadRecentSearches()
        
        // Auto-focus search
        etSearch.requestFocus()
    }
    
    private fun setupAdapters() {
        // Search results adapter
        searchAdapter = UserSearchAdapter(
            onUserClick = { userId, avatarUrl ->
                navigateToProfile(userId, avatarUrl)
            }
        )
        rvSearchResults.adapter = searchAdapter
        
        // Recent searches adapter (with remove button)
        recentAdapter = UserSearchAdapter(
            onUserClick = { userId, avatarUrl ->
                navigateToProfile(userId, avatarUrl)
            },
            onRemoveClick = { userId ->
                removeFromHistory(userId)
            }
        )
        rvRecent.adapter = recentAdapter
    }
    
    private fun setupListeners() {
        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        
        btnClear.setOnClickListener {
            etSearch.setText("")
        }
        
        // Real-time search with debounce
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                
                // Show/hide clear button
                btnClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                
                // Debounced search
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(300) // 300ms debounce
                    viewModel.search(query)
                }
            }
        })
    }
    
    private fun setupObservers() {
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            val query = etSearch.text.toString()
            
            if (query.isBlank()) {
                // Show recent section
                recentSection.visibility = View.VISIBLE
                rvSearchResults.visibility = View.GONE
                emptySearch.visibility = View.GONE
            } else {
                // Show search results
                recentSection.visibility = View.GONE
                rvSearchResults.visibility = View.VISIBLE
                
                if (results.isEmpty()) {
                    rvSearchResults.visibility = View.GONE
                    emptySearch.visibility = View.VISIBLE
                } else {
                    emptySearch.visibility = View.GONE
                    searchAdapter.submitList(results.map { SearchItem.UserItem(it) })
                }
            }
        }
    }
    
    private fun loadRecentSearches() {
        val recent = SearchHistoryStore.load(requireContext())
        
        if (recent.isEmpty()) {
            rvRecent.visibility = View.GONE
            emptyRecent.visibility = View.VISIBLE
        } else {
            rvRecent.visibility = View.VISIBLE
            emptyRecent.visibility = View.GONE
            recentAdapter.submitList(recent.map { SearchItem.RecentItem(it) })
        }
    }
    
    private fun removeFromHistory(userId: String) {
        SearchHistoryStore.remove(requireContext(), userId)
        loadRecentSearches()
    }
    
    private fun navigateToProfile(userId: String, avatarUrl: String?) {
        // Save to history
        lifecycleScope.launch {
            try {
                val userRepo = com.frzterr.app.data.repository.user.UserRepository()
                val user = userRepo.getUserByIdForce(userId)
                if (user != null) {
                    SearchHistoryStore.save(requireContext(), user)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        // Navigate to profile
        val bundle = Bundle().apply {
            putString("userId", userId)
            putString("avatarUrl", avatarUrl)
        }
        
        val currentUser = com.frzterr.app.data.repository.auth.AuthRepository().getCurrentUser()
        if (userId == currentUser?.id) {
            findNavController().navigate(R.id.profileFragment, bundle)
        } else {
            findNavController().navigate(R.id.publicProfileFragment, bundle)
        }
    }
}
