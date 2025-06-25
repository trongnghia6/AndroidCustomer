package com.example.customerapp.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customerapp.data.model.User
import com.example.customerapp.data.repository.AvatarRepository
import com.example.customerapp.core.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AvatarViewModel : ViewModel() {
    private val avatarRepository = AvatarRepository()
    
    var currentUser by mutableStateOf<User?>(null)
        private set
    
    var isUploading by mutableStateOf(false)
        private set
    
    var uploadProgress by mutableStateOf(0f)
        private set
    
    var error by mutableStateOf<String?>(null)
        private set
    
    var showImagePicker by mutableStateOf(false)
        private set
    
    // Lấy thông tin user hiện tại
    fun loadCurrentUser(context: Context) {
        val userId = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getString("user_id", null)
            
        if (userId.isNullOrEmpty()) {
            error = "Không tìm thấy thông tin người dùng"
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = supabase.from("users").select {
                    filter {
                        eq("id", userId)
                    }
                }.decodeSingle<User>()
                
                withContext(Dispatchers.Main) {
                    currentUser = user
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "Lỗi tải thông tin người dùng: ${e.message}"
                }
            }
        }
    }
    
    // Upload avatar mới
    fun uploadAvatar(context: Context, imageUri: Uri) {
        val userId = currentUser?.id
        if (userId == null) {
            error = "Không tìm thấy thông tin người dùng"
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isUploading = true
                uploadProgress = 0f
                error = null
            }
            
            try {
                // Simulate progress updates
                withContext(Dispatchers.Main) {
                    uploadProgress = 0.3f
                }
                
                val result = avatarRepository.uploadAndUpdateAvatar(context, userId, imageUri)
                
                withContext(Dispatchers.Main) {
                    uploadProgress = 0.8f
                }
                
                result.fold(
                    onSuccess = { avatarUrl ->
                        withContext(Dispatchers.Main) {
                            // Cập nhật currentUser với avatar mới
                            currentUser = currentUser?.copy(avatar = avatarUrl)
                            uploadProgress = 1f
                            isUploading = false
                        }
                    },
                    onFailure = { exception ->
                        withContext(Dispatchers.Main) {
                            error = exception.message
                            isUploading = false
                            uploadProgress = 0f
                        }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "Lỗi upload avatar: ${e.message}"
                    isUploading = false
                    uploadProgress = 0f
                }
            }
        }
    }
    
    // Xóa avatar
    fun removeAvatar(context: Context) {
        val userId = currentUser?.id
        if (userId == null) {
            error = "Không tìm thấy thông tin người dùng"
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isUploading = true
                error = null
            }
            
            try {
                val result = avatarRepository.removeAvatar(userId)
                
                result.fold(
                    onSuccess = {
                        withContext(Dispatchers.Main) {
                            // Cập nhật currentUser với avatar = null
                            currentUser = currentUser?.copy(avatar = null)
                            isUploading = false
                        }
                    },
                    onFailure = { exception ->
                        withContext(Dispatchers.Main) {
                            error = exception.message
                            isUploading = false
                        }
                    }
                )
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "Lỗi xóa avatar: ${e.message}"
                    isUploading = false
                }
            }
        }
    }
    
    // Show/hide image picker
    fun showImagePicker() {
        showImagePicker = true
    }
    
    fun hideImagePicker() {
        showImagePicker = false
    }
    
    // Clear error
    fun clearError() {
        error = null
    }
} 