package com.example.customerapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PayoutRequest(
    val sender_batch_header: SenderBatchHeader,
    val items: List<PayoutItem>
)

@Serializable
data class SenderBatchHeader(
    val sender_batch_id: String,
    val email_subject: String
)

@Serializable
data class PayoutItem(
    val recipient_type: String = "EMAIL",
    val amount: PayoutAmount,
    val receiver: String,
    val note: String? = null
)

@Serializable
data class PayoutAmount(
    val value: String,
    val currency: String
)
