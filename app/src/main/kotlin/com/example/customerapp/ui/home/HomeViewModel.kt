package com.example.customerapp.ui.home

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.Service
import com.example.customerapp.data.model.ServiceType
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    // Trạng thái loading
    val isLoading: MutableState<Boolean> = mutableStateOf(false)

    var serviceTypes by mutableStateOf<List<ServiceType>>(emptyList())
        private set

    var services by mutableStateOf<List<Service>>(emptyList())
        private set

    var selectedTypeId by mutableStateOf<Int?>(null)

    init {
        loadServiceTypes()
    }

    fun loadServiceTypes(forceRefresh: Boolean = false) {
        // [Tối ưu 2 - Bộ nhớ đệm] Cache service type ngắn hạn để tránh gọi mạng khi user quay lại màn hình Home
        val cached = getCachedServiceTypes()
        if (cached != null && !forceRefresh) {
            serviceTypes = cached
            return
        }

        isLoading.value = true
        viewModelScope.launch {
            val freshData = supabase.from("service_types").select().decodeList<ServiceType>()
            cacheServiceTypes(freshData)
            serviceTypes = freshData
            isLoading.value = false
        }
    }

    fun loadServicesByType(typeId: Int, forceRefresh: Boolean = false) {
        selectedTypeId = typeId
        val cached = getCachedServices(typeId)
        if (cached != null && !forceRefresh) {
            services = cached
            return
        }

        isLoading.value = true
        viewModelScope.launch {
            val result = supabase.from("services")
                .select {
                    filter {
                        eq("service_type_id", typeId)
                    }
                }
                .decodeList<Service>()
            cacheServices(typeId, result)
            services = result
            isLoading.value = false
        }
    }

    fun clearSelection() {
        selectedTypeId = null
        services = emptyList()
    }

    private fun getCachedServiceTypes(): List<ServiceType>? {
        val cacheAge = System.currentTimeMillis() - lastServiceTypeFetchTimestamp
        return if (serviceTypesCache.isNotEmpty() && cacheAge < CACHE_TTL_MS) {
            serviceTypesCache
        } else {
            null
        }
    }

    private fun cacheServiceTypes(types: List<ServiceType>) {
        serviceTypesCache = types
        lastServiceTypeFetchTimestamp = System.currentTimeMillis()
    }

    private fun getCachedServices(typeId: Int): List<Service>? {
        val entry = servicesCache[typeId] ?: return null
        val cacheAge = System.currentTimeMillis() - entry.second
        return if (cacheAge < CACHE_TTL_MS) entry.first else null
    }

    private fun cacheServices(typeId: Int, list: List<Service>) {
        servicesCache[typeId] = list to System.currentTimeMillis()
    }

    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 phút
        private var serviceTypesCache: List<ServiceType> = emptyList()
        private var lastServiceTypeFetchTimestamp: Long = 0L
        private val servicesCache = mutableMapOf<Int, Pair<List<Service>, Long>>()

        fun clearCache() {
            serviceTypesCache = emptyList()
            servicesCache.clear()
            lastServiceTypeFetchTimestamp = 0L
        }
    }
}