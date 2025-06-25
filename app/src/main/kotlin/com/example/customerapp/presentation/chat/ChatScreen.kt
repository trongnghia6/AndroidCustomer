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
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
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
import androidx.navigation.NavController
import com.example.testappcc.core.supabase
import com.example.testappcc.data.model.Message
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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Objects.isNull
import com.example.testappcc.data.model.User


@Composable
fun ChatScreen(
    providerId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val userId = remember {
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getString("user_id", null) ?: ""
    }
    var providerName by remember { mutableStateOf("Không rõ") }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var newMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val channelState = remember { mutableStateOf<RealtimeChannel?>(null) }


    // Hàm đánh dấu tin nhắn đã xem
    suspend fun markMessageAsSeen(messageId: String, userId: String) {
        try {
            if (messageId.isNotEmpty()) {
                supabase.from("messages")
                    .update(mapOf("seen_at" to OffsetDateTime.now().toString())) {
                        filter {
                            eq("id", messageId)
                            eq("receiver_id", userId)
                            isNull("seen_at")
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e("ChatScreen", "❌ Lỗi khi đánh dấu tin nhắn đã xem: ${e.message}")
        }
    }

    // Hàm đánh dấu tất cả tin nhắn từ người gửi đã xem
    suspend fun markMessagesAsSeen(senderId: String, receiverId: String) {
        try {
            supabase.from("messages")
                .update(mapOf("seen_at" to OffsetDateTime.now().toString())) {
                    filter {
                        eq("sender_id", senderId)
                        eq("receiver_id", receiverId)
                        isNull("seen_at")
                    }
                }
        } catch (e: Exception) {
            Log.e("ChatScreen", "❌ Lỗi khi đánh dấu tất cả tin nhắn đã xem: ${e.message}")
        }
    }

    // Hàm tải dữ liệu
    fun loadData() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Ngắt kết nối kênh cũ nếu có
                channelState.value?.unsubscribe()
                channelState.value = null

                // Lấy thông tin người cung cấp
                val providerService = supabase.from("users").select(){
                    filter{
                        eq("id", providerId)
                    }
                }.decodeSingle<User>()
                providerName = providerService.name ?: "Không rõ"

                // Gọi connect để thiết lập WebSocket (chỉ khi chưa connect)
                if (supabase.realtime.status.value != Realtime.Status.CONNECTED) {
                    supabase.realtime.connect()
                }

                // Đánh dấu tất cả tin nhắn từ provider là đã xem
                markMessagesAsSeen(providerId, userId)


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



                // Subscription 1: INSERT - user gửi cho provider
                val insertFlow1 = channel.postgresChangeFlow<PostgresAction.Insert>(
                    schema = "public"
                ) {
                    table = "messages"
                    filter("sender_id", FilterOperator.EQ, userId)
                    filter("receiver_id", FilterOperator.EQ, providerId)
                }

                // Subscription 2: INSERT - provider gửi cho user
                val insertFlow2 = channel.postgresChangeFlow<PostgresAction.Insert>(
                    schema = "public"
                ) {
                    table = "messages"
                    filter("sender_id", FilterOperator.EQ, providerId)
                    filter("receiver_id", FilterOperator.EQ, userId)
                }

                // Subscription 3: UPDATE - cập nhật seen_at cho tin nhắn của user
                val updateFlow1 = channel.postgresChangeFlow<PostgresAction.Update>(
                    schema = "public"
                ) {
                    table = "messages"
                    filter("sender_id", FilterOperator.EQ, userId)
                    filter("receiver_id", FilterOperator.EQ, providerId)
                }

                // Subscription 4: UPDATE - cập nhật seen_at cho tin nhắn của provider
                val updateFlow2 = channel.postgresChangeFlow<PostgresAction.Update>(
                    schema = "public"
                ) {
                    table = "messages"
                    filter("sender_id", FilterOperator.EQ, providerId)
                    filter("receiver_id", FilterOperator.EQ, userId)
                }

                val changeFlow = merge(insertFlow1, insertFlow2, updateFlow1, updateFlow2)

                // Subscribe trước khi setup postgres changes
                channel.subscribe(blockUntilSubscribed = true)



                changeFlow.onEach { action ->
                    when (action) {
                        is PostgresAction.Insert -> {
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
                                        // Nếu tin nhắn mới từ provider, đánh dấu đã xem
                                        if (newMsg.senderId == providerId) {
                                            markMessageAsSeen(newMsg.id ?: "", userId)
                                        }
                                    }
                                } else {
                                    Log.d("ChatScreen", "⚠️ Tin nhắn đã tồn tại, bỏ qua")
                                }
                            }
                        }
                        is PostgresAction.Update -> {
                            val updatedMsg = action.decodeRecord<Message>()
                            
                            // Cập nhật tin nhắn đã có trong danh sách
                            messages = messages.map { message ->
                                if (message.id == updatedMsg.id) {
                                    updatedMsg
                                } else {
                                    message
                                }
                            }
                            Log.d("ChatScreen", "✅ Cập nhật trạng thái đã xem cho tin nhắn: ${updatedMsg.id}")
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
    LaunchedEffect(providerId) {
        loadData()
    }
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
            // Đánh dấu tin nhắn từ provider đã xem khi có tin nhắn mới
            val unseenMessages = messages.filter { 
                it.senderId == providerId && 
                it.receiverId == userId && 
                it.seenAt == null 
            }
            unseenMessages.forEach { message ->
                coroutineScope.launch(Dispatchers.IO) {
                    markMessageAsSeen(message.id ?: "", userId)
                }
            }
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
                    if (messages.isEmpty()) {
                        // Hiển thị thông báo khi chưa có tin nhắn
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "👋",
                                    style = MaterialTheme.typography.displayLarge,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Text(
                                    text = "Bắt đầu cuộc trò chuyện",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = "Hãy gửi tin nhắn đầu tiên với $providerName",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = formattedTime ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSentByUser)
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Hiển thị icon trạng thái chỉ cho tin nhắn người dùng gửi
                    if (isSentByUser) {
                        if (message.seenAt != null) {
                            // Đã xem - 2 dấu tích
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "Đã xem",
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            // Đã gửi - 1 dấu tích
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = "Đã gửi",
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
