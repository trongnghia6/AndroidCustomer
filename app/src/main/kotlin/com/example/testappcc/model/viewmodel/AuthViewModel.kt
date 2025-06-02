package com.example.testappcc.model.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.testappcc.core.supabase
import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

@Serializable
data class UsersSignUp(
    val email: String,
    val password: String,
    val role: String,
    val address: String
)
@Serializable
data class UserSignIn(
    val id : String,
    val email: String,
    val password: String
)

class AuthViewModel : ViewModel() {
    var isLoading by mutableStateOf(false)
        private set

    var authError by mutableStateOf<String?>(null)
    suspend fun countUsersByEmail(email: String): Int {
        return try {
            val count = supabase.from("users")
                .select(columns = Columns.list("email", "password", "role")) {
                    filter {
                        eq("email", email)
                    }
                }
                .decodeList<UsersSignUp>()
            if (count.isEmpty()){
                0
            }else{
                1
            }
        } catch (e: Exception) {
            Log.e("Supabase", "Error counting users: ${e.message}")
            0
        }
    }

    fun signIn(email: String, password: String, context: Context,onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val check =  supabase.from("users")
                        .select(columns = Columns.list("email", "password", "id")) {
                            filter {
                                eq("email", email)
                                eq("password", password)
                            }
                        }
                    .decodeList<UserSignIn>()
                if (check.isNotEmpty()){
                    val user = check.first() // Lấy người dùng đầu tiên trong danh sách
                    val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putString("user_id", user.id)
                        apply()
                    }
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                    Log.e("AuthViewModel", "${user.id}")
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
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val role = "provider"
                val check = countUsersByEmail(email)
                if (check == 0){
                    val newUser = UsersSignUp(email = email, password = password, role = role, address = address )
                    supabase.from("users").insert(newUser)
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
}
