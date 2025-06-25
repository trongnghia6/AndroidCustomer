package com.example.customerapp.presentation.userprofile


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch

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
                    .select(){
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
                val updateResponse = supabase
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
                val update = supabase.from("users")
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

    fun logout(onLogout: () -> Unit) {
        // Xử lý đăng xuất (xóa token, dữ liệu,...)
        onLogout()
    }
}
