package com.example.testappcc.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val otherUser: User,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    @SerialName("last_message_time")
    val lastMessageTime: String? = null
) 