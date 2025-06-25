package com.example.testappcc.data.repository

import android.util.Log
import com.example.testappcc.core.supabase
import com.example.testappcc.data.model.Conversation
import com.example.testappcc.data.model.Message
import com.example.testappcc.data.model.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator

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
    

} 