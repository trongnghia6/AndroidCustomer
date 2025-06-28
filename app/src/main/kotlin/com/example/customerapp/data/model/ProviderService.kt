package com.example.customerapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProviderService(
    val id: Int,
    @SerialName("provider_id")
    val providerId: String, // uuid
    @SerialName("service_id")
    val serviceId: Int,
    @SerialName("custom_price")
    val customPrice: Double? = null,
    @SerialName("custom_description")
    val customDescription: String? = null,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("duration_minutes")
    val durationMinutes: Int? = null,
    val name: String? = null,
    @SerialName("user")
    val user: User? = null,
    @SerialName("service")
    val service: Service? = null
)

