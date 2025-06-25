package com.example.testappcc.presentation.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.testappcc.core.supabase
import com.example.testappcc.data.model.Service
import com.example.testappcc.data.model.ServiceType
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

    fun loadServiceTypes() {
        isLoading.value = true
        viewModelScope.launch {
            serviceTypes = supabase.from("service_types").select().decodeList()
            isLoading.value = false
        }
    }

    fun loadServicesByType(typeId: Int) {
        isLoading.value = true
        viewModelScope.launch {
            selectedTypeId = typeId
            services = supabase.from("services")
                .select()
                .decodeList<Service>()
                .filter { it.serviceTypeId == typeId }
            isLoading.value = false
        }
    }

    fun clearSelection() {
        selectedTypeId = null
        services = emptyList()
    }
}