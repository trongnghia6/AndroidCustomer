package com.example.testappcc.presentation.orders

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testappcc.core.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.testappcc.data.model.Booking
import com.example.testappcc.data.repository.ProviderServiceRepository
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun OrdersScreen(
    onOrderClick: (String) -> Unit
) {
    val context = LocalContext.current
    val userId = remember { context.getSharedPreferences("user_session", Context.MODE_PRIVATE).getString("user_id", null) }
    var orders by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Đang đến", "Lịch sử", "Đã huỷ", "Đánh giá")

    LaunchedEffect(userId) {
        if (userId != null) {
            isLoading = true
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = supabase.from("bookings").select().decodeList<Booking>()
                    orders = result.filter { it.customer_id == userId }
                    isLoading = false
                } catch (e: Exception) {
                    error = e.message
                    isLoading = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Đơn hàng", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex = selectedTab, containerColor = Color.White) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Text("Lỗi: $error", color = Color.Red, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            else -> {
                when (selectedTab) {
                    0 -> OrderList(orders.filter { it.status == "pending" || it.status == "confirmed" }, onOrderClick)
                    1 -> OrderList(orders.filter { it.status == "completed" }, onOrderClick)
                    2 -> OrderList(orders.filter { it.status == "cancelled" }, onOrderClick)
                    3 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Hệ thống đang phát triển", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun OrderList(orderList: List<Booking>, onOrderClick: (String) -> Unit) {
    val providerRepo = remember { ProviderServiceRepository() }
    if (orderList.isEmpty()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Quên chưa đặt đơn rồi nè bạn ơi?", fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text("Quay về trang chủ và nhanh tay đặt đơn để chúng mình phục vụ cậu nhé!", color = Color.Gray)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(orderList) { order ->
                var providerName by remember { mutableStateOf<String?>(null) }
                // Lấy tên provider
                LaunchedEffect(order.provider_service_id) {
                    val provider = providerRepo.getProviderServiceById(order.provider_service_id)
                    providerName = provider?.user?.name ?: "Không rõ"
                }
                val statusText = when(order.status) {
                    "pending" -> "Chờ xác nhận"
                    "confirmed" -> "Đã xác nhận"
                    "completed" -> "Đã hoàn thành"
                    "cancelled" -> "Đã huỷ"
                    else -> order.status
                }
                val statusColor = when(order.status) {
                    "pending" -> Color(0xFFFFA000)
                    "confirmed" -> Color(0xFF1976D2)
                    "completed" -> Color(0xFF388E3C)
                    "cancelled" -> Color(0xFFE53935)
                    else -> Color.Gray
                }
                // Format thời gian
                val createdAt = order.created_at
                val formattedTime = try {
                    if (createdAt != null) {
                        val instant = java.time.OffsetDateTime.parse(createdAt)
                        instant.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    } else "-"
                } catch (e: Exception) { createdAt ?: "-" }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOrderClick(order.id.toString()) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Mã đơn: ${order.id}", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
                        Spacer(Modifier.height(2.dp))
                        Text("Provider: ${providerName ?: "..."}", color = Color(0xFF1976D2), fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                        Spacer(Modifier.height(6.dp))
                        Text("Địa chỉ: ${order.location ?: "Không rõ"}", fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                        Spacer(Modifier.height(6.dp))
                        Text("Trạng thái: $statusText", color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                        Spacer(Modifier.height(6.dp))
                        Text("Thời gian tạo: $formattedTime", fontSize = MaterialTheme.typography.bodySmall.fontSize, color = Color.Gray)
                    }
                }
            }
        }
    }
}