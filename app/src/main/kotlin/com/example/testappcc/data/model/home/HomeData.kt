package com.example.testappcc.data.model.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.testappcc.data.model.Item
import com.example.testappcc.core.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

@Composable
fun RenderHome(){
    var items by remember { mutableStateOf<List<Item>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val fetchedItems = supabase.from("bookings")
                .select(columns = Columns.list("service_type"))
                .decodeList<Item>()
            items = fetchedItems
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }
}