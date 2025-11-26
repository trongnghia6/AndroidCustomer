package com.example.customerapp.data.local

import androidx.room.*

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationKey = :conversationKey ORDER BY createdAt ASC")
    suspend fun getMessages(conversationKey: String): List<MessageEntity>

    // Lấy tin nhắn mới nhất (trang đầu tiên)
    @Query("SELECT * FROM messages WHERE conversationKey = :conversationKey ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMessages(conversationKey: String, limit: Int): List<MessageEntity>

    // Lấy tin nhắn cũ hơn (phân trang) - so sánh theo createdAt và id
    @Query("""
        SELECT * FROM messages 
        WHERE conversationKey = :conversationKey 
        AND (createdAt < :lastCreatedAt OR (createdAt = :lastCreatedAt AND id < :lastId))
        ORDER BY createdAt DESC 
        LIMIT :limit
    """)
    suspend fun getMessagesBeforeCursor(
        conversationKey: String,
        lastCreatedAt: String,
        lastId: String,
        limit: Int
    ): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationKey = :conversationKey")
    suspend fun deleteConversation(conversationKey: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("UPDATE messages SET seenAt = :seenAt WHERE id = :messageId")
    suspend fun updateSeenAt(messageId: String, seenAt: String)
}

