package com.example.customerapp.presentation.search

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.customerapp.data.model.User
import com.example.customerapp.core.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun SearchScreen() {
    var searchName by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Kết quả sẽ hiển thị ở đây") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchName,
            onValueChange = { searchName = it },
            label = { Text("Nhập email hoặc tên người dùng") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (searchName.isNotEmpty()) {
                    fetchUsersByName(searchName) { newResult ->
                        result = newResult
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Tìm kiếm")
        }
        Text(
            text = result,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun fetchUsersByName(name: String, onResult: (String) -> Unit) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val response = supabase.postgrest
                .rpc("get_users_by_name", parameters = buildJsonObject {
                    put("search_name", name)
                })
                .decodeAs<List<User>>()
            onResult(response.joinToString("\n") { user ->
                "${user.email} (${user.role})${user.username?.let { " - $it" } ?: ""}"
            })
        } catch (e: Exception) {
            onResult("Lỗi: ${e.message}")
        }
    }
}