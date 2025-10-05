package com.example.customerapp.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Voucher(
    val id: Long,
    val name: String,
    val describe: String,
    val discount: Double, // Phần trăm giảm giá (0-100)
    val status: String, // active, inactive
    @SerialName("created_at")
    val createdAt: String? = null,
    val date: String? = null // Ngày hết hạn
) {
    fun isValid(): Boolean {
        return status == "active"
    }
    
    fun getDiscountAmount(total: Double): Double {
        return (total * discount) / 100.0
    }
    
    fun getFinalAmount(total: Double): Double {
        return total - getDiscountAmount(total)
    }
}
