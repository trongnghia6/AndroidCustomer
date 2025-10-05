package com.example.customerapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

@Serializable
data class Users(
    val id: String,
    val email: String,
    val password: String,
    val role: String,
    val username: String? = null
)

@Serializable
data class BookingWithProvider(
    val id: Int,
    @SerialName("provider_service_id")
    val providerServiceId: Int,
    @SerialName("provider_services")
    val providerServices: ProviderServiceWithUser
)

@Serializable
data class ProviderServiceWithUser(
    val id: Int,
    @SerialName("provider_id")
    val providerId: String,
    val users: UserEmail
)

@Serializable
data class UserEmail(
    @SerialName("paypal_email")
    val paypalEmail: String
)
