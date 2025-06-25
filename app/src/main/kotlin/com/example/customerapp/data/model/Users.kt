package com.example.testappcc.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Users(
    val id: String,
    val email: String,
    val password: String,
    val role: String,
    val username: String? = null
)