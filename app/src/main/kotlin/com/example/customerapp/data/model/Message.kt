package com.example.customerapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message (
    val id: String? = null,
    val content: String? = null,
    @SerialName("sender_id")
    val senderId: String? = null,
    @SerialName("receiver_id")
    val receiverId: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("seen_at")
    val seenAt: String? = null
)

