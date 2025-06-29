package com.example.customerapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val id: String = "",
    @SerialName("user_id")
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val type: String = "general", // general, booking, order, system
    @SerialName("is_read")
    val isRead: Boolean = false,
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("data")
    val data: Map<String, String>? = null // Extra data like booking_id, order_id etc.
)

@Serializable
data class NotificationInsert(
    @SerialName("user_id")
    val userId: String,
    val title: String,
    val body: String,
    val type: String = "general",
    @SerialName("data")
    val data: Map<String, String>? = null
)

@Serializable
data class NotificationUpdate(
    @SerialName("is_read")
    val isRead: Boolean
) 