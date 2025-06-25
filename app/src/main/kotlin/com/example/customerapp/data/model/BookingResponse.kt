package com.example.customerapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BookingResponse(
    val id: Int,
    @SerialName("customer_id")
    val customerId: String,
    @SerialName("provider_service_id")
    val providerServiceId: Int,
    val status: String,
    val location: String? = null,
    @SerialName("start_at")
    val startAt: String? = null,
    @SerialName("end_at")
    val endAt: String? = null,
    @SerialName("number_workers")
    val numWorkers: Int,
)