package com.example.customerapp.ui.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customerapp.data.model.ProviderService
import com.example.customerapp.data.repository.ProviderServiceRepository
import kotlinx.coroutines.launch

class ServiceDetailViewModel(
    private val repository: ProviderServiceRepository = ProviderServiceRepository()
) : ViewModel() {
    var providers by mutableStateOf<List<ProviderService>>(emptyList())
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    var error by mutableStateOf<String?>(null)
        private set

    fun loadProviders(serviceId: Int) {
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                providers = repository.getProvidersByServiceId(serviceId)
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }
} 