package com.example.customerapp.data.repository

import android.util.Log
import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.Conversation
import com.example.customerapp.data.model.Message
import com.example.customerapp.data.model.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import java.time.OffsetDateTime
import java.util.Objects.isNull

class ChatRepository {
    
    // Lấy danh sách cuộc trò chuyện của user
    suspend fun getConversations(currentUserId: String): List<Conversation> {
        return try {
            // Lấy tất cả tin nhắn có liên quan đến user hiện tại
            val messages = supabase.from("messages").select {
                filter {
                    or {
                        eq("sender_id", currentUserId)
                        eq("receiver_id", currentUserId)
                    }
                }
                order(column = "created_at", order = Order.DESCENDING)
            }.decodeList<Message>()

            // Group messages theo conversation và lấy thông tin cần thiết
            val conversationMap = mutableMapOf<String, MutableList<Message>>()
            
            messages.forEach { message ->
                val otherUserId = if (message.senderId == currentUserId) {
                    message.receiverId ?: ""
                } else {
                    message.senderId ?: ""
                }
                
                if (otherUserId.isNotEmpty()) {
                    conversationMap.getOrPut(otherUserId) { mutableListOf() }.add(message)
                }
            }

            // Chuyển đổi thành Conversation objects
            val conversations = mutableListOf<Conversation>()
            
            conversationMap.forEach { (otherUserId, messageList) ->
                // Lấy thông tin user
                val otherUser = getUserById(otherUserId)
                if (otherUser != null) {
                    val lastMessage = messageList.firstOrNull() // Đã sort DESC nên first là mới nhất
                    val unreadCount = messageList.count { 
                        it.receiverId == currentUserId && it.seenAt == null 
                    }
                    
                    conversations.add(
                        Conversation(
                            otherUser = otherUser,
                            lastMessage = lastMessage,
                            unreadCount = unreadCount,
                            lastMessageTime = lastMessage?.createdAt
                        )
                    )
                }
            }
            
            // Sort theo thời gian tin nhắn cuối cùng
            conversations.sortedByDescending { 
                it.lastMessageTime ?: ""
            }
            
        } catch (e: Exception) {
            Log.e("ChatRepository", "❌ Lỗi khi lấy danh sách conversation: ${e.message}")
            emptyList()
        }
    }
    
    // Tìm kiếm người dùng
    suspend fun searchUsers(query: String, currentUserId: String): List<User> {
        return try {
            Log.d("ChatRepository", "🔍 Tìm kiếm user với query: $query")
            if (query.trim().isEmpty()) return emptyList()
            
            supabase.from("users").select {
                filter {
                    and {
                        neq("id", currentUserId) // Loại trừ user hiện tại
                        or {
                            ilike("name", "%$query%")
                            ilike("email", "%$query%")
                        }
                    }
                }
                limit(10) // Giới hạn 10 kết quả
            }.decodeList<User>()
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Không log lỗi khi coroutine bị cancel (đây là hành vi bình thường)
            throw e // Re-throw để ViewModel có thể xử lý
        } catch (e: Exception) {
            Log.e("ChatRepository", "❌ Lỗi khi tìm kiếm user: ${e.message}")
            emptyList()
        }
    }
    
    // Lấy thông tin user theo ID
    private suspend fun getUserById(userId: String): User? {
        return try {
            val users = supabase.from("users").select {
                filter {
                    eq("id", userId)
                }
            }.decodeList<User>()
            
            users.firstOrNull()
        } catch (e: Exception) {
            Log.e("ChatRepository", "❌ Lỗi khi lấy thông tin user: ${e.message}")
            null
        }
    }

    // Gửi tin nhắn mới
    suspend fun sendMessage(message: Message) {
        try {
            supabase.from("messages").insert(message)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Không log lỗi khi coroutine bị cancel
            throw e
        } catch (e: Exception) {
            Log.e("ChatRepository", "❌ Lỗi khi gửi tin nhắn: ${e.message}")
            throw e
        }
    }

    suspend fun getProvider(providerId: String): User {
        return try {
            supabase.from("users").select {
                filter {
                    eq("id", providerId)
                }
            }.decodeSingle<User>()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Không log lỗi khi coroutine bị cancel
            throw e
        } catch (e: Exception) {
            Log.e("ChatRepository", "❌ Lỗi khi lấy thông tin provider: ${e.message}")
            throw e
        }
    }

    suspend fun getMessages(userId: String, providerId: String): List<Message> {
        return try {
            supabase.from("messages").select {
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Không log lỗi khi coroutine bị cancel
            throw e
        } catch (e: Exception) {
            Log.e("ChatRepository", "❌ Lỗi khi lấy danh sách tin nhắn: ${e.message}")
            emptyList()
        }
    }

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
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Không log lỗi khi coroutine bị cancel
            throw e
        } catch (e: Exception) {
            Log.e("ChatRepository", "❌ Lỗi khi đánh dấu tin nhắn đã xem: ${e.message}")
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Không log lỗi khi coroutine bị cancel
            throw e
        } catch (e: Exception) {
            Log.e("ChatRepository", "❌ Lỗi khi đánh dấu tất cả tin nhắn đã xem: ${e.message}")
        }
    }

    // Subcribe realtime channel cho 1 cuộc trò chuyện
    suspend fun subscribeToMessages(userId: String, providerId: String): Pair<RealtimeChannel, Flow<PostgresAction>> {
        val channelName = "chat:${minOf(userId, providerId)}-${maxOf(userId, providerId)}"
        val channel = supabase.channel(channelName)

        val insertFlow1 = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("sender_id", FilterOperator.EQ, userId)
            filter("receiver_id", FilterOperator.EQ, providerId)
        }

        val insertFlow2 = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("sender_id", FilterOperator.EQ, providerId)
            filter("receiver_id", FilterOperator.EQ, userId)
        }

        val updateFlow1 = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "messages"
            filter("sender_id", FilterOperator.EQ, userId)
            filter("receiver_id", FilterOperator.EQ, providerId)
        }

        val updateFlow2 = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "messages"
            filter("sender_id", FilterOperator.EQ, providerId)
            filter("receiver_id", FilterOperator.EQ, userId)
        }

        val mergedFlow = merge(insertFlow1, insertFlow2, updateFlow1, updateFlow2)

        return channel to mergedFlow
    }
} 