package com.example.customerapp.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
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

class ChatViewModel : ViewModel() {
    private val repository = ChatRepository()

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

    private var channel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    fun loadData(userId: String, providerId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                // Lấy provider
                val provider = repository.getProvider(providerId)
                _providerName.value = provider.name ?: "Không rõ"

                // Lấy messages
                _messages.value = repository.getMessages(userId, providerId)

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

    private fun handleInsert(
        action: PostgresAction.Insert,
        userId: String,
        providerId: String
    ) {
        val newMsg = action.decodeRecord<Message>()
        if ((newMsg.senderId == userId && newMsg.receiverId == providerId) ||
            (newMsg.senderId == providerId && newMsg.receiverId == userId)
        ) {
            if (!isDuplicate(_messages.value, newMsg)) {
                _messages.value = _messages.value + newMsg
                viewModelScope.launch {
                    if (newMsg.senderId == providerId) {
                        repository.markMessageAsSeen(newMsg.id ?: "", userId)
                    }
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
        }
    }

    private fun handleDelete() {
        // Delete action không cần xử lý vì tin nhắn đã bị xóa khỏi DB
        Log.d("ChatViewModel", "Tin nhắn đã bị xóa")
    }

    private fun isDuplicate(existing: List<Message>, newMsg: Message): Boolean {
        return existing.any { it.id == newMsg.id ||
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
                repository.sendMessage(message)
                onSuccess()
            } catch (e: Exception) {
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