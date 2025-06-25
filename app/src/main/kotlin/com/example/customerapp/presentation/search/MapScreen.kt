package com.example.customerapp.presentation.search


import androidx.compose.runtime.*
import com.example.customerapp.core.network.MapboxGeocodingService
import com.example.customerapp.data.model.AddressAutoCompleteScreen


@Composable
fun MapboxSuggestionScreen(geocodingService: MapboxGeocodingService) {
    AddressAutoCompleteScreen(geocodingService = geocodingService)
}