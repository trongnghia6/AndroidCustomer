package com.example.testappcc.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BookingResponse(
    val id: Int // Giả định id là Long, có thể cần chỉnh sửa nếu khác
) 