package com.example.customerapp.data.repository

import android.content.Context
import android.util.Log
import com.example.customerapp.core.supabase
import com.example.customerapp.data.local.AppDatabase
import com.example.customerapp.data.local.MessageEntity
import com.example.customerapp.data.model.Conversation
import com.example.customerapp.data.model.Message
import com.example.customerapp.data.model.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.MDC.put
import java.time.OffsetDateTime
import java.util.Objects.isNull

class ChatRepository(private val context: Context? = null) {
    private val messageDao by lazy { 
        context?.let { AppDatabase.getDatabase(it).messageDao() }
    }
    
    // Lấy danh sách cuộc trò chuyện của user
//    suspend fun getConversations(currentUserId: String): List<Conversation> {
//        return try {
//            val messages = supabase.from("messages").select {
//                filter {
//                    or {
//                        eq("sender_id", currentUserId)
//                        eq("receiver_id", currentUserId)
//                    }
//                }
//                order(column = "created_at", order = Order.DESCENDING)
//            }.decodeList<Message>()
//
//            // Group messages theo conversation và lấy thông tin cần thiết
//            val conversationMap = mutableMapOf<String, MutableList<Message>>()
//
//            messages.forEach { message ->
//                val otherUserId = if (message.senderId == currentUserId) {
//                    message.receiverId ?: ""
//                } else {
//                    message.senderId ?: ""
//                }
//
//                if (otherUserId.isNotEmpty()) {
//                    conversationMap.getOrPut(otherUserId) { mutableListOf() }.add(message)
//                }
//            }
//
//            val conversations = mutableListOf<Conversation>()
//
//            conversationMap.forEach { (otherUserId, messageList) ->
//                val otherUser = getUserById(otherUserId)
//                if (otherUser != null) {
//                    val lastMessage = messageList.firstOrNull() // Đã sort DESC nên first là mới nhất
//                    val unreadCount = messageList.count {
//                        it.receiverId == currentUserId && it.seenAt == null
//                    }
//
//                    conversations.add(
//                        Conversation(
//                            otherUser = otherUser,
//                            lastMessage = lastMessage,
//                            unreadCount = unreadCount,
//                            lastMessageTime = lastMessage?.createdAt
//                        )
//                    )
//                }
//            }
//
//            // Sort theo thời gian tin nhắn cuối cùng
//            conversations.sortedByDescending {
//                it.lastMessageTime ?: ""
//            }
//
//        } catch (e: Exception) {
//            Log.e("ChatRepository", "❌ Lỗi khi lấy danh sách conversation: ${e.message}")
//            emptyList()
//        }
//    }

