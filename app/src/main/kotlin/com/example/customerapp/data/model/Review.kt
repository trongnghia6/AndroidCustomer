package com.example.customerapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Giả định model Review
@Serializable
data class Review(
    val id: Long,
    @SerialName("user_id")
    val userId: String,
    @SerialName("provider_service_id")
    val providerServiceId: Int,
    @SerialName("booking_id")
    val bookingId: Long,
    val rating: Int,
    val comment: String?,
    val responses: String?,
    @SerialName("created_at")
    val createdAt: String? = null,
)
@Serializable
data class ReviewInsert(
    @SerialName("user_id")
    val userId: String,
    @SerialName("provider_service_id")
    val providerServiceId: Int,
    @SerialName("booking_id")
    val bookingId: Long,
    val rating: Int,
    val comment: String?,
)