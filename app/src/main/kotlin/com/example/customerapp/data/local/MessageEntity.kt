package com.example.customerapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.customerapp.data.model.Message

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val senderId: String,
    val receiverId: String,
    val content: String?,
    val createdAt: String?,
    val seenAt: String?,
    val conversationKey: String // "userId1_userId2" để query nhanh
) {
    fun toMessage(): Message {
        return Message(
            id = id,
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            createdAt = createdAt,
            seenAt = seenAt
        )
    }

    companion object {
        fun fromMessage(message: Message, userId1: String, userId2: String): MessageEntity {
            // Tạo key chuẩn hóa (luôn sắp xếp userId theo thứ tự)
            val key = if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
            
            return MessageEntity(
                id = message.id ?: "",
                senderId = message.senderId ?: "",
                receiverId = message.receiverId ?: "",
                content = message.content,
                createdAt = message.createdAt,
                seenAt = message.seenAt,
                conversationKey = key
            )
        }
    }
}

