package com.example.customerapp.data.model

import kotlinx.datetime.*
import java.time.format.DateTimeFormatter

fun formatTimestampToUserTimezonePretty(
    timestamp: String?,
    userTimeZone: TimeZone = TimeZone.currentSystemDefault()
): String {
    if (timestamp.isNullOrEmpty()) return ""

    val instant = Instant.parse(timestamp)
    val localDateTime = instant.toLocalDateTime(userTimeZone)

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    return java.time.LocalDateTime.of(
        localDateTime.year,
        localDateTime.monthNumber,
        localDateTime.dayOfMonth,
        localDateTime.hour,
        localDateTime.minute
    ).format(formatter)
}