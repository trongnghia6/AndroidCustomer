package com.example.customerapp.data.model

@kotlinx.serialization.Serializable
data class CreateOrderResponse(
    val orderId: String,
    val approvalUrl: String
)