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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.navigation.NavController
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    orderId: Int,
    viewModel: com.example.customerapp.presentation.viewmodel.OrderViewModel,
    navController: NavController
) {
    var booking by remember { mutableStateOf<Booking?>(null) }
    var providerService by remember { mutableStateOf<ProviderService?>(null) }
    var provider by remember { mutableStateOf<User?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    val providerRepo = remember { ProviderServiceRepository() }
    val scope = rememberCoroutineScope()

    fun loadAll() {
        scope.launch {
            isRefreshing = true
            try {
                val bookingResult = supabase.from("bookings").select {
                    filter {
                        eq("id", orderId)
                    }
                }.decodeList<Booking>()

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
                Log.e("OrderDetail", "Error loading booking detail: ${e.message}", e)
                booking = null
            } finally {
                isRefreshing = false
            }
        }
    }

    fun cancelOrder() {
        scope.launch {
            try {
                supabase.from("bookings").update({
                    set("status", "cancelled")
                }) {
                    filter {
                        eq("id", orderId)
                    }
                }
                // Sau khi huỷ thành công, quay về trang danh sách đơn hàng
                navController.navigate("orders_main") {
                    popUpTo("orders_main") { inclusive = true }
                }
            } catch (e: Exception) {
                Log.e("OrderDetail", "Error cancelling order: ${e.message}", e)
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

        Column(modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8F8))) {

            CenterAlignedTopAppBar(
                title = { Text("Chi tiết đơn hàng") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )

            Column(modifier = Modifier.padding(16.dp)) {
                // Status progress bar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    StatusBar(booking = booking!!)
                }

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
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (provider?.avatar != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(provider!!.avatar),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    provider?.name ?: "Provider",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    "SĐT: ${provider?.phone_number ?: "..."}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${providerService?.customPrice?.toInt() ?: 0}đ",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFFF5722),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        // Thông tin dịch vụ
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            InfoColumn(
                                label = "Loại dịch vụ",
                                value = providerService?.service?.name ?: "..."
                            )
                            InfoColumn(
                                label = "Số nhân viên",
                                value = "${booking!!.number_workers ?: 1} người"
                            )
                            InfoColumn(
                                label = "Thời gian",
                                value = "${providerService?.durationMinutes ?: 0} phút"
                            )
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
                            Text(
                                when (booking!!.status) {
                                    "pending" -> "Chờ xác nhận"
                                    "confirmed" -> "Đã xác nhận"
                                    "completed" -> "Đã hoàn thành"
                                    else -> "Không xác định"
                                },
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
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

                // Cancel button
                if (booking!!.status == "pending") {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Huỷ đơn hàng", color = Color.White)
                    }
                }
            }
        }

        // Cancel confirmation dialog
        if (showCancelDialog) {
            AlertDialog(
                onDismissRequest = { showCancelDialog = false },
                title = { Text("Xác nhận huỷ đơn") },
                text = { Text("Bạn có chắc chắn muốn huỷ đơn hàng này?") },
                confirmButton = {
                    TextButton(onClick = {
                        cancelOrder()
                        showCancelDialog = false
                    }) {
                        Text("Đồng ý")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelDialog = false }) {
                        Text("Huỷ")
                    }
                }
            )
        }
    }
}

@Composable
private fun StatusStep(
    label: String,
    isActive: Boolean,
    isCompleted: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    when {
                        isActive -> MaterialTheme.colorScheme.primary
                        isCompleted -> Color(0xFF4CAF50) // Màu xanh lá
                        else -> Color.LightGray
                    },
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isCompleted) Icons.Default.Check
                else if (isActive) Icons.Default.Check
                else Icons.Default.Close,
                contentDescription = null,
                tint = Color.White
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
        )
    }
}

@Composable
private fun StatusBar(booking: Booking) {
    Column(Modifier.padding(16.dp)) {
        Text(
            "Trạng thái đơn hàng",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chờ xác nhận
            StatusStep(
                "Chờ xác nhận",
                isActive = booking.status == "pending",
                isCompleted = booking.status != "pending" && booking.status != "cancelled"
            )

            // Đường kẻ 1
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(
                        when {
                            booking.status == "cancelled" -> Color.Red
                            booking.status == "pending" -> Color.LightGray
                            else -> Color(0xFF4CAF50)
                        }
                    )
            )

            // Đã xác nhận
            StatusStep(
                "Đã xác nhận",
                isActive = booking.status == "confirmed",
                isCompleted = booking.status == "completed"
            )

            // Đường kẻ 2
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(
                        when {
                            booking.status == "completed" -> Color(0xFF4CAF50)
                            else -> Color.LightGray
                        }
                    )
            )

            // Đã hoàn thành
            StatusStep(
                "Đã hoàn thành",
                isActive = booking.status == "completed",
                isCompleted = false
            )
        }

        if (booking.status == "cancelled") {
            Spacer(Modifier.height(8.dp))
            Text(
                "Đơn hàng đã bị huỷ",
                color = Color.Red,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun ServiceInfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.Black
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun InfoColumn(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}
