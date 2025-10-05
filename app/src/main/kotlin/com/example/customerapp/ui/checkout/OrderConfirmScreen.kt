@file:Suppress("UNCHECKED_CAST")

package com.example.customerapp.ui.checkout

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.customerapp.core.network.RetrofitInstance
import com.example.customerapp.data.repository.BookingPaypalRepository
import com.example.customerapp.ui.components.VoucherSelectionDialog
import com.example.customerapp.data.model.Voucher
import com.example.customerapp.data.repository.VoucherRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.CircularProgressIndicator
import com.example.customerapp.data.model.BookingPaypalState
import com.example.customerapp.core.paypal.PayPalDeepLinkHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderConfirmScreen(
    address: String,
    name: String,
    phone: String,
    price: Double,
    durationMinutes: Int?,
    providerServiceId: Int,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedPayment by remember { mutableStateOf("MoMo") }




    // PayPal ViewModel
    val paypalRepository = remember { BookingPaypalRepository(RetrofitInstance.api) }
    val paypalViewModel: BookingPaypalViewModel = viewModel { BookingPaypalViewModel(paypalRepository) }
    val paypalUiState by paypalViewModel.uiState.collectAsState()
    
    // PayPal Deep Link Handler
    val paypalResult by PayPalDeepLinkHandler.paypalResult.collectAsState()
    var processedOrderIds by remember { mutableStateOf(setOf<String>()) }
    val userId = remember {
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getString("user_id", null)
    }
    var isPlacingOrder by remember { mutableStateOf(false) }
    var orderSuccess by remember { mutableStateOf(false) }
    var orderError by remember { mutableStateOf<String?>(null) }
    
    // Voucher state
    val voucherRepository = remember { VoucherRepository() }
    var availableVouchers by remember { mutableStateOf<List<Voucher>>(emptyList()) }
    var selectedVoucher by remember { mutableStateOf<Voucher?>(null) }
    var showVoucherDialog by remember { mutableStateOf(false) }
    
    // Load vouchers on startup
    LaunchedEffect(Unit) {
        try {
            availableVouchers = voucherRepository.getActiveVouchers()
        } catch (e: Exception) {
            Log.e("OrderConfirmScreen", "Error loading vouchers: ${e.message}", e)
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
    val voucherDiscount = (selectedVoucher?.getDiscountAmount(calculatedPrice)?.times(100)) ?: 0.0
    val total = calculatedPrice - voucherDiscount


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

        // Voucher selection item
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mã giảm giá",
                                fontWeight = FontWeight.Bold,
                                fontSize = MaterialTheme.typography.titleMedium.fontSize
                            )
                            Text(
                                text = if (selectedVoucher != null) {
                                    "${selectedVoucher!!.name} - Giảm ${selectedVoucher!!.discount*100}%"
                                } else {
                                    "Chọn mã giảm giá"
                                },
                                color = if (selectedVoucher != null) Color(0xFFE53935) else Color.Gray,
                                fontSize = MaterialTheme.typography.bodyMedium.fontSize
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { showVoucherDialog = true }
                        ) {
                            Text(
                                text = if (selectedVoucher != null) "Đổi" else "Chọn",
                                color = Color(0xFF9C27B0)
                            )
                        }
                    }
                    if (voucherDiscount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(
                                text = "Giảm giá",
                                color = Color(0xFFE53935)
                            )
                            Text(
                                text = "-${String.format(Locale("vi", "VN"),"%,.0f₫", voucherDiscount)}",
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE53935)
                            )
                        }
                    }
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
                    onClick = { selectedPayment = "Paypal" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedPayment == "Paypal") Color(0xFFFF5722) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Paypal",
                        color = if (selectedPayment == "Paypal") Color.White else Color.Black
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
                    if(selectedPayment == "Paypal"){
                        // Sử dụng ViewModel để tạo PayPal order
                        Log.d("PayPal", "Bắt đầu tạo PayPal order với amount: $total USD")
                        paypalViewModel.createOrder(total, "USD")
                    } else{
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
            // Hiển thị trạng thái PayPal
            when (paypalUiState) {
                is BookingPaypalState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF1976D2)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Đang xử lý PayPal...",
                            color = Color(0xFF1976D2),
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize
                        )
                    }
                }
                is BookingPaypalState.Error -> {
                    Text(
                        text = "Lỗi PayPal: ${(paypalUiState as BookingPaypalState.Error).message}",
                        color = Color.Red,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                is BookingPaypalState.OrderCreated -> {
                    Text(
                        text = "Đã mở PayPal. Vui lòng hoàn tất thanh toán.",
                        color = Color(0xFF388E3C),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                is BookingPaypalState.OrderCaptured -> {
                    if ((paypalUiState as BookingPaypalState.OrderCaptured).status == "COMPLETED") {
                        Text(
                            text = "Thanh toán PayPal thành công!",
                            color = Color(0xFF388E3C),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {}
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

    // Xử lý PayPal UI State với tối ưu tốc độ
    LaunchedEffect(paypalUiState) {
        Log.d("OrderConfirmScreen", "PayPal UI State changed: $paypalUiState")
        when (paypalUiState) {
            is BookingPaypalState.OrderCreated -> {
                val state = paypalUiState as BookingPaypalState.OrderCreated
                Log.d("OrderConfirmScreen", "🚀 Fast processing OrderCreated state with approvalUrl: ${state.approvalUrl}")
                
                // Mở PayPal URL ngay lập tức để tăng tốc độ phản hồi
                paypalViewModel.openPaypalUrlFast(context, state.approvalUrl)
                
                // Xử lý tạo booking trong background
                paypalViewModel.handleOrderCreated(
                    userId = userId ?: "",
                    providerServiceId = providerServiceId,
                    address = address,
                    startAt = startDateTime ?: now,
                    endAt = endDateTime,
                    durationMinutes = durationMinutes,
                    total = total,
                    numWorkers = selectedWorkers,
                    approvalUrl = state.approvalUrl,
                    context = context
                )
                Log.d("OrderConfirmScreen", "✅ Fast processing completed")
            }

            is BookingPaypalState.OrderCaptured -> {
                val orderCaptured = paypalUiState as BookingPaypalState.OrderCaptured
                val captureId = orderCaptured.captureId
                val paypalOrderId = orderCaptured.orderId
                
                // Xử lý kết quả thanh toán thông qua ViewModel
                paypalViewModel.handleWebPaymentResult(
                    bookingId = paypalViewModel.getBookingId() ?: 0,
                    captureId = captureId,
                    paypalOrderId = paypalOrderId
                )
            }

            is BookingPaypalState.Error -> {
                paypalViewModel.orderError = (paypalUiState as BookingPaypalState.Error).message
            }

            else -> {}
        }
    }


    // Xử lý PayPal Deep Link Result
    LaunchedEffect(paypalResult) {
        paypalResult?.let { result ->
            Log.d("OrderConfirmScreen", "PayPal deep link result: $result")
            
            // Clear the result immediately to prevent multiple processing
            PayPalDeepLinkHandler.clearResult()
            
            when (result.status) {
                "success" -> {
                    if (result.orderId != null && !processedOrderIds.contains(result.orderId)) {
                        Log.d("OrderConfirmScreen", "PayPal payment successful, capturing order: ${result.orderId}")
                        processedOrderIds = processedOrderIds + result.orderId
                        paypalViewModel.captureOrder(result.orderId)
                    } else if (result.orderId != null) {
                        Log.d("OrderConfirmScreen", "Order ${result.orderId} already processed, skipping capture")
                    }
                }
                "failed" -> {
                    Log.d("OrderConfirmScreen", "PayPal payment failed")
                    orderError = "Thanh toán PayPal thất bại"
                }
                "cancelled" -> {
                    Log.d("OrderConfirmScreen", "PayPal payment cancelled")
                    orderError = "Thanh toán PayPal bị hủy"
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

    // Voucher selection dialog
    if (showVoucherDialog) {
        VoucherSelectionDialog(
            vouchers = availableVouchers,
            selectedVoucher = selectedVoucher,
            onVoucherSelected = { voucher ->
                selectedVoucher = voucher
            },
            onDismiss = { showVoucherDialog = false }
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
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.95f)
                .padding(4.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
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

                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(8.dp))

                // Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            DatePicker(
                                state = dateState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(480.dp)
                                    .padding(2.dp),
                                showModeToggle = false
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

                Spacer(modifier = Modifier.height(12.dp))

                // Selected DateTime Display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                            modifier = Modifier.weight(1f)
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

                        Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            val millis = dateState.selectedDateMillis
                            if (millis != null) {
                                val selectedDate = Instant.ofEpochMilli(millis)
                                        .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                                        .toLocalDate()
                                val selectedTime = LocalTime.of(timeState.hour, timeState.minute)
                                val selectedDateTime = LocalDateTime.of(selectedDate, selectedTime)

                                if (!selectedDateTime.isBefore(now)) {
                                    onConfirm(selectedDateTime)
                                }
                            }
                            }
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

                Spacer(modifier = Modifier.height(16.dp))
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

