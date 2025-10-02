package com.example.customerapp.ui.orders

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.Booking
import com.example.customerapp.data.model.Review
import com.example.customerapp.data.model.ReviewInsert
import com.example.customerapp.data.model.formatTimestampToUserTimezonePretty
import com.example.customerapp.data.repository.ProviderServiceRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.customerapp.core.MyFirebaseMessagingService

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun OrdersScreen(
    onOrderClick: (String) -> Unit
) {
    val context = LocalContext.current
    val userId = remember { context.getSharedPreferences("user_session", Context.MODE_PRIVATE).getString("user_id", null) }
    var orders by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var reviews by remember { mutableStateOf<List<Review>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Đang đến", "Lịch sử", "Đã huỷ", "Đánh giá")
    val scope = rememberCoroutineScope()

    // Function to load all data
    suspend fun loadAll() {
        Log.d("OrdersScreen", "Starting loadAll() function")
        try {
            isLoading = true
            Log.d("OrdersScreen", "Fetching bookings from Supabase")
            val bookingResult = supabase.from("bookings").select {
                order(column = "created_at", order = Order.DESCENDING)
            }.decodeList<Booking>()
            Log.d("OrdersScreen", "Received ${bookingResult.size} bookings from Supabase")
            
            orders = bookingResult.filter { it.customer_id == userId }
            Log.d("OrdersScreen", "Filtered ${orders.size} bookings for user $userId")
            
            Log.d("OrdersScreen", "Fetching reviews from Supabase")
            val reviewResult = supabase.from("service_ratings").select{
                order(column = "created_at", order = Order.DESCENDING)
            }.decodeList<Review>()
            Log.d("OrdersScreen", "Received ${reviewResult.size} reviews from Supabase")
            
            reviews = reviewResult.filter { booking -> orders.any { it.id == booking.bookingId } }
            Log.d("OrdersScreen", "Filtered ${reviews.size} reviews for user's bookings")
        } catch (e: Exception) {
            Log.e("OrdersScreen", "Error in loadAll: ${e.message}", e)
            error = e.message
        } finally {
            isLoading = false
            Log.d("OrdersScreen", "Completed loadAll() function")
        }
    }

    // Create broadcast receiver
    val broadcastReceiver = remember {
        Log.d("OrdersScreen", "Creating new broadcast receiver instance")
        object : BroadcastReceiver() {
            init {
                Log.d("OrdersScreen", "Initializing broadcast receiver object")
            }
            
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("OrdersScreen", "🔔 Broadcast received in OrdersScreen")
                try {
                    Log.d("OrdersScreen", "Action: ${intent?.action}")
                    Log.d("OrdersScreen", "Extras: ${intent?.extras?.keySet()?.joinToString()}")
                    
                    if (intent?.action == MyFirebaseMessagingService.NEW_NOTIFICATION_ACTION) {
                        val notificationType = intent.getStringExtra(MyFirebaseMessagingService.EXTRA_NOTIFICATION_TYPE)
                        Log.d("OrdersScreen", "📬 Received notification of type: $notificationType")
                        
                        scope.launch {
                            try {
                                Log.d("OrdersScreen", "🔄 Starting data reload")
                                loadAll()
                                Log.d("OrdersScreen", "✅ Data reload completed successfully")
                            } catch (e: Exception) {
                                Log.e("OrdersScreen", "❌ Error reloading data: ${e.message}", e)
                                Log.e("OrdersScreen", "Stack trace: ", e)
                            }
                        }
                    } else {
                        Log.d("OrdersScreen", "⚠️ Unknown action received: ${intent?.action}")
                    }
                } catch (e: Exception) {
                    Log.e("OrdersScreen", "❌ Error in broadcast receiver: ${e.message}", e)
                    Log.e("OrdersScreen", "Stack trace: ", e)
                }
            }
        }
    }

    // Register the broadcast receiver when the screen is first created
    DisposableEffect(Unit) {
        Log.d("OrdersScreen", "Starting DisposableEffect")
        Log.d("OrdersScreen", "Current context: $context")

        val intentFilter = IntentFilter(MyFirebaseMessagingService.NEW_NOTIFICATION_ACTION)
        Log.d("OrdersScreen", "Created IntentFilter with action: ${intentFilter.actionsIterator().asSequence().toList()}")

        try {
            Log.d("OrdersScreen", "Attempting to register receiver")
            context.registerReceiver(
                broadcastReceiver,
                intentFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
            Log.d("OrdersScreen", "Successfully registered broadcast receiver")
        } catch (e: Exception) {
            Log.e("OrdersScreen", "Failed to register receiver - ${e.message}")
            Log.e("OrdersScreen", "Stack trace: ", e)
        }

        onDispose {
            Log.d("OrdersScreen", "Starting onDispose")
            try {
                context.unregisterReceiver(broadcastReceiver)
                Log.d("OrdersScreen", "Successfully unregistered receiver")
            } catch (e: Exception) {
                Log.e("OrdersScreen", "Error unregistering receiver - ${e.message}")
                Log.e("OrdersScreen", "Stack trace: ", e)
            }
            Log.d("OrdersScreen", "Completed onDispose")
        }
    }

    // Initial data load
    LaunchedEffect(userId) {
        if (userId != null) {
            loadAll()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Đơn hàng",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            error != null -> {
                Text(
                    text = "Lỗi: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            else -> {
                val refreshOrders = {
                    if (userId != null) {
                        scope.launch {
                            try {
                                loadAll()
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    }
                }
                
                when (selectedTab) {
                    0 -> OrderList(
                        orders.filter { it.status == "pending" || it.status == "accepted" }, 
                        onOrderClick,
                        onRefresh = refreshOrders
                    )
                    1 -> OrderList(
                        orders.filter { it.status == "completed" || it.status == "c-confirmed" || it.status == "p-confirmed" }, 
                        onOrderClick,
                        onRefresh = refreshOrders
                    )
                    2 -> OrderList(
                        orders.filter { it.status == "cancelled" }, 
                        onOrderClick,
                        onRefresh = refreshOrders
                    )
                    3 -> ReviewList(
                        orders.filter { it.status == "completed" },
                        reviews,
                        onOrderClick,
                        onReviewSubmit = { bookingId, rating, comment ->
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    // Tìm Booking theo bookingId để lấy provider_service_id
                                    val booking = orders.find { it.id == bookingId }
                                    val providerServiceId = booking?.provider_service_id

                                    if (providerServiceId != null) {
                                        supabase.from("service_ratings").insert(
                                            ReviewInsert(
                                                userId = userId ?: "",
                                                bookingId = bookingId,
                                                rating = rating,
                                                comment = comment,
                                                providerServiceId = providerServiceId // <-- Thêm ở đây
                                            )
                                        )
                                    }
                                    // Refresh reviews
                                    val reviewResult = supabase.from("service_ratings").select().decodeList<Review>()
                                    reviews = reviewResult.filter { booking -> orders.any { it.id == booking.bookingId } }
                                } catch (e: Exception) {
                                    error = e.message
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OrderList(
    orderList: List<Booking>, 
    onOrderClick: (String) -> Unit,
    onRefresh: (() -> Unit)? = null
) {
    val providerRepo = remember { ProviderServiceRepository() }
    var isUpdating by remember { mutableStateOf<Long?>(null) }
    
    // Function to update order status
    suspend fun updateOrderStatus(orderId: Long, newStatus: String) {
        try {
            isUpdating = orderId
            supabase.from("bookings")
                .update({
                    set("status" , newStatus)
                }){
                    filter {
                        eq("id", orderId) // Only allow update if current status is accepted
                    }
                } // Refresh page after update
            onRefresh?.invoke()
        } catch (e: Exception) {
            Log.e("OrderList", "Error updating order status: ${e.message}", e)
            // Handle error
        } finally {
            isUpdating = null
        }
    }
    
    if (orderList.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Quên chưa đặt đơn rồi nè bạn ơi?",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Quay về trang chủ và nhanh tay đặt đơn để chúng mình phục vụ cậu nhé!",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(orderList) { order ->
                var providerName by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(order.provider_service_id) {
                    val provider = providerRepo.getProviderServiceById(order.provider_service_id)
                    providerName = provider?.user?.name ?: "Không rõ"
                }
                val statusText = when (order.status) {
                    "pending" -> "Chờ xác nhận"
                    "accepted" -> "Đã chấp nhận"
                    "p-confirmed" -> "Nhà cung cấp đã xác nhận"
                    "c-confirmed" -> "Khách hàng đã xác nhận"
                    "completed" -> "Đã hoàn thành"
                    "cancelled" -> "Đã huỷ"
                    else -> order.status
                }
                val statusColor = when (order.status) {
                    "pending" -> Color(0xFFFFA000)
                    "accepted" -> Color(0xFF4CAF50)
                    "p-confirmed" -> Color(0xFF0288D1)
                    "c-confirmed" -> Color(0xFF1976D2)
                    "completed" -> Color(0xFF388E3C)
                    "cancelled" -> Color(0xFFE53935)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val createdAt = formatTimestampToUserTimezonePretty(order.created_at)

                val showConfirmButton = order.status == "accepted" || order.status == "p-confirmed"
                val nextStatus = when (order.status) {
                    "accepted" -> "c-confirmed"
                    "p-confirmed" -> "completed"
                    else -> null
                }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOrderClick(order.id.toString()) }
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Mã đơn: ${order.id}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Provider: ${providerName ?: "..."}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Địa chỉ: ${order.location ?: "Không rõ"}",
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Trạng thái: $statusText",
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Thời gian tạo: $createdAt",
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Confirm button for accepted and p-confirmed orders
                        if (showConfirmButton && nextStatus != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        CoroutineScope(Dispatchers.IO).launch {
                                            updateOrderStatus(order.id, nextStatus)
                                        }
                                    },
                                    enabled = isUpdating != order.id,
                                    modifier = Modifier.height(40.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    if (isUpdating == order.id) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Text(
                                            text = "Xác nhận",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewList(
    orderList: List<Booking>,
    reviews: List<Review>,
    onOrderClick: (String) -> Unit,
    onReviewSubmit: (Long, Int, String?) -> Unit
) {
    val providerRepo = remember { ProviderServiceRepository() }
    if (orderList.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Chưa có đơn hàng nào hoàn thành!",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Hoàn thành đơn hàng để đánh giá dịch vụ nhé!",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(orderList) { order ->
                var providerName by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(order.provider_service_id) {
                    val provider = providerRepo.getProviderServiceById(order.provider_service_id)
                    providerName = provider?.user?.name ?: "Không rõ"
                }
                val review = reviews.find { it.bookingId == order.id }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOrderClick(order.id.toString()) }
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Mã đơn: ${order.id}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Provider: ${providerName ?: "..."}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Địa chỉ: ${order.location ?: "Không rõ"}",
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (review == null) {
                            ReviewForm(
                                onSubmit = { rating, comment ->
                                    onReviewSubmit( order.id , rating, comment)
                                }
                            )
                        } else {
                            ReviewDisplay(review = review)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewForm(onSubmit: (Int, String?) -> Unit) {
    var rating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    val isFormValid = rating in 1..5

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f)
                    )
                )
            )
            .padding(12.dp)
    ) {
        Text(
            text = "Đánh giá dịch vụ",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { star ->
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (star <= rating) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { rating = star }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text("Bình luận (tuỳ chọn)") },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            minLines = 3,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                if (isFormValid) {
                    onSubmit(rating, comment.trim().takeIf { it.isNotBlank() })
                }
            },
            enabled = isFormValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
            )
        ) {
            Text(
                text = "Gửi đánh giá",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
fun ReviewDisplay(review: Review) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f)
                    )
                )
            )
            .padding(12.dp)
    ) {
        Text(
            text = "Đánh giá của bạn",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { star ->
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (star <= review.rating) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (review.comment != null) {
            Text(
                text = review.comment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = "Phản hồi từ nhà cung cấp",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = review.responses ?: "Chưa trả lời",
            style = MaterialTheme.typography.bodyMedium,
            color = if (review.responses != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = if (review.responses == null) FontStyle.Italic else FontStyle.Normal
        )
    }
}