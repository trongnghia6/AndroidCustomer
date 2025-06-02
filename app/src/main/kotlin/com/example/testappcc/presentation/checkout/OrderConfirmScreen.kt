package com.example.testappcc.presentation.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.testappcc.core.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.util.Log
import com.example.testappcc.data.model.BookingInsert
import java.time.OffsetDateTime
import java.time.ZoneOffset
import com.example.testappcc.data.model.BookingResponse
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.result.PostgrestResult
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderConfirmScreen(
    address: String,
    name: String,
    phone: String,
    price: Double,
    durationMinutes: Int?,
    providerServiceId: Int,
    onOrderClick: (String) -> Unit,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val serviceFee = 6000.0
    val total = price + serviceFee
    var selectedPayment by remember { mutableStateOf("MoMo") }
    val userId = remember { context.getSharedPreferences("user_session", Context.MODE_PRIVATE).getString("user_id", null) }
    var isPlacingOrder by remember { mutableStateOf(false) }
    var orderSuccess by remember { mutableStateOf(false) }
    var orderError by remember { mutableStateOf<String?>(null) }

    // Thời gian hiện tại
    val now = remember { LocalTime.now() }
    var scheduledTime by remember { mutableStateOf<LocalTime?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Phần đầu: Địa chỉ, tên, sđt
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(address, fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
                Spacer(Modifier.height(4.dp))
                Text("$name | $phone", color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Thời gian bắt đầu:", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        scheduledTime?.format(timeFormatter) ?: now.format(timeFormatter),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { showTimePicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Hẹn giờ", color = Color.White, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (scheduledTime == null) "(Thời gian hiện tại)" else "(Đã chọn hẹn giờ)",
                    color = Color.Gray,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Chi tiết thanh toán
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF6F0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Chi tiết thanh toán", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Phí đơn hàng")
                    Text(String.format("%,.0f₫", price), fontWeight = FontWeight.Medium)
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Phí dịch vụ")
                    Text("6.000₫", fontWeight = FontWeight.Medium)
                }
                Divider(Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Tổng thanh toán", fontWeight = FontWeight.Bold)
                    Text(String.format("%,.0f₫", total), fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Phương thức thanh toán
        Text("Phương thức thanh toán", fontWeight = FontWeight.Bold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedPayment = "MoMo" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedPayment == "MoMo") Color(0xFFFF5722) else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) { Text("MoMo", color = if (selectedPayment == "MoMo") Color.White else Color.Black) }
            Button(
                onClick = { selectedPayment = "Tiền mặt" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedPayment == "Tiền mặt") Color(0xFFFF5722) else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.weight(1f)
            ) { Text("Tiền mặt", color = if (selectedPayment == "Tiền mặt") Color.White else Color.Black) }
        }
        Spacer(Modifier.height(24.dp))

        // Nút đặt đơn
        Button(
            onClick = {
                if (userId != null && providerServiceId != 0) {
                    isPlacingOrder = true
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val startAtDateTime = scheduledTime?.let {
                                LocalDateTime.now()
                                    .withHour(it.hour)
                                    .withMinute(it.minute)
                                    .atOffset(ZoneOffset.UTC)
                            } ?: OffsetDateTime.now()

                            val startAt = startAtDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            val endTime = startAtDateTime.plusMinutes(durationMinutes?.toLong() ?: 0L)
                            val endAt = endTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                            val booking = BookingInsert(
                                customerId = userId,
                                providerServiceId = providerServiceId,
                                status = "pending",
                                location = address,
                                startAt = startAt,
                                endAt = endAt,
                            )

                            val response = supabase
                                .from("bookings")
                                .insert(booking)

//                            val orderId = response.id.toString()

                            withContext(Dispatchers.Main) {
                                isPlacingOrder = false
                                orderSuccess = true
//                                onOrderClick(orderId) // <-- Gọi ngay sau khi insert thành công
                            }

                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isPlacingOrder = false
                                orderError = e.message
                                Log.e("BookingError", "Lỗi đặt đơn: ${e.message}", e)
                            }
                        }
                    }
                }
            },
            enabled = !isPlacingOrder,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (isPlacingOrder) "Đang đặt đơn..." else "Đặt đơn - ${String.format("%,.0f₫", total)}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.titleLarge.fontSize
            )
        }
        if (orderSuccess) {
            Text("Đặt đơn thành công!", color = Color.Green, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        if (orderError != null) {
            Text("Lỗi: $orderError", color = Color.Red, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Spacer(Modifier.height(12.dp))
        // Nút quay lại
        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
        ) {
            Text("Quay lại", color = Color(0xFF1976D2), fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.titleMedium.fontSize)
        }
    }

    // Dialog chọn thời gian
    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = now.hour,
            initialMinute = now.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    scheduledTime = LocalTime.of(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text("Chọn") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Hủy") }
            },
            title = { Text("Chọn thời gian") },
            text = {
                TimePicker(state = timeState)
            }
        )
    }
}

