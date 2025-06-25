package com.example.testappcc.presentation.chat

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.automirrored.sharp.Send
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.sharp.Send
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.testappcc.core.supabase
import com.example.testappcc.data.model.Message
import com.example.testappcc.data.repository.ProviderServiceRepository
import com.example.testappcc.presentation.orders.OrdersScreen
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.decodeIfNotEmptyOrDefault
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.realtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter



@Composable
fun ChatScreen(
    providerServiceId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val userId = remember {
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getString("user_id", null) ?: ""
    }
    val providerRepo = remember { ProviderServiceRepository() }
    var providerName by remember { mutableStateOf("Không rõ") }
    var providerId by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var newMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val channelState = remember { mutableStateOf<RealtimeChannel?>(null) }


    // Hàm tải dữ liệu
    fun loadData() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Ngắt kết nối kênh cũ nếu có
                channelState.value?.unsubscribe()
                channelState.value = null

                // Lấy thông tin người cung cấp
                val providerService = providerRepo.getProviderServiceById(providerServiceId.toInt())
                providerName = providerService?.user?.name ?: "Không rõ"
                providerId = providerService?.user?.id ?: ""

                // Gọi connect để thiết lập WebSocket (chỉ khi chưa connect)
                if (supabase.realtime.status.value != Realtime.Status.CONNECTED) {
                    supabase.realtime.connect()
                }


                // Tải danh sách tin nhắn hiện tại
                val result = supabase.from("messages").select {
                    filter {
                        or {
                            and {
                                eq("sender_id", userId)
                                eq("receiver_id", providerId)
                            }
                            and {
                                eq("sender_id", providerId)
                                eq("receiver_id", userId)
                            }
                        }
                    }
                    order(column = "created_at", order = Order.ASCENDING)
                }.decodeList<Message>()
                messages = result

                // Tạo channel riêng cho cuộc trò chuyện với tên unique
                val channelName = "chat:${minOf(userId, providerId)}-${maxOf(userId, providerId)}"
                val channel = supabase.channel(channelName)



                // Subscription 1: user gửi cho provider
                val flow1 = channel.postgresChangeFlow<PostgresAction.Insert>(
                    schema = "public"
                ) {
                    table = "messages"
                    filter("sender_id", FilterOperator.EQ, userId)
                    filter("receiver_id", FilterOperator.EQ, providerId)
                }

// Subscription 2: provider gửi cho user
                val flow2 = channel.postgresChangeFlow<PostgresAction.Insert>(
                    schema = "public"
                ) {
                    table = "messages"
                    filter("sender_id", FilterOperator.EQ, providerId)
                    filter("receiver_id", FilterOperator.EQ, userId)
                }
                val changeFlow = merge(flow1, flow2)

                // Subscribe trước khi setup postgres changes
                channel.subscribe(blockUntilSubscribed = true)



                changeFlow.onEach { action ->
                    val newMsg = action.decodeRecord<Message>()

                    // Kiểm tra xem tin nhắn có thuộc cuộc trò chuyện này không
                    if ((newMsg.senderId == userId && newMsg.receiverId == providerId) ||
                        (newMsg.senderId == providerId && newMsg.receiverId == userId)) {

                        // Tránh duplicate message - chỉ thêm nếu chưa có
                        val exists = messages.any { it.id == newMsg.id ||
                            (it.content == newMsg.content &&
                             it.senderId == newMsg.senderId &&
                             it.receiverId == newMsg.receiverId &&
                             kotlin.math.abs((it.createdAt?.let { time ->
                                 try { OffsetDateTime.parse(time).toEpochSecond() } catch (e: Exception) { 0L }
                             } ?: 0L) - (newMsg.createdAt?.let { time ->
                                 try { OffsetDateTime.parse(time).toEpochSecond() } catch (e: Exception) { 0L }
                             } ?: 0L)) < 2) // Trong vòng 2 giây
                        }

                        if (!exists) {
                            messages = messages + newMsg
                            coroutineScope.launch {
                                listState.animateScrollToItem(messages.lastIndex)
                            }
                        } else {
                            Log.d("ChatScreen", "⚠️ Tin nhắn đã tồn tại, bỏ qua")
                        }
                    }
                }.launchIn(coroutineScope)

                // Lưu kênh để unsubscribe khi cần
                channelState.value = channel

                isLoading = false
                Log.d("ChatScreen", "🔗 Đã thiết lập realtime subscription cho channel: $channelName")
            } catch (e: Exception) {
                Log.e("ChatScreen", "❌ Lỗi trong loadData: ${e.message}", e)
                error = e.message
                isLoading = false
            }
        }
    }


    // Gọi hàm tải dữ liệu
    LaunchedEffect(providerServiceId) {
        loadData()
    }
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }


    DisposableEffect(channelState.value) {
        val channel = channelState.value
        onDispose {
            if (channel != null) {
                runBlocking {
                    channel.unsubscribe()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(top = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.navigateUp() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Sharp.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Trò chuyện với $providerName",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
        // Nội dung
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { message ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(animationSpec = tween(300)),
                                exit = fadeOut(animationSpec = tween(300))
                            ) {
                                MessageBubble(
                                    message = message,
                                    isSentByUser = message.senderId == userId
                                )
                            }
                        }
                    }
                    // Input tin nhắn
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newMessage,
                            onValueChange = { newMessage = it },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp)),
                            placeholder = { Text("Nhập tin nhắn...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 3
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        // Test button for debugging
                        IconButton(
                            onClick = {
                                val testMessage = Message(
                                    senderId = providerId,
                                    receiverId = userId,
                                    content = "Test message từ provider lúc ${OffsetDateTime.now()}",
                                    createdAt = OffsetDateTime.now().toString()
                                )
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        supabase.from("messages").insert(testMessage)
                                    } catch (e: Exception) {
                                        Log.e("ChatScreen", "❌ Lỗi khi gửi test message: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("T", color = MaterialTheme.colorScheme.onSecondary)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (newMessage.trim().isNotEmpty()) {
                                    val message = Message(
                                        senderId = userId,
                                        receiverId = providerId,
                                        content = newMessage.trim(),
                                        createdAt = OffsetDateTime.now().toString()
                                    )
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            supabase.from("messages").insert(message)
                                            newMessage = ""
                                        } catch (e: Exception) {
                                            Log.e("ChatScreen", "❌ Lỗi khi gửi tin nhắn: ${e.message}")
                                            error = e.message
                                        }
                                    }
                                }
                            },
                            enabled = newMessage.trim().isNotEmpty(),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (newMessage.trim().isNotEmpty())
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Sharp.Send,
                                contentDescription = "Gửi",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, isSentByUser: Boolean) {
    val formattedTime = try {
        val instant = OffsetDateTime.parse(message.createdAt)
        instant.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        message.createdAt
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isSentByUser) 48.dp else 8.dp,
                end = if (isSentByUser) 8.dp else 48.dp
            ),
        contentAlignment = if (isSentByUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isSentByUser) 12.dp else 4.dp,
                        bottomEnd = if (isSentByUser) 4.dp else 12.dp
                    )
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isSentByUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = message.content ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSentByUser)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedTime ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSentByUser)
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
