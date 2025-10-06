package com.example.customerapp.ui.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.customerapp.core.supabase
import com.example.customerapp.data.repository.NotificationService
import android.util.Log
import androidx.core.content.edit
import com.example.customerapp.data.model.auth.AuthDtos
import com.example.customerapp.data.repository.AuthRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.auth.auth



class AuthViewModel : ViewModel() {
    private val notificationService = NotificationService()

    private val authRepository = AuthRepository()
    
    var isLoading by mutableStateOf(false)
        private set

    var authError by mutableStateOf<String?>(null)
    var isSignUpSuccess by mutableStateOf<Boolean?>(null)
        private set

    suspend fun countUsersByEmail(email: String): Int = authRepository.countCustomerUsersByEmail(email)

    fun signIn(email: String, password: String, context: Context,onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user =  authRepository.signInCustomer(email, password)
                if (user != null){
                    if (user.lock == "active"){
//                        isSignUpSuccess = true
//                        authError = null
                        val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
                        val userId = user.id
                        val userName = user.name
                        sharedPref.edit {
                            putString("user_id", userId)
                            putString("username", userName)
                        }

                        // Gửi thông báo đăng nhập thành công và generate FCM token
                        launch {
                            try {
                                val success = notificationService.sendLoginSuccessNotification(userId, userName)
                                if (success) {
                                    Log.d("AuthViewModel", "Login success notification sent to user: $userName")
                                } else {
                                    Log.w("AuthViewModel", "Failed to send login success notification")
                                }
                            } catch (e: Exception) {
                                Log.e("AuthViewModel", "Error sending login notification: ${e.message}")
                            }
                        }

                        // Generate and upload FCM token for push notifications
                        withContext(Dispatchers.Main) {
                            authRepository.uploadFcmToken(context, userId)
                        }

                        withContext(Dispatchers.Main) {
                            onSuccess()
                        }
                    }else{
                        authError = "Tài khoản đã bị khoá. Vui lòng liên hệ quản trị viên qua email abc@gmail.com."
                    }
                }else{
                    Log.e("AuthViewModel", "Sign in failed - No user returned")
                    authError = "Không tìm thấy thông tin người dùng"
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Sign in error: ${e.message}", e)
                authError = "Đăng nhập thất bại: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun signUp(
        email: String,
        password: String,
        address: String,
        name: String,
        phoneNumber: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val role = "customer"
                val check = countUsersByEmail(email)
                if (check == 0){
                    val newUser = AuthDtos.UsersSignUp(
                        email = email,
                        password = password,
                        role = role,
                        address = address,
                        name = name,
                        phoneNumber = phoneNumber
                    )
                    authRepository.signUpCustomer(newUser)
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                }else{
                    authError = "Email đã được đăng ký trước đó"
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Sign up error: ${e.message}", e)
                authError = "Đăng ký thất bại: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }


    fun clearError() {
        authError = null
    }

    /**
     * Hàm xử lý đăng xuất hoàn chỉnh
     * - Xóa tất cả dữ liệu SharedPreferences
     * - Xóa Supabase auth session
     * - Xóa FCM token khỏi database (tùy chọn)
     */
    fun logout(context: Context, onLogoutComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("AuthViewModel", "Starting logout process...")
                
                // 1. Lấy thông tin user trước khi xóa SharedPreferences
                val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
                val userId = sharedPref.getString("user_id", null)
                
                // 2. Xóa FCM token khỏi database (tùy chọn)
                if (!userId.isNullOrEmpty()) {
                    try {
                        supabase.from("user_push_tokens")
                            .delete {
                                filter {
                                    eq("user_id", userId)
                                }
                            }
                        Log.d("AuthViewModel", "FCM tokens deleted for user: $userId")
                    } catch (e: Exception) {
                        Log.w("AuthViewModel", "Could not delete FCM tokens: ${e.message}")
                        // Không throw error vì việc này không quan trọng lắm
                    }
                }
                
                // 3. Xóa tất cả dữ liệu SharedPreferences
                withContext(Dispatchers.Main) {
                    sharedPref.edit{
                        clear()
                    }
                    Log.d("AuthViewModel", "SharedPreferences cleared")
                }
                
                // 4. Xóa Supabase auth session
                try {
                    supabase.auth.signOut()
                    Log.d("AuthViewModel", "Supabase session cleared")
                } catch (e: Exception) {
                    Log.w("AuthViewModel", "Could not clear Supabase session: ${e.message}")
                    // Tiếp tục logout dù có lỗi
                }
                
                // 5. Reset các state variables
                withContext(Dispatchers.Main) {
                    authError = null
                    isSignUpSuccess = null
                    isLoading = false
                    
                    Log.d("AuthViewModel", "Logout completed successfully")
                    onLogoutComplete()
                }
                
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error during logout: ${e.message}", e)
                
                // Ngay cả khi có lỗi, vẫn cố gắng xóa SharedPreferences và navigate
                withContext(Dispatchers.Main) {
                    try {
                        val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
                        sharedPref.edit {
                            clear()
                        }
                        Log.d("AuthViewModel", "SharedPreferences cleared (fallback)")
                    } catch (clearError: Exception) {
                        Log.e("AuthViewModel", "Failed to clear SharedPreferences: ${clearError.message}")
                    }
                    
                    authError = null
                    isSignUpSuccess = null
                    isLoading = false
                    onLogoutComplete()
                }
            }
        }
    }
}
