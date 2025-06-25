package com.example.customerapp.presentation.orders

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.customerapp.data.model.Booking
import com.example.customerapp.data.model.ProviderService
import com.example.customerapp.data.model.User
import com.example.customerapp.data.repository.ProviderServiceRepository
import com.example.customerapp.presentation.components.SwipeRefreshLayout
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import com.example.customerapp.core.supabase
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Home
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun OrderDetailScreen(
    orderId: Int,
    viewModel: com.example.customerapp.presentation.viewmodel.OrderViewModel
) {
    var booking by remember { mutableStateOf<Booking?>(null) }
    var providerService by remember { mutableStateOf<ProviderService?>(null) }
    var provider by remember { mutableStateOf<User?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val providerRepo = remember { ProviderServiceRepository() }
    val scope = rememberCoroutineScope()

    fun loadAll() {
        scope.launch {
            isRefreshing = true
            try {
                val bookingResult = supabase.from("bookings").select {
                    filter {
                        eq("id", orderId) // Lấy booking theo ID
                    }
                }.decodeList<Booking>() // Giải mã thành một đối tượng Booking

                if (bookingResult.isNotEmpty()) {
                    booking = bookingResult.first()
                } else {
                    Log.e("OrderDetail", "No booking found with id = $orderId")
                    booking = null
                }
                val providerServiceResult = providerRepo.getProviderServiceById(booking?.provider_service_id
                    ?: 0)
                providerService = providerServiceResult
                provider = providerServiceResult?.user
            } catch (e: Exception) {
                // Xử lý lỗi nếu không tìm thấy booking hoặc lỗi khác
                Log.e("OrderDetail", "Error loading booking detail: ${e.message}", e)
                booking = null // Đặt booking về null để hiển thị trạng thái lỗi hoặc rỗng
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(orderId) {
        loadAll()
    }

    SwipeRefreshLayout(isRefreshing = isRefreshing, onRefresh = { loadAll() }) {
        if (booking == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@SwipeRefreshLayout
        }
        val statusSteps = listOf("Đã đặt đơn", "Đang chuẩn bị", "Đang giao", "Đã nhận hàng")
        val statusIcons = listOf(
            Icons.Default.ShoppingCart,
            Icons.Default.ShoppingCart,
            Icons.Default.ShoppingCart,
            Icons.Default.Home
        )
        val statusIndex = when (booking!!.status) {
            "pending" -> 0
            "confirmed" -> 1
            "delivering" -> 2
            "completed" -> 3
            else -> 0
        }
        Column(modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8F8))
            .padding(16.dp)) {

            // Tiến trình trạng thái
            Spacer(Modifier.height(16.dp))

            // Địa chỉ
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF1976D2))
                        Spacer(Modifier.width(8.dp))
                        Text("Từ: ${provider?.address ?: "..."}", fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFE53935))
                        Spacer(Modifier.width(8.dp))
                        Text("Đến: ${booking!!.location ?: "..."}", fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Thông tin Provider/Dịch vụ
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (provider?.avatar != null) {
                        Image(
                            painter = rememberAsyncImagePainter(provider!!.avatar),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(provider?.name ?: "Provider", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("SĐT: ${provider?.phone_number ?: "..."}", color = Color.Gray, fontSize = 14.sp)
                        Text("Giá: ${providerService?.customPrice?.toInt() ?: 0}đ", color = Color(0xFFFF5722), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Thông tin đơn hàng
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Text("Trạng thái:", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(statusSteps[statusIndex], color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row {
                        Text("Thời gian tạo:", modifier = Modifier.weight(1f))
                        val createdAtString = booking!!.created_at
                        val formattedTime = try {
                            if (createdAtString != null) {
                                val offsetDateTime = OffsetDateTime.parse(createdAtString, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                offsetDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                            } else "..."
                        } catch (e: Exception) {
                            "Lỗi thời gian"
                        }
                        Text(formattedTime)
                    }
                    Row {
                        Text("Ghi chú:", modifier = Modifier.weight(1f))
                        Text(booking!!.description ?: "Không có")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusProgressBarWithIcon(steps: List<String>, icons: List<ImageVector>, currentStep: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        steps.forEachIndexed { idx, label ->
            val color = if (idx <= currentStep) Color(0xFFFF5722) else Color.LightGray
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icons[idx], contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(label, color = color, fontSize = 12.sp)
            }
            if (idx < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(32.dp)
                        .background(if (idx < currentStep) Color(0xFFFF5722) else Color.LightGray)
                )
            }
        }
    }
}