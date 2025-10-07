package com.example.customerapp.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer

@Serializable
data class Report(
    val id: Long? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("booking_id")
    val bookingId: Long,
    @SerialName("provider_id")
    val providerId: String,
    val title: String,
    val description: String,
    @SerialName("image_urls")
    @Serializable(with = ImageUrlsSerializer::class)
    val imageUrls: List<String>? = null,
    val status: String = "pending", // pending, reviewed, resolved
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("admin_response")
    val adminResponse: String? = null,
    @SerialName("resolved_at")
    val resolvedAt: String? = null
) {
}

// Custom serializer for imageUrls to handle both string and array formats
object ImageUrlsSerializer : KSerializer<List<String>?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ImageUrls")

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: List<String>?) {
        when (encoder) {
            is kotlinx.serialization.json.JsonEncoder -> {
                encoder.encodeJsonElement(
                    value?.let { Json.encodeToJsonElement(it) } ?: JsonNull
                )
            }
            else -> {
                encoder.encodeNullableSerializableValue(
                    kotlinx.serialization.builtins.ListSerializer(serializer()),
                    value
                )
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): List<String>? {
        return when (decoder) {
            is kotlinx.serialization.json.JsonDecoder -> {
                val element = decoder.decodeJsonElement()
                when {
                    element is JsonNull -> null
                    element is JsonArray -> element.map { it.jsonPrimitive.content }
                    element is JsonPrimitive -> {
                        // Handle case where it's stored as a JSON string
                        try {
                            val jsonArray = Json.parseToJsonElement(element.content).jsonArray
                            jsonArray.map { it.jsonPrimitive.content }
                        } catch (e: Exception) {
                            // If parsing fails, return empty list
                            emptyList()
                        }
                    }
                    else -> emptyList()
                }
            }
            else -> {
                decoder.decodeNullableSerializableValue(
                    kotlinx.serialization.builtins.ListSerializer(serializer())
                )
            }
        }
    }
}

@Serializable
data class ReportInsert(
    @SerialName("user_id")
    val userId: String,
    @SerialName("booking_id")
    val bookingId: Long,
    @SerialName("provider_id")
    val providerId: String,
    val title: String,
    val description: String,
    @SerialName("image_urls")
    @Serializable(with = ImageUrlsSerializer::class)
    val imageUrls: List<String>? = null,
    val status: String = "pending"
)

enum class ReportStatus(val value: String) {
    PENDING("pending"),
    PROCESSING("processing"),
    RESOLVED("resolved"),
    REJECTED("rejected");

    companion object {
        fun from(status: String?): ReportStatus =
            values().find { it.value.equals(status, ignoreCase = true) } ?: PENDING
    }
}
