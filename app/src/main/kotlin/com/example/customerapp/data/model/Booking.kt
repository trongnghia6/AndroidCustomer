package com.example.testappcc.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Booking(
    val id: Long,
    val customer_id: String,
    val provider_service_id: Int,
    val status: String,
    val location: String? = null,
    val created_at: String? = null,
    val start_at: String? = null,
    val end_at: String? = null,
    val description: String? = null
)
@Serializable
data class Transaction(
    val id: Long? = null,
    @SerialName("booking_id")
    val bookingId: Int,
    val amount: Double,
    val status: String,
    @SerialName("payment_method")
    val paymentMethod: String? = null,
)