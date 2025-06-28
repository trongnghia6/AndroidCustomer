package com.example.customerapp.data.repository

import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.ProviderService
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
            .select(columns = Columns.list("*, user:users(*), service:services(*)")) {
                filter {
                    eq("id", id)
                }
            }
            .decodeSingleOrNull()
    }
}

