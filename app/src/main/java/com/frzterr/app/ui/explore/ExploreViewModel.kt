package com.frzterr.app.ui.explore

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frzterr.app.data.repository.user.AppUser
import com.frzterr.app.data.repository.user.UserRepository
import com.frzterr.app.data.repository.auth.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ExploreViewModel : ViewModel() {
    
    private val userRepo = UserRepository()
    private val authRepo = AuthRepository()
    
    private val _searchResults = MutableLiveData<List<AppUser>>(emptyList())
    val searchResults: LiveData<List<AppUser>> = _searchResults
    
    private val _isSearching = MutableLiveData<Boolean>(false)
    val isSearching: LiveData<Boolean> = _isSearching
    
    private var searchJob: Job? = null
    
    fun search(query: String) {
        searchJob?.cancel()
        
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        
        searchJob = viewModelScope.launch {
            try {
                _isSearching.value = true
                val currentUser = authRepo.getCurrentUser()
                val results = userRepo.searchUsers(query, excludeUserId = currentUser?.id)
                _searchResults.value = results
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _isSearching.value = false
    }
}
