package com.example.testappcc.presentation.orders

import android.content.Context
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testappcc.core.supabase
import com.example.testappcc.data.model.Booking
import com.example.testappcc.data.model.Review
import com.example.testappcc.data.model.ReviewInsert
import com.example.testappcc.data.repository.ProviderServiceRepository
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter




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
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Đang đến", "Lịch sử", "Đã huỷ", "Đánh giá")

    LaunchedEffect(userId) {
        if (userId != null) {
            isLoading = true
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val bookingResult = supabase.from("bookings").select().decodeList<Booking>()
                    orders = bookingResult.filter { it.customer_id == userId }
                    val reviewResult = supabase.from("service_ratings").select().decodeList<Review>()
                    reviews = reviewResult.filter { booking -> orders.any { it.id == booking.bookingId } }
                    isLoading = false
                } catch (e: Exception) {
                    error = e.message
                    isLoading = false
                }
            }
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
                when (selectedTab) {
                    0 -> OrderList(orders.filter { it.status == "pending" || it.status == "confirmed" }, onOrderClick)
                    1 -> OrderList(orders.filter { it.status == "completed" }, onOrderClick)
                    2 -> OrderList(orders.filter { it.status == "cancelled" }, onOrderClick)
                    3 -> ReviewList(
                        orders.filter { it.status == "completed" },
                        reviews,
                        onOrderClick,
                        onReviewSubmit = { bookingId, rating, comment ->
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    // Tìm Booking theo bookingId để lấy provider_service_id
                                    val booking = orders.find { it.id == bookingId.toLong() }
                                    val providerServiceId = booking?.provider_service_id

                                    if (providerServiceId != null) {
                                        supabase.from("service_ratings").insert(
                                            ReviewInsert(
                                                userId = userId ?: "",
                                                bookingId = bookingId.toLong(),
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
fun OrderList(orderList: List<Booking>, onOrderClick: (String) -> Unit) {
    val providerRepo = remember { ProviderServiceRepository() }
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
                    "confirmed" -> "Đã xác nhận"
                    "completed" -> "Đã hoàn thành"
                    "cancelled" -> "Đã huỷ"
                    else -> order.status
                }
                val statusColor = when (order.status) {
                    "pending" -> Color(0xFFFFA000)
                    "confirmed" -> Color(0xFF1976D2)
                    "completed" -> Color(0xFF388E3C)
                    "cancelled" -> Color(0xFFE53935)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val createdAt = order.created_at
                val formattedTime = try {
                    if (createdAt != null) {
                        val instant = OffsetDateTime.parse(createdAt)
                        instant.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    } else "-"
                } catch (e: Exception) {
                    createdAt ?: "-"
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOrderClick(order.id.toString()) }
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
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
                            text = "Thời gian tạo: $formattedTime",
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
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
                                bookingId = order.id,
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
fun ReviewForm(bookingId: Long, onSubmit: (Int, String?) -> Unit) {
    var rating by remember { mutableStateOf(0) }
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
            fontStyle = if (review.responses == null) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal
        )
    }
}