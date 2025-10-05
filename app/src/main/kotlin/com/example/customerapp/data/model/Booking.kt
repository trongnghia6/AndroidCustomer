package com.example.customerapp.data.model

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
    val description: String? = null,
    val number_workers: Int? = null
)

@Serializable
data class Transaction(
    val id: String? = null,
    @SerialName("booking_id")
    val bookingId: Int,
    @SerialName("provider_services_id")
    val providerServicesId: Int? = null,
    val amount: Double,
    val status: String,
    @SerialName("payment_method")
    val paymentMethod: String? = null,
    @SerialName("capture_id")
    val captureId: String? = null,
    @SerialName("paypal_order_id")
    val paypalOrderId: String? = null,
    @SerialName("payout_id")
    val payoutId: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
)

