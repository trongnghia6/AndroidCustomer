package com.example.customerapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String,
    val password: String? = null,
    val role: String,
    val name: String? = null,
    val username: String? = null,
    val address: String? = null,
    val phone_number: String? = null,
    val avatar: String? = null
)