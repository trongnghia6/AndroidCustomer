package com.example.testappcc.data.repository

import com.example.testappcc.core.supabase
import com.example.testappcc.data.model.ProviderService
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

class ProviderServiceRepository {
    suspend fun getProvidersByServiceId(serviceId: Int): List<ProviderService> {
        return supabase.from("provider_services")
            .select(columns = Columns.list("*, user:users(*)")) {
                filter {
                    eq("service_id", serviceId)
                    eq("is_active", true)
                }
            }
            .decodeList()
    }

    suspend fun getProviderServiceById(id: Int): ProviderService? {
        return supabase.from("provider_services")
            .select(columns = Columns.list("*, user:users(*)")) {
                filter {
                    eq("id", id)
                }
            }
            .decodeSingleOrNull()
    }
} 