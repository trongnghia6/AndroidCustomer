package com.example.customerapp.presentation.checkout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.util.Log
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.customerapp.core.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.customerapp.data.model.BookingInsert
import com.example.customerapp.data.model.BookingResponse
import com.example.customerapp.data.model.Transaction
import io.github.jan.supabase.postgrest.postgrest
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import android.widget.Toast

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
    var selectedPayment by remember { mutableStateOf("MoMo") }
    val userId = remember {
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getString("user_id", null)
    }
    var isPlacingOrder by remember { mutableStateOf(false) }
    var orderSuccess by remember { mutableStateOf(false) }
    var orderError by remember { mutableStateOf<String?>(null) }

    val activity = LocalContext.current as? Activity

    // State để lưu kết quả MoMo
    var momoPaymentSuccess by remember { mutableStateOf(false) }
    var momoPaymentError by remember { mutableStateOf<String?>(null) }

    // Thêm state để báo lỗi khi không có app MoMo
    var momoAppNotInstalled by remember { mutableStateOf(false) }

    // Launcher để nhận kết quả từ MoMo
    val momoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!
            val status = data.getIntExtra("status", -1)
            if (status == 0) {
                momoPaymentSuccess = true
                momoPaymentError = null
            } else {
                momoPaymentSuccess = false
                momoPaymentError = data.getStringExtra("message") ?: "Thanh toán thất bại"
            }
        } else {
            momoPaymentSuccess = false
            momoPaymentError = "Thanh toán bị huỷ hoặc thất bại"
        }
    }

    // Thời gian và ngày hiện tại (múi giờ +07:00)
    val now = remember { LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")) }
    var startDateTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var endDateTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var showStartDateTimePicker by remember { mutableStateOf(false) }
    var showEndDateTimePicker by remember { mutableStateOf(false) }
    var availableWorkers by remember { mutableStateOf<Int?>(null) }
    var workerQueryError by remember { mutableStateOf<String?>(null) }
    var selectedWorkers by remember { mutableStateOf("1") }
    var workerSelectionError by remember { mutableStateOf<String?>(null) }
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val durationHours = if (startDateTime != null && endDateTime != null) {
        val duration = Duration.between(startDateTime, endDateTime)
        duration.toMinutes().toDouble() / 60.0
    } else 0.0
    val numWorkers = selectedWorkers.toIntOrNull() ?: 1
    val calculatedPrice = price * durationHours * numWorkers
    val total = calculatedPrice + serviceFee


    // Kiểm tra số lượng người được chọn
    LaunchedEffect(selectedWorkers, availableWorkers) {
        try {
            val numWorkers = selectedWorkers.toIntOrNull()
            val workers = availableWorkers ?: 0
            workerSelectionError = when {
                numWorkers == null || numWorkers < 1 ->
                    "Vui lòng nhập số lượng nhân viên hợp lệ (tối thiểu 1)"
                availableWorkers != null && numWorkers > workers ->
                    "Số nhân viên chọn ($numWorkers) vượt quá số nhân viên rảnh ($availableWorkers)"
                else -> null
            }
        } catch (e: Exception) {
            workerSelectionError = "Lỗi nhập số lượng: ${e.message}"
        }
    }

    // Gọi findAvailableWorkers khi startDateTime hoặc endDateTime thay đổi
    LaunchedEffect(startDateTime, endDateTime) {
        if (startDateTime != null && endDateTime != null) {
            if (startDateTime!! >= now && startDateTime!! < endDateTime!!) {
                try {
                    val workers = findAvailableWorkers(
                        startDateTime!!.toLocalTime(),
                        endDateTime!!.toLocalTime(),
                        providerServiceId,
                        startDateTime!!.toLocalDate(),
                        endDateTime!!.toLocalDate()
                    )
                    availableWorkers = workers
                    workerQueryError = null
                } catch (e: Exception) {
                    workerQueryError = "Lỗi tìm nhân viên: ${e.message}"
                    availableWorkers = null
                    Log.e("WorkerQuery", "Lỗi: ${e.message}", e)
                }
            } else {
                workerQueryError = if (startDateTime!! < now) {
                    "Thời gian bắt đầu không được trước thời gian hiện tại"
                } else {
                    "Thời gian kết thúc phải sau thời gian bắt đầu"
                }
                availableWorkers = null
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = address,
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$name | $phone",
                        color = Color.Gray,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    )
                    Spacer(Modifier.height(12.dp))

                    // Thời gian bắt đầu
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Thời gian bắt đầu",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val startText = startDateTime?.let {
                                    "${it.toLocalDate().format(dateFormatter)} ${it.toLocalTime().format(timeFormatter)}"
                                } ?: "(Chưa chọn)"

                                Text(
                                    text = startText,
                                    color = if (startDateTime != null) MaterialTheme.colorScheme.primary else Color.Gray,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { showStartDateTimePicker = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = "Chọn",
                                        color = Color.White,
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Thời gian kết thúc
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Thời gian kết thúc",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val endText = endDateTime?.let {
                                    "${it.toLocalDate().format(dateFormatter)} ${it.toLocalTime().format(timeFormatter)}"
                                } ?: "(Chưa chọn)"

                                Text(
                                    text = endText,
                                    color = if (endDateTime != null) MaterialTheme.colorScheme.primary else Color.Gray,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { showEndDateTimePicker = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = "Chọn",
                                        color = Color.White,
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Số nhân viên
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Số nhân viên:",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(100.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = selectedWorkers,
                            onValueChange = { selectedWorkers = it },
                            modifier = Modifier.width(100.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = workerSelectionError != null,
                            enabled = availableWorkers != null,
                            supportingText = {
                                workerSelectionError?.let {
                                    Text(
                                        text = it,
                                        color = Color.Red,
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                                    )
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    // Hiển thị số người rảnh
                    availableWorkers?.let {
                        Text(
                            text = "Số nhân viên rảnh: $it",
                            color = Color(0xFF388E3C),
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize
                        )
                    }
                    workerQueryError?.let {
                        Text(
                            text = it,
                            color = Color.Red,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (startDateTime == null || endDateTime == null) "(Chưa chọn thời gian)" else "(Đã chọn thời gian)",
                        color = Color.Gray,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF6F0))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Chi tiết thanh toán",
                        fontWeight = FontWeight.Bold,
                        fontSize = MaterialTheme.typography.titleMedium.fontSize
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(text = "Phí đơn hàng")
                        Text(
                            text = String.format(Locale("vi", "VN"),"%,.0f₫", calculatedPrice),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(text = "Phí dịch vụ")
                        Text(
                            text = "6.000₫",
                            fontWeight = FontWeight.Medium
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(
                            text = "Tổng thanh toán",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(Locale("vi", "VN"),"%,.0f₫", total),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE53935)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Phương thức thanh toán",
                fontWeight = FontWeight.Bold,
                fontSize = MaterialTheme.typography.titleMedium.fontSize
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { selectedPayment = "MoMo" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedPayment == "MoMo") Color(0xFFFF5722) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "MoMo",
                        color = if (selectedPayment == "MoMo") Color.White else Color.Black
                    )
                }
                Button(
                    onClick = { selectedPayment = "Tiền mặt" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedPayment == "Tiền mặt") Color(0xFFFF5722) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Tiền mặt",
                        color = if (selectedPayment == "Tiền mặt") Color.White else Color.Black
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (selectedPayment == "MoMo") {
                        // Kiểm tra app MoMo đã cài chưa
                        val momoPackage = "com.mservice.momotransfer"
                        val pm = context.packageManager
                        val momoInstalled = try {
                            pm.getPackageInfo(momoPackage, PackageManager.GET_ACTIVITIES)
                            true
                        } catch (e: Exception) {
                            false
                        }
                        if (!momoInstalled) {
                            momoAppNotInstalled = true
                            return@Button
                        }
                        // Gọi SDK MoMo
                        val momoIntent = Intent("com.momo.payment")
                        momoIntent.putExtra("merchantName", "Tên Merchant")
                        momoIntent.putExtra("merchantCode", "Mã Merchant")
                        momoIntent.putExtra("amount", total.toLong())
                        momoIntent.putExtra("orderId", System.currentTimeMillis().toString())
                        momoIntent.putExtra("orderLabel", "Thanh toán dịch vụ")
                        momoIntent.putExtra("description", "Thanh toán qua MoMo")
                        momoIntent.putExtra("fee", 0)
                        momoIntent.putExtra("extra", "")
                        momoIntent.putExtra("requestId", System.currentTimeMillis().toString())
                        momoIntent.putExtra("partnerCode", "Mã Partner")
                        momoIntent.putExtra("partnerName", "Tên Partner")
                        momoIntent.putExtra("appScheme", "momo")
                        // ...các tham số khác nếu cần...

                        momoLauncher.launch(momoIntent)
                    } else {
                        // Thanh toán tiền mặt: tạo booking luôn
                        if (userId != null && providerServiceId != 0) {
                            isPlacingOrder = true
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val startAtDateTime = startDateTime?.atOffset(ZoneOffset.ofHours(7))
                                        ?: OffsetDateTime.now(ZoneOffset.ofHours(7))

                                    val endAtDateTime = endDateTime?.atOffset(ZoneOffset.ofHours(7))
                                        ?: startAtDateTime.plusMinutes(durationMinutes?.toLong() ?: 60L)

                                    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                                    val startAt = startAtDateTime.format(formatter)
                                    val endAt = endAtDateTime.format(formatter)
                                    val numWorkers = selectedWorkers.toIntOrNull() ?: 1

                                    val booking = BookingInsert(
                                        customerId = userId,
                                        providerServiceId = providerServiceId,
                                        status = "pending",
                                        location = address,
                                        startAt = startAt,
                                        endAt = endAt,
                                        numWorkers = numWorkers
                                    )

                                    val bookingResult = supabase.from("bookings")
                                        .insert(booking) {
                                            select() // Trả về toàn bộ bản ghi sau khi insert
                                        }

                                    // Lấy ID từ kết quả
                                    val insertedBooking = bookingResult.decodeSingle<BookingResponse>()
                                    val bookingId = insertedBooking.id
                                    val transaction = Transaction(
                                        bookingId = bookingId,
                                        amount = total,
                                        status = "pending",
                                        paymentMethod = selectedPayment
                                    )

                                    supabase.from("transactions").insert(transaction)

                                    withContext(Dispatchers.Main) {
                                        isPlacingOrder = false
                                        orderSuccess = true
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
                    }
                },
                enabled = !isPlacingOrder && availableWorkers != null && availableWorkers!! > 0 &&
                        selectedWorkers.toIntOrNull() != null && selectedWorkers.toIntOrNull()!! <= availableWorkers!! &&
                        workerSelectionError == null &&
                        startDateTime != null && endDateTime != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isPlacingOrder) "Đang đặt đơn..." else "Đặt đơn - ${
                        String.format(Locale("vi", "VN"),
                            "%,.0f₫",
                            total
                        )
                    }",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.titleLarge.fontSize
                )
            }
        }

        item {
            if (orderSuccess) {
                Text(
                    text = "Đặt đơn thành công!",
                    color = Color.Green,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            if (orderError != null) {
                Text(
                    text = "Lỗi: $orderError",
                    color = Color.Red,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            // Hiển thị kết quả MoMo
            momoPaymentError?.let {
                Text(
                    text = "Lỗi MoMo: $it",
                    color = Color.Red,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            // Hiển thị lỗi nếu không có app MoMo
            if (momoAppNotInstalled) {
                Text(
                    text = "Bạn chưa cài đặt ứng dụng MoMo. Vui lòng cài đặt MoMo để sử dụng chức năng này.",
                    color = Color.Red,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true)
            ) {
                Text(
                    text = "Quay lại",
                    color = Color(0xFF1976D2),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize
                )
            }
        }
    }

    // Khi thanh toán MoMo thành công thì tạo booking
    LaunchedEffect(momoPaymentSuccess) {
        if (momoPaymentSuccess && userId != null && providerServiceId != 0) {
            isPlacingOrder = true
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val startAtDateTime = startDateTime?.atOffset(ZoneOffset.ofHours(7))
                        ?: OffsetDateTime.now(ZoneOffset.ofHours(7))

                    val endAtDateTime = endDateTime?.atOffset(ZoneOffset.ofHours(7))
                        ?: startAtDateTime.plusMinutes(durationMinutes?.toLong() ?: 60L)

                    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    val startAt = startAtDateTime.format(formatter)
                    val endAt = endAtDateTime.format(formatter)
                    val numWorkers = selectedWorkers.toIntOrNull() ?: 1

                    val booking = BookingInsert(
                        customerId = userId,
                        providerServiceId = providerServiceId,
                        status = "pending",
                        location = address,
                        startAt = startAt,
                        endAt = endAt,
                        numWorkers = numWorkers
                    )

                    val bookingResult = supabase.from("bookings")
                        .insert(booking) {
                            select() // Trả về toàn bộ bản ghi sau khi insert
                        }

                    // Lấy ID từ kết quả
                    val insertedBooking = bookingResult.decodeSingle<BookingResponse>()
                    val bookingId = insertedBooking.id
                    val transaction = Transaction(
                        bookingId = bookingId,
                        amount = total,
                        status = "pending",
                        paymentMethod = selectedPayment
                    )

                    supabase.from("transactions").insert(transaction)

                    withContext(Dispatchers.Main) {
                        isPlacingOrder = false
                        orderSuccess = true
                        momoPaymentSuccess = false // reset
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isPlacingOrder = false
                        orderError = e.message
                        momoPaymentSuccess = false // reset
                        Log.e("BookingError", "Lỗi đặt đơn: ${e.message}", e)
                    }
                }
            }
        }
    }

    // DateTimePicker bắt đầu
    if (showStartDateTimePicker) {
        EnhancedDateTimePickerDialog(
            initialDateTime = startDateTime ?: now,
            onDismissRequest = { showStartDateTimePicker = false },
            onConfirm = { dateTime ->
                startDateTime = dateTime
                // Tự động set endDateTime nếu chưa có hoặc không hợp lệ
                if (endDateTime == null || endDateTime!! <= dateTime) {
                    endDateTime = dateTime.plusMinutes(durationMinutes?.toLong() ?: 60L)
                }
                showStartDateTimePicker = false
            }
        )
    }

    // DateTimePicker kết thúc
    if (showEndDateTimePicker) {
        EnhancedDateTimePickerDialog(
            initialDateTime = endDateTime ?: (startDateTime?.plusMinutes(durationMinutes?.toLong() ?: 60L) ?: now.plusHours(1)),
            onDismissRequest = { showEndDateTimePicker = false },
            onConfirm = { dateTime ->
                endDateTime = dateTime
                showEndDateTimePicker = false
            }
        )
    }
}


// Enhanced DateTimePicker với giao diện đẹp hơn
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedDateTimePickerDialog(
    initialDateTime: LocalDateTime = LocalDateTime.now(),
    onDismissRequest: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    val now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
    var selectedTab by remember { mutableIntStateOf(0) }

    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initialDateTime.toLocalDate()
            .atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh"))
            .toInstant()
            .toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                    .toLocalDate()
                return !date.isBefore(now.toLocalDate())
            }
        }
    )

    val timeState = rememberTimePickerState(
        initialHour = initialDateTime.hour,
        initialMinute = initialDateTime.minute,
        is24Hour = true
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Chọn ngày và giờ",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Ngày") },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Giờ") },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            DatePicker(
                                state = dateState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        1 -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                TimePicker(
                                    state = timeState,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Selected DateTime Display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Thời gian đã chọn:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val selectedDate = dateState.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it)
                                .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                                .toLocalDate()
                        } ?: initialDateTime.toLocalDate()

                        val selectedTime = LocalTime.of(timeState.hour, timeState.minute)
                        val selectedDateTime = LocalDateTime.of(selectedDate, selectedTime)

                        Text(
                            text = selectedDateTime.format(
                                DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm")
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hủy")
                    }

                    Button(
                        onClick = {
                            val millis = dateState.selectedDateMillis
                            if (millis != null) {
                                val selectedDate = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneId.of("Asia/Ho_Chi_Minh")
                                    ).toLocalDate()
                                val selectedTime = LocalTime.of(timeState.hour, timeState.minute)
                                val selectedDateTime = LocalDateTime.of(selectedDate, selectedTime)

                                if (!selectedDateTime.isBefore(now)) {
                                    onConfirm(selectedDateTime)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xác nhận")
                    }
                }
            }
        }
    }
}

// Hàm tìm số người rảnh (giữ nguyên)
suspend fun findAvailableWorkers(
    startTime: LocalTime,
    endTime: LocalTime,
    providerServiceId: Int,
    startDate: LocalDate,
    endDate: LocalDate
): Int {
    return withContext(Dispatchers.IO) {
        try {
            val startDateTime = LocalDateTime.of(startDate, startTime)
                .atOffset(ZoneOffset.ofHours(7))
            val endDateTime = LocalDateTime.of(endDate, endTime)
                .atOffset(ZoneOffset.ofHours(7))

            val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            val startTimeStr = startDateTime.format(formatter)
            val endTimeStr = endDateTime.format(formatter)

            Log.d("lay_nguoi_ranh_bat_dau", startTimeStr)
            Log.d("lay_nguoi_ranh_ket_thuc", endTimeStr)

            val result = supabase.postgrest.rpc(
                function = "get_available_people",
                parameters = buildJsonObject {
                    put("in_service_id", providerServiceId)
                    put("in_start_time", startTimeStr)
                    put("in_end_time", endTimeStr)
                }
            )

            Log.d("lay_nguoi_ranh_result", result.toString())
            val jsonResult = Json.parseToJsonElement(result.data)
            val count = maxOf(jsonResult.jsonPrimitive.intOrNull ?: 0, 0)
            Log.d("lay_nguoi_ranh_jsonResult", jsonResult.toString())
            Log.d("lay_nguoi_ranh_count", count.toString())

            count
        } catch (e: Exception) {
            Log.e("WorkerQuery", "Lỗi truy vấn nhân viên: ${e.message}", e)
            0
        }
    }
}

// Hiện tại, khi chọn "MoMo", chỉ lưu thông tin vào transaction chứ chưa tích hợp SDK MoMo.
// Nếu muốn thanh toán MoMo thực tế, cần tích hợp SDK MoMo và xử lý callback thanh toán thành công.
