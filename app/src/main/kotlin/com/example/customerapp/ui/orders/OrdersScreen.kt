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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.tracing.Trace
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
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import com.example.customerapp.core.MyFirebaseMessagingService
import com.example.customerapp.data.repository.BookingPaypalRepository
import com.example.customerapp.core.network.RetrofitInstance
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


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

    // [Tối ưu - Phân trang] Pagination states để tránh load toàn bộ đơn hàng cùng lúc
    val pageSize = 15
    var currentPage by remember { mutableIntStateOf(0) }
    var hasMoreOrders by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }

    // [Tối ưu - Phân trang] Function to load orders với pagination
//    suspend fun loadOrders(isLoadMore: Boolean = false) {
//        Log.d("OrdersScreen", "Starting loadOrders() function, isLoadMore=$isLoadMore")
//        Trace.beginSection("📡 API: Fetch Bookings with Pagination")
//        try {
//            if (!isLoadMore) {
//                isLoading = true
//                currentPage = 0
//                orders = emptyList()
//                hasMoreOrders = true
//            } else {
//                isLoadingMore = true
//            }
//
//            // Tính range cho pagination
//            val from = currentPage * pageSize
//            val to = from + pageSize - 1
//            Log.d("OrdersScreen", "Fetching bookings from Supabase, range: $from to $to")
//
//            val newOrders = supabase.from("bookings").select {
//                order(column = "created_at", order = Order.DESCENDING)
//                filter {
//                    eq("customer_id", userId ?: "")
//                }
//                range(from.toLong(), to.toLong()) // [Tối ưu] Giới hạn số lượng record
//            }.decodeList<Booking>()
//            Log.d("OrdersScreen", "Received ${newOrders.size} bookings from Supabase")
//            val startTime = System.currentTimeMillis()
//            while (System.currentTimeMillis() - startTime < 2000) {
//                // Giả lập xử lý nặng trong 2 giây trên luồng Main
//                val dummy = (1..1000).map { it * it }.filter { it % 2 == 0 }
//            }
//
//            // Kiểm tra còn data không
//            hasMoreOrders = newOrders.size == pageSize
//
//            // Append hoặc replace orders
//            orders = if (isLoadMore) orders + newOrders else newOrders
//            currentPage++
//
//            // Load reviews cho các orders hiện có
//            if (!isLoadMore || reviews.isEmpty()) {
//                val bookingIds = orders.map { it.id }
//
//                val reviewsResult = try {
//                    supabase.postgrest.rpc(
//                        function = "get_ratings_by_booking_ids",
//                        parameters = RatingRpcParams(pBookingIds = bookingIds)
//                    ).decodeList<Review>()
//                } finally {
//                    Trace.endSection()
//                }
//                reviews = reviewsResult
//            }
//        } catch (e: Exception) {
//            Log.e("OrdersScreen", "Error in loadOrders: ${e.message}", e)
//            error = e.message
//        } finally {
//            isLoading = false
//            isLoadingMore = false
//            Log.d("OrdersScreen", "Completed loadOrders() function")
//            Trace.endSection()
//        }
//    }

    // Function to load all data optimized
    suspend fun loadOrders(isLoadMore: Boolean = false) {
        Log.d("OrdersScreen", "Starting loadOrders(), isLoadMore=$isLoadMore")

        // 1. Cập nhật trạng thái Loading trên Main Thread trước khi chuyển sang IO
        if (!isLoadMore) {
            isLoading = true
            currentPage = 0
            orders = emptyList()
            hasMoreOrders = true
        } else {
            isLoadingMore = true
        }

        try {
            // 2. Chuyển sang luồng IO cho các tác vụ nặng
            withContext(Dispatchers.IO) {

                // --- TỐI ƯU: ĐO TẢI BOOKINGS ---
                Trace.beginSection("📡 API: Fetch Bookings")
                val from = currentPage * pageSize
                val to = from + pageSize - 1

                val newOrders: List<Booking> = try { // Xác định kiểu List<Booking> tại đây
                    val result = supabase.from("bookings").select {
                        order(column = "created_at", order = Order.DESCENDING)
                        filter { eq("customer_id", userId ?: "") }
                        range(from.toLong(), to.toLong())
                    }.decodeList<Booking>()

                    // Đoạn xử lý nặng này hiện đang nằm an toàn ở luồng IO
//                    val startTime = System.currentTimeMillis()
//                    while (System.currentTimeMillis() - startTime < 2000) {
//                        (1..1000).map { it * it }.filter { it % 2 == 0 }
//                    }
                    result // Trả về kết quả cho biến newOrders
                } finally {
                    Trace.endSection()
                }


                // --- CẬP NHẬT DỮ LIỆU ORDERS (Chuyển về Main) ---
                withContext(Dispatchers.Main) {
                    Trace.beginSection("🎨 UI: Update Orders List")
                    hasMoreOrders = newOrders.size == pageSize
                    orders = if (isLoadMore) orders + newOrders else newOrders
                    currentPage++
                    Trace.endSection()
                }

                // --- BƯỚC 3: TẢI REVIEWS (RPC) ---
                if (orders.isNotEmpty()) {
                    Trace.beginSection("📡 API: Fetch Reviews RPC")
                    val bookingIds = orders.map { it.id }

                    val reviewsResult = try {
                        supabase.postgrest.rpc(
                            function = "get_ratings_by_booking_ids",
                            parameters = RatingRpcParams(pBookingIds = bookingIds)
                        ).decodeList<Review>()
                    } finally {
                        Trace.endSection()
                    }

                    // Cập nhật Reviews lên UI
                    withContext(Dispatchers.Main) {
                        Trace.beginSection("🎨 UI: Update Reviews")
                        reviews = reviewsResult
                        Trace.endSection()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OrdersScreen", "Error in loadOrders: ${e.message}", e)
            withContext(Dispatchers.Main) {
                error = e.message
            }
        } finally {
            // Đảm bảo tắt loading trên Main Thread
            withContext(Dispatchers.Main) {
                isLoading = false
                isLoadingMore = false
                Log.d("OrdersScreen", "Completed loadOrders()")
            }
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
                                loadOrders()
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

        val intentFilter = IntentFilter(MyFirebaseMessagingService.NEW_NOTIFICATION_ACTION)

        try {
            context.registerReceiver(
                broadcastReceiver,
                intentFilter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.e("OrdersScreen", "Failed to register receiver - ${e.message}")
            Log.e("OrdersScreen", "Stack trace: ", e)
        }

        onDispose {
            try {
                context.unregisterReceiver(broadcastReceiver)
                Log.d("OrdersScreen", "Successfully unregistered receiver")
            } catch (e: Exception) {
                Log.e("OrdersScreen", "Error unregistering receiver - ${e.message}")
                Log.e("OrdersScreen", "Stack trace: ", e)
            }
        }
    }

    // Initial data load
    LaunchedEffect(userId) {
        if (userId != null) {
            loadOrders()
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

        val context = LocalContext.current // Lấy context để gửi broadcast

        Button(
            onClick = {
                Log.d("TestButton", "🚀 User bấm nút Test Broadcast")

                // 1. Tạo Intent giả lập giống hệt cái Service gửi ra
                val fakeIntent = Intent(MyFirebaseMessagingService.NEW_NOTIFICATION_ACTION).apply {
                    // Gắn dữ liệu giả để logic bên trong onReceive chạy được
                    putExtra(MyFirebaseMessagingService.EXTRA_NOTIFICATION_TYPE, "TEST_TYPE")

                    // QUAN TRỌNG: Chỉ gửi cho chính app này (Explicit Broadcast)
                    // Giúp bảo mật hơn và khớp với receiver exported=false
                    `package` = context.packageName
                }

                // 2. Bắn Broadcast đi
                context.sendBroadcast(fakeIntent)

                Log.d("TestButton", "✅ Đã gửi Broadcast giả lập!")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red) // Màu đỏ cho dễ thấy
        ) {
            Text("🔥 GIẢ LẬP NHẬN THÔNG BÁO")
        }
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
                                loadOrders()
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    }
                }
                
                // [Tối ưu - Phân trang] Callback để load thêm đơn hàng
                val loadMoreOrders = {
                    if (hasMoreOrders && !isLoadingMore) {
                        scope.launch {
                            try {
                                loadOrders(isLoadMore = true)
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
                        onRefresh = refreshOrders,
                        onLoadMore = loadMoreOrders,
                        hasMore = hasMoreOrders,
                        isLoadingMore = isLoadingMore
                    )
                    1 -> OrderList(
                        orders.filter { it.status == "completed" || it.status == "c-confirmed" || it.status == "p-confirmed" }, 
                        onOrderClick,
                        onRefresh = refreshOrders,
                        onLoadMore = loadMoreOrders,
                        hasMore = hasMoreOrders,
                        isLoadingMore = isLoadingMore
                    )
                    2 -> OrderList(
                        orders.filter { it.status == "cancelled" }, 
                        onOrderClick,
                        onRefresh = refreshOrders,
                        onLoadMore = loadMoreOrders,
                        hasMore = hasMoreOrders,
                        isLoadingMore = isLoadingMore
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
    onRefresh: (() -> Unit)? = null,
    // [Tối ưu - Phân trang] Thêm các callback và state cho pagination
    onLoadMore: (() -> Unit)? = null,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false
) {
    val providerRepo = remember { ProviderServiceRepository() }
    // [Tối ưu 5 - Thuật toán] Cache tên provider theo provider_service_id để tránh Supabase call lặp lại khi render list
    val providerNameCache = remember { mutableStateMapOf<Int, String>() }
    val bookingPaypalRepo = remember { BookingPaypalRepository(RetrofitInstance.api) }
    var isUpdating by remember { mutableStateOf<Long?>(null) }
    
    // [Tối ưu - Phân trang] LazyListState để detect scroll position
    val listState = rememberLazyListState()

    // [Tối ưu - Phân trang] Detect khi cuộn gần cuối danh sách
    LaunchedEffect(listState, hasMore, isLoadingMore) {
        snapshotFlow {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            // Load khi còn 3 item nữa là hết
            lastVisibleItem >= totalItems - 3 && totalItems > 0
        }.distinctUntilChanged().collect { shouldLoad ->
            if (shouldLoad && hasMore && !isLoadingMore) {
                onLoadMore?.invoke()
            }
        }
    }

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
            
            // Gọi payout khi cập nhật trạng thái đơn hàng thành "completed"
            if (newStatus == "completed" || newStatus == "c-confirmed") {
                bookingPaypalRepo.processPayoutForCompletedOrder(orderId.toInt())
            }
            
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
        LazyColumn(
            state = listState, // [Tối ưu - Phân trang] Gắn listState để detect scroll
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(orderList) { order ->
                val providerName = providerNameCache[order.provider_service_id]
                LaunchedEffect(order.provider_service_id) {
                    if (!providerNameCache.contains(order.provider_service_id)) {
                        val provider = providerRepo.getProviderServiceById(order.provider_service_id)
                        providerNameCache[order.provider_service_id] = provider?.user?.name ?: "Không rõ"
                    }
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

            // [Tối ưu - Phân trang] Loading indicator khi đang tải thêm
            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Đang tải thêm...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
    val providerNameCache = remember { mutableStateMapOf<Int, String>() }
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
                val providerName = providerNameCache[order.provider_service_id]
                LaunchedEffect(order.provider_service_id) {
                    if (!providerNameCache.contains(order.provider_service_id)) {
                        val provider = providerRepo.getProviderServiceById(order.provider_service_id)
                        providerNameCache[order.provider_service_id] = provider?.user?.name ?: "Không rõ"
                    }
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
@Serializable
data class RatingRpcParams(
    @SerialName("ids")
    val pBookingIds: List<Long> // Tên biến phải khớp tham số SQL
)