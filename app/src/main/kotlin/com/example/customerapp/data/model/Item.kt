package com.example.customerapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Item(

    val id: Int,
    @SerialName("service_type")
    val nameService: String,
    @SerialName("time")
    val description: String
)