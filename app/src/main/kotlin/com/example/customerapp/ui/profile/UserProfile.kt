package com.example.customerapp.ui.profile


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.jan.supabase.auth.auth

class UserViewModel : ViewModel() {
    var user by mutableStateOf<User?>(null)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    var isEditing by mutableStateOf(false)
        private set

    fun loadUserById(userId: String) {
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                val response = supabase
                    .from("users")
                    .select {
                        filter {
                            eq("id", userId)
                        }
                    }
                    .decodeSingle<User>()

                user = response
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun changePassword(
        idUser: String,
        currentPassword: String,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // 1. Kiểm tra mật khẩu hiện tại đúng không
                val checkResponse = supabase
                    .from("users")
                    .select(columns = Columns.list("password")) {
                        filter {
                            eq("id", idUser)
                        }
                    }
                    .decodeSingle<Map<String, String>>()

                val savedPassword = checkResponse["password"]
                if (savedPassword == null || savedPassword != currentPassword) {
                    onError("Mật khẩu hiện tại không đúng")
                    return@launch
                }

                // 2. Update mật khẩu mới
                supabase
                    .from("users")
                    .update(mapOf("password" to newPassword)) {
                        filter {
                            eq("id", idUser)
                        }
                    }
                onSuccess()
            } catch (e: Exception) {
                onError("Lỗi hệ thống: ${e.message}")
            }
        }
    }

    fun toggleEdit() {
        isEditing = !isEditing
    }

    fun updateUser(newUser: User,
                   onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                supabase.from("users")
                    .update(newUser){
                        filter {
                            eq("id", newUser.id)
                        }
                    }
                isEditing = false
                user = newUser
            }catch (e: Exception){
                onError("Lỗi hệ thống: ${e.message}")
            }
        }
    }

    /**
     * Hàm xử lý đăng xuất hoàn chỉnh
     * - Xóa tất cả dữ liệu SharedPreferences
     * - Xóa Supabase auth session
     * - Xóa FCM token khỏi database
     * - Reset các state
     */
    fun logout(context: Context, onLogoutComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("UserViewModel", "Starting logout process...")
                
                // 1. Lấy thông tin user trước khi xóa SharedPreferences
                val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
                val userId = sharedPref.getString("user_id", null)
                val userName = sharedPref.getString("username", null)
                
                Log.d("UserViewModel", "Logging out user: $userName (ID: $userId)")
                
                // 2. Xóa FCM token khỏi database
                if (!userId.isNullOrEmpty()) {
                    try {
                        supabase.from("user_push_tokens")
                            .delete {
                                filter {
                                    eq("user_id", userId)
                                }
                            }
                        Log.d("UserViewModel", "FCM tokens deleted for user: $userId")
                    } catch (e: Exception) {
                        Log.w("UserViewModel", "Could not delete FCM tokens: ${e.message}")
                        // Không throw error vì việc này không quan trọng lắm
                    }
                }
                
                // 3. Xóa tất cả dữ liệu SharedPreferences
                withContext(Dispatchers.Main) {
                    sharedPref.edit{
                        clear()
                    }
                    Log.d("UserViewModel", "All SharedPreferences data cleared")
                }
                
                // 4. Xóa Supabase auth session
                try {
                    supabase.auth.signOut()
                    Log.d("UserViewModel", "Supabase session cleared")
                } catch (e: Exception) {
                    Log.w("UserViewModel", "Could not clear Supabase session: ${e.message}")
                    // Tiếp tục logout dù có lỗi
                }
                
                // 5. Reset các state variables
                withContext(Dispatchers.Main) {
                    user = null
                    isLoading = false
                    errorMessage = null
                    isEditing = false
                    
                    Log.d("UserViewModel", "Logout completed successfully - user: $userName")
                    onLogoutComplete()
                }
                
            } catch (e: Exception) {
                Log.e("UserViewModel", "Error during logout: ${e.message}", e)
                
                // Ngay cả khi có lỗi, vẫn cố gắng xóa SharedPreferences và navigate
                withContext(Dispatchers.Main) {
                    try {
                        val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
                        sharedPref.edit{
                            clear()
                        }
                        Log.d("UserViewModel", "SharedPreferences cleared (fallback)")
                    } catch (clearError: Exception) {
                        Log.e("UserViewModel", "Failed to clear SharedPreferences: ${clearError.message}")
                    }
                    
                    // Reset state dù có lỗi
                    user = null
                    isLoading = false
                    errorMessage = null
                    isEditing = false
                    
                    onLogoutComplete()
                }
            }
        }
    }

    /**
     * Hàm test để verify logout success
     * Kiểm tra xem tất cả dữ liệu SharedPreferences đã được xóa chưa
     */
    fun verifyLogoutSuccess(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        
        val userId = sharedPref.getString("user_id", null)
        val userName = sharedPref.getString("username", null)
        val pendingFcmToken = sharedPref.getString("pending_fcm_token", null)
        
        val isLoggedOut = userId.isNullOrEmpty() && 
                         userName.isNullOrEmpty() && 
                         pendingFcmToken.isNullOrEmpty()
        
        Log.d("UserViewModel", "=== LOGOUT VERIFICATION ===")
        Log.d("UserViewModel", "user_id: ${userId ?: "NULL"}")
        Log.d("UserViewModel", "username: ${userName ?: "NULL"}")
        Log.d("UserViewModel", "pending_fcm_token: ${pendingFcmToken ?: "NULL"}")
        Log.d("UserViewModel", "Is completely logged out: $isLoggedOut")
        Log.d("UserViewModel", "Current user state: ${user?.name ?: "NULL"}")
        Log.d("UserViewModel", "===========================")
        
        return isLoggedOut
    }
}
