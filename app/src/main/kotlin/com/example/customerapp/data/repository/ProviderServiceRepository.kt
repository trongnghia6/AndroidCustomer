package com.example.customerapp.data.repository

import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.ProviderService
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.util.concurrent.ConcurrentHashMap

class ProviderServiceRepository {
    suspend fun getProvidersByServiceId(serviceId: Int, forceRefresh: Boolean = false): List<ProviderService> {
        val cacheKey = "service_$serviceId"
        if (!forceRefresh) {
            getCachedList(cacheKey)?.let { return it }
        }

        val providers = supabase.from("provider_services")
            .select(columns = Columns.list("*, user:users(*)")) {
                filter {
                    eq("service_id", serviceId)
                    eq("is_active", true)
                }
            }
            .decodeList<ProviderService>()

        cacheList(cacheKey, providers)
        return providers
    }

    suspend fun getProviderServiceById(id: Int, forceRefresh: Boolean = false): ProviderService? {
        if (!forceRefresh) {
            getCachedProvider(id)?.let { return it }
        }

        val provider = supabase.from("provider_services")
            .select(columns = Columns.list("*, user:users(*), service:services(*)")) {
                filter {
                    eq("id", id)
                }
            }
            .decodeSingleOrNull<ProviderService>()

        if (provider != null) {
            cacheProvider(id, provider)
        }
        return provider
    }

    private fun getCachedProvider(id: Int): ProviderService? {
        val cache = providerCache[id] ?: return null
        return if (System.currentTimeMillis() < cache.expiredAt) cache.value else null
    }

    private fun cacheProvider(id: Int, providerService: ProviderService) {
        providerCache[id] = CacheEntry(providerService, System.currentTimeMillis() + CACHE_TTL_MS)
    }

    private fun getCachedList(key: String): List<ProviderService>? {
        val cache = providersByServiceCache[key] ?: return null
        return if (System.currentTimeMillis() < cache.expiredAt) cache.value else null
    }

    private fun cacheList(key: String, value: List<ProviderService>) {
        providersByServiceCache[key] = CacheEntry(value, System.currentTimeMillis() + CACHE_TTL_MS)
    }

    // [Tối ưu 2 - Bộ nhớ đệm] TTL cache entry giúp tránh Supabase call lặp lại cho cùng provider/service
    private data class CacheEntry<T>(
        val value: T,
        val expiredAt: Long
    )

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private val providerCache = ConcurrentHashMap<Int, CacheEntry<ProviderService>>()
        private val providersByServiceCache = ConcurrentHashMap<String, CacheEntry<List<ProviderService>>>()

        fun clearCache() {
            providerCache.clear()
            providersByServiceCache.clear()
        }
    }
}
