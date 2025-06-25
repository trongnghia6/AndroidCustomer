package com.example.customerapp.presentation.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customerapp.data.model.Conversation
import com.example.customerapp.data.model.User
import com.example.customerapp.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatListViewModel : ViewModel() {
    private val chatRepository = ChatRepository()
    
    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set
    
    var searchResults by mutableStateOf<List<User>>(emptyList())
        private set
    
    var searchQuery by mutableStateOf("")
        private set
    
    var isLoading by mutableStateOf(false)
        private set
        
    var isSearching by mutableStateOf(false)
        private set
    
    var error by mutableStateOf<String?>(null)
        private set
    
    private var searchJob: Job? = null
    
    // Lấy danh sách conversation
    fun loadConversations(context: Context) {
        val userId = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getString("user_id", null)
            
        if (userId.isNullOrEmpty()) {
            error = "Không tìm thấy thông tin người dùng"
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isLoading = true
                error = null
            }
            
            try {
                val result = chatRepository.getConversations(userId)
                withContext(Dispatchers.Main) {
                    conversations = result
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.message
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }
    
    // Tìm kiếm người dùng
    fun searchUsers(query: String, context: Context) {
        viewModelScope.launch(Dispatchers.Main) {
            searchQuery = query
        }
        
        val userId = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getString("user_id", null)
            
        if (userId.isNullOrEmpty()) return
        
        // Cancel previous search job
        searchJob?.cancel()
        
        if (query.trim().isEmpty()) {
            viewModelScope.launch(Dispatchers.Main) {
                searchResults = emptyList()
                isSearching = false
            }
            return
        }
        
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isSearching = true
            }
            delay(300) // Debounce search
            
            try {
                val result = chatRepository.searchUsers(query, userId)
                withContext(Dispatchers.Main) {
                    searchResults = result
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.message
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSearching = false
                }
            }
        }
    }
    
    // Không cần tạo conversation - chỉ cần clear search và navigate
    fun onUserSelected() {
        viewModelScope.launch(Dispatchers.Main) {
            searchQuery = ""
            searchResults = emptyList()
        }
    }
    
    fun clearSearch() {
        viewModelScope.launch(Dispatchers.Main) {
            searchQuery = ""
            searchResults = emptyList()
            isSearching = false
            searchJob?.cancel()
        }
    }
} 