    suspend fun getConversations(currentUserId: String): List<Conversation> {
        return supabase.postgrest.rpc(
            function = "get_conversations_nested",
            parameters = mapOf("current_user_id" to currentUserId)
        ).decodeList<Conversation>()
        // Supabase sẽ tự động điền JSON "otherUser" vào biến otherUser: User
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
            
            // Lưu vào cache ngay lập tức
            if (messageDao != null && message.senderId != null && message.receiverId != null) {
                try {
                    val entity = MessageEntity.fromMessage(
                        message, 
                        message.senderId!!, 
                        message.receiverId!!
                    )
                    messageDao!!.insertMessage(entity)
                    Log.d("ChatRepository", "💾 Đã lưu tin nhắn gửi đi vào cache: ${message.id}")
                } catch (e: Exception) {
                    Log.e("ChatRepository", "❌ Lỗi lưu tin nhắn vào cache: ${e.message}")
                }
            }
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

    suspend fun loadChatMessages(
        myUserId: String,
        partnerUserId: String,
        lastMessage: Message? = null // Null nếu là trang đầu tiên
    ): List<Message> {
        val conversationKey = if (myUserId < partnerUserId) 
            "${myUserId}_${partnerUserId}" 
        else 
            "${partnerUserId}_${myUserId}"

        // 1. Thử load từ cache trước (cả trang đầu và phân trang)
        if (messageDao != null) {
            try {
                val cachedMessages = if (lastMessage == null) {
                    // Trang đầu: lấy 20 tin nhắn mới nhất
                    Log.d("ChatRepository", "🔍 Tìm kiếm cache: trang đầu (20 tin mới nhất)")
                    messageDao!!.getRecentMessages(conversationKey, 20)
                } else {
                    // Phân trang: lấy 20 tin nhắn cũ hơn lastMessage
                    Log.d("ChatRepository", "🔍 Tìm kiếm cache: tin nhắn cũ hơn ${lastMessage.createdAt} (id=${lastMessage.id})")
                    messageDao!!.getMessagesBeforeCursor(
                        conversationKey = conversationKey,
                        lastCreatedAt = lastMessage.createdAt ?: "",
                        lastId = lastMessage.id ?: "",
                        limit = 20
                    )
                }
                
                if (cachedMessages.isNotEmpty()) {
                    Log.d("ChatRepository", "✅ Tìm thấy ${cachedMessages.size} tin nhắn trong cache")
                    // Reverse vì query DESC nhưng cần hiển thị ASC (cũ -> mới)
                    return cachedMessages.reversed().map { it.toMessage() }
                } else {
                    Log.d("ChatRepository", "⚠️ Không tìm thấy tin nhắn trong cache, sẽ load từ server")
                }
            } catch (e: Exception) {
                Log.e("ChatRepository", "❌ Lỗi load cache: ${e.message}")
            }
        }

        // 2. Nếu không có cache hoặc cache rỗng, gọi server
        val params = buildJsonObject {
            put("p_user1", myUserId)
            put("p_user2", partnerUserId)
            put("p_limit", 20)

            if (lastMessage != null) {
                put("p_last_created_at", lastMessage.createdAt)
                put("p_last_id", lastMessage.id)
            }
        }

        // 3. Gọi RPC
        val messages = supabase.postgrest.rpc(
            function = "get_chat_history",
            parameters = params
        ).decodeList<Message>()
        
        messages.forEach { message ->
            Log.d("ChatRepository", "📝 Tin nhắn tải về từ server: ${message.id} | ${message.content} | ${message.createdAt} | seen_at=${message.seenAt}")
        }
        
        // 4. Lưu vào cache
        if (messageDao != null && messages.isNotEmpty()) {
            try {
                val entities = messages.map { 
                    MessageEntity.fromMessage(it, myUserId, partnerUserId) 
                }
                messageDao!!.insertMessages(entities)
                Log.d("ChatRepository", "💾 Đã lưu ${entities.size} tin nhắn vào cache")
            } catch (e: Exception) {
                Log.e("ChatRepository", "❌ Lỗi lưu cache: ${e.message}")
            }
        }
        
        // 5. Reverse để hiển thị đúng thứ tự (cũ -> mới)
        return messages.reversed()
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

    // Xóa toàn bộ cache tin nhắn (dùng khi logout hoặc clear data)
    suspend fun clearAllCache() {
        if (messageDao == null) {
            Log.w("ChatRepository", "⚠️ MessageDao null, không thể xóa cache")
            return
        }
        
        try {
            messageDao!!.deleteAll()
            Log.d("ChatRepository", "🗑️ Đã xóa toàn bộ cache tin nhắn")
        } catch (e: Exception) {
            Log.e("ChatRepository", "❌ Lỗi xóa cache: ${e.message}")
        }
    }
    
    // Xóa cache của một cuộc trò chuyện cụ thể
    suspend fun clearConversationCache(userId1: String, userId2: String) {
        if (messageDao == null) {
            return
        }
        
        try {
            val conversationKey = if (userId1 < userId2) 
                "${userId1}_${userId2}" 
            else 
                "${userId2}_${userId1}"
            
            messageDao!!.deleteConversation(conversationKey)
            Log.d("ChatRepository", "🗑️ Đã xóa cache cuộc trò chuyện: $conversationKey")
        } catch (e: Exception) {
            Log.e("ChatRepository", "❌ Lỗi xóa cache cuộc trò chuyện: ${e.message}")
        }
    }
    
    // Lưu tin nhắn realtime vào cache
    suspend fun cacheRealtimeMessage(message: Message) {
        if (messageDao == null || message.senderId == null || message.receiverId == null) {
            return
        }
        
        try {
            val entity = MessageEntity.fromMessage(
                message, 
                message.senderId!!, 
                message.receiverId!!
            )
            messageDao!!.insertMessage(entity)
            Log.d("ChatRepository", "💾 Đã lưu tin nhắn realtime vào cache: ${message.id}")
        } catch (e: Exception) {
            Log.e("ChatRepository", "❌ Lỗi lưu tin nhắn realtime vào cache: ${e.message}")
        }
    }
    
    // Cập nhật tin nhắn trong cache (khi có update như seen_at)
    suspend fun updateCachedMessage(message: Message) {
        if (messageDao == null || message.senderId == null || message.receiverId == null) {
            return
        }
        
        try {
            val entity = MessageEntity.fromMessage(
                message, 
                message.senderId!!, 
                message.receiverId!!
            )
            messageDao!!.insertMessage(entity) // REPLACE strategy sẽ update
            Log.d("ChatRepository", "💾 Đã cập nhật tin nhắn trong cache: ${message.id}")
        } catch (e: Exception) {
            Log.e("ChatRepository", "❌ Lỗi cập nhật tin nhắn trong cache: ${e.message}")
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