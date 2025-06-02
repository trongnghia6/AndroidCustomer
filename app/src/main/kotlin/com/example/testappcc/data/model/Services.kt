package com.example.testappcc.data.model

import com.example.testappcc.core.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServiceType(
    val id: Int,
    val name: String,
    val description: String? = null,
    @SerialName("icon_url")
    val iconUrl: String? = null,
    @SerialName("is_active")
    val isActive: Boolean = true,
)

@Serializable
data class Service(
    val id: Int,
    @SerialName("service_type_id")
    val serviceTypeId: Int,
    val name: String,
    val description: String? = null,
    @SerialName("duration_minutes")
    val durationMinutes: Int? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("is_active")
    val isActive: Boolean = true,
)
class ServiceTypeRepository {

    suspend fun getAllServiceTypes(): List<ServiceType> {
        return supabase.from("service_types")
            .select()
            .decodeList()
    }

    suspend fun getServicesByType(serviceTypeId: Int): List<Service> {
        return supabase.from("services")
            .select(){
                filter {
                    eq("service_type_id", serviceTypeId)
                }
            }
            .decodeList()
    }
}


