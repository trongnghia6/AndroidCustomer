package com.example.customerapp.ui.chat

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
import androidx.compose.runtime.collectAsState
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
import com.example.customerapp.data.model.Message
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter


@Composable
fun ChatScreen(
    providerId: String,
    navController: NavController,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val userId = remember {
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            .getString("user_id", null) ?: ""
    }
    var newMessage by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Sử dụng ViewModel state - cần observe để UI cập nhật
    val providerName by viewModel.providerName.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    // Debug logs
    LaunchedEffect(providerName, messages.size, isLoading) {
        Log.d("ChatScreen", "🔄 UI State update - providerName: $providerName, messages: ${messages.size}, isLoading: $isLoading")
    }


    // Gọi hàm tải dữ liệu
    LaunchedEffect(userId, providerId) {
        viewModel.loadData(userId, providerId)
    }
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            // Cuộn tới tin nhắn cuối cùng
            listState.animateScrollToItem(messages.lastIndex)

            // Đánh dấu các tin nhắn mới từ provider là đã xem
            messages.filter {
                it.senderId == providerId &&
                        it.receiverId == userId &&
                        it.seenAt == null
            }.forEach { message ->
                // Gọi suspend trực tiếp trong LaunchedEffect
                viewModel.markMessageAsSeen(message.id ?: "", userId)
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
                                    viewModel.sendMessage(
                                        message = message,
                                        onSuccess = { newMessage = "" },
                                        onError = { errorMessage -> 
                                            Log.e("ChatScreen", "❌ Lỗi khi gửi tin nhắn: $errorMessage")
                                        }
                                    )
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
        Log.e("MessageBubble", "❌ Lỗi định dạng thời gian: ${e.message}")
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
