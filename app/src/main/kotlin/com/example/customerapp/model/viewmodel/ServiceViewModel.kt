package com.example.customerapp.model.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customerapp.data.model.ServiceType
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import com.example.customerapp.data.model.ServiceTypeRepository

class ServiceViewModel(
    private val repository: ServiceTypeRepository = ServiceTypeRepository()
) : ViewModel() {

    var serviceTypes by mutableStateOf<List<ServiceType>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    fun loadServiceTypes() {
        viewModelScope.launch {
            isLoading = true
            serviceTypes = repository.getAllServiceTypes()
            isLoading = false
        }
    }
}
