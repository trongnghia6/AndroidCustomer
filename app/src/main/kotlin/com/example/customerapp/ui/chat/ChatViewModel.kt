package com.example.customerapp.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.customerapp.data.model.Message
import com.example.customerapp.data.repository.ChatRepository
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.decodeRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import kotlin.math.abs

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application.applicationContext)
    private val pageSize = 20

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()
    
    private val _providerName = MutableStateFlow("")
    val providerName: StateFlow<String> = _providerName.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        // Debug StateFlow changes
        _isLoading.onEach { Log.d("ChatViewModel", "🔄 isLoading changed to: $it") }.launchIn(viewModelScope)
        _messages.onEach { Log.d("ChatViewModel", "🔄 messages changed to: ${it.size} items") }.launchIn(viewModelScope)
        _providerName.onEach { Log.d("ChatViewModel", "🔄 providerName changed to: $it") }.launchIn(viewModelScope)
    }
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var channel: io.github.jan.supabase.realtime.RealtimeChannel? = null
    private var currentUserId: String? = null
    private var currentProviderId: String? = null

    fun loadData(userId: String, providerId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                _hasMore.value = true
                currentUserId = userId
                currentProviderId = providerId

                // Lấy provider
                val provider = repository.getProvider(providerId)
                _providerName.value = provider.name ?: "Không rõ"

                // Lấy messages
                val initialMessages = repository.loadChatMessages(userId, providerId, null)

                _messages.value = initialMessages
                _hasMore.value = initialMessages.size >= pageSize

                // Mark đã xem
                repository.markMessagesAsSeen(providerId, userId)

                // Subcribe realtime (async để không block)
                viewModelScope.launch {
                    try {
                        val (chan, flow) = repository.subscribeToMessages(userId, providerId)
                        channel = chan
                        chan.subscribe(blockUntilSubscribed = true)

                        flow.onEach { action ->
                        when (action) {
                            is PostgresAction.Insert -> handleInsert(action, userId, providerId)
                                is PostgresAction.Update -> handleUpdate(action, userId, providerId)
                                is PostgresAction.Delete -> handleDelete()
                                else -> {
                                    Log.d("ChatViewModel", "⚠️ Tin nhắn đã tồn tại, bỏ qua")
                                }
                            }
                        }.launchIn(viewModelScope)
                    } catch (e: Exception) {
                        Log.w("ChatViewModel", "⚠️ Không thể kết nối realtime, tiếp tục với chế độ offline: ${e.message}")
                        // Tiếp tục mà không có realtime - vẫn có thể chat bình thường
                    }
                }

                _isLoading.value = false
            } catch (e: CancellationException) {
                Log.d("ChatViewModel", "⚠️ Load data bị cancel")
                throw e
            } catch (e: Exception) {
                Log.e("ChatViewModel", "❌ Lỗi trong loadData: ${e.message}", e)
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun loadMoreMessages() {
        val userId = currentUserId ?: return
        val providerId = currentProviderId ?: return
        
        // Lấy tin nhắn ĐẦU TIÊN (cũ nhất) trong list làm cursor
        val cursor = _messages.value.firstOrNull() ?: return

        if (_isLoadingMore.value || !_hasMore.value) {
            Log.d("ChatViewModel", "⚠️ Đang load hoặc hết tin nhắn, bỏ qua")
            return
        }

        viewModelScope.launch {
            try {
                _isLoadingMore.value = true
                Log.d("ChatViewModel", "📥 Load thêm tin nhắn cũ hơn ${cursor.createdAt}")
                
                val olderMessages = repository.loadChatMessages(userId, providerId, cursor)
                
                if (olderMessages.isNotEmpty()) {
                    // Thêm tin cũ vào ĐẦU list (vì list đã reversed: cũ -> mới)
                    _messages.value = olderMessages + _messages.value
                    _hasMore.value = olderMessages.size >= pageSize
                    Log.d("ChatViewModel", "✅ Đã load thêm ${olderMessages.size} tin nhắn")
                } else {
                    _hasMore.value = false
                    Log.d("ChatViewModel", "✅ Đã hết tin nhắn cũ")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "❌ Lỗi khi load thêm tin nhắn: ${e.message}")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private fun handleInsert(
        action: PostgresAction.Insert,
        userId: String,
        providerId: String
    ) {
        val newMsg = action.decodeRecord<Message>()
        if ((newMsg.senderId == userId && newMsg.receiverId == providerId) ||
            (newMsg.senderId == providerId && newMsg.receiverId == userId)
        ) {
            // Kiểm tra xem có tin nhắn tạm nào khớp với tin nhắn này không
            val tempMessageIndex = _messages.value.indexOfFirst { 
                it.id?.startsWith("temp_") == true &&
                it.senderId == newMsg.senderId &&
                it.receiverId == newMsg.receiverId &&
                it.content == newMsg.content
            }
            
            if (tempMessageIndex >= 0) {
                // Thay thế tin nhắn tạm bằng tin nhắn thật
                val updatedMessages = _messages.value.toMutableList()
                updatedMessages[tempMessageIndex] = newMsg
                _messages.value = updatedMessages
                Log.d("ChatViewModel", "✅ Đã thay thế tin nhắn tạm bằng tin nhắn thật: ${newMsg.id}")
            } else if (!isDuplicate(_messages.value, newMsg)) {
                // Tin nhắn mới từ người khác hoặc từ device khác
                _messages.value = _messages.value + newMsg
                Log.d("ChatViewModel", "✅ Đã thêm tin nhắn mới từ realtime: ${newMsg.id}")
            }
            
            // Lưu tin nhắn mới vào cache ngay lập tức
            viewModelScope.launch {
                repository.cacheRealtimeMessage(newMsg)
                
                if (newMsg.senderId == providerId) {
                    repository.markMessageAsSeen(newMsg.id ?: "", userId)
                }
            }
        }
    }

    private fun handleUpdate(action: PostgresAction.Update, userId: String, providerId: String) {
        val updatedMsg = action.decodeRecord<Message>()
        if ((updatedMsg.senderId == userId && updatedMsg.receiverId == providerId) ||
            (updatedMsg.senderId == providerId && updatedMsg.receiverId == userId)
        ) {
            _messages.value = _messages.value.map { if (it.id == updatedMsg.id) updatedMsg else it }
            
            // Cập nhật tin nhắn trong cache (ví dụ: seen_at thay đổi)
            viewModelScope.launch {
                repository.updateCachedMessage(updatedMsg)
            }
        }
    }

    private fun handleDelete() {
        // Delete action không cần xử lý vì tin nhắn đã bị xóa khỏi DB
        Log.d("ChatViewModel", "Tin nhắn đã bị xóa")
    }

    private fun isDuplicate(existing: List<Message>, newMsg: Message): Boolean {
        return existing.any { 
            // Bỏ qua tin nhắn tạm khi check duplicate
            val isTempMessage = it.id?.startsWith("temp_") == true
            if (isTempMessage) return@any false
            
            // Check duplicate bằng ID hoặc content + time
            it.id == newMsg.id ||
                (it.content == newMsg.content &&
                        it.senderId == newMsg.senderId &&
                        it.receiverId == newMsg.receiverId &&
                        abs(
                            (it.createdAt?.let { t -> runCatching { OffsetDateTime.parse(t).toEpochSecond() }.getOrDefault(0L) } ?: 0L) -
                                    (newMsg.createdAt?.let { t -> runCatching { OffsetDateTime.parse(t).toEpochSecond() }.getOrDefault(0L) } ?: 0L)
                        ) < 2)
        }
    }

    fun sendMessage(message: Message, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // 1. Thêm tin nhắn vào UI ngay lập tức (optimistic update)
                val tempMessage = message.copy(id = "temp_${System.currentTimeMillis()}")
                _messages.value = _messages.value + tempMessage
                Log.d("ChatViewModel", "✅ Đã thêm tin nhắn tạm vào UI: ${tempMessage.id}")
                
                // 2. Gửi lên server
                repository.sendMessage(message)
                onSuccess()
                
                Log.d("ChatViewModel", "✅ Tin nhắn đã gửi thành công lên server")
            } catch (e: Exception) {
                // Nếu gửi thất bại, xóa tin nhắn tạm khỏi UI
                _messages.value = _messages.value.filter { it.id?.startsWith("temp_") ?: false }
                Log.e("ChatViewModel", "❌ Lỗi khi gửi tin nhắn: ${e.message}")
                onError(e.message ?: "Lỗi không xác định")
            }
        }
    }

    fun markMessageAsSeen(messageId: String, userId: String) {
        viewModelScope.launch {
            repository.markMessageAsSeen(messageId, userId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try {
                channel?.unsubscribe()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Lỗi khi unsubscribe channel", e)
            }
        }
    }
}