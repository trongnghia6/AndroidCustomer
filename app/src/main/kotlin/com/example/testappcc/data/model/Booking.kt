package com.example.testappcc.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Booking(
    val id: Long? = null,
    val customer_id: String,
    val provider_service_id: Int,
    val status: String,
    val location: String? = null,
    val created_at: String? = null,
    val start_at: String? = null,
    val end_at: String? = null,
    val description: String? = null
) 