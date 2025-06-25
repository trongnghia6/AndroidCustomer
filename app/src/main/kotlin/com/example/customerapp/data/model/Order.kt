package com.example.customerapp.data.model

data class OrderItem(
    val name: String,
    val price: Int,
    val quantity: Int,
    val imageUrl: String? = null
)

data class Order(
    val id: String,
    val fromAddress: String,
    val toAddress: String,
    val items: List<OrderItem>,
    val total: Int,
    val shippingFee: Int,
    val surcharge: Int,
    val note: String?,
    val phone: String,
    val orderTime: String,
    val paymentStatus: String
) 