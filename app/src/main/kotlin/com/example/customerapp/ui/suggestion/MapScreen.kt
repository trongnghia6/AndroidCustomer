package com.example.customerapp.ui.suggestion


import androidx.compose.runtime.*
import com.example.customerapp.core.network.MapboxGeocodingService
import com.example.customerapp.data.model.AddressAutoCompleteScreen


@Composable
fun MapboxSuggestionScreen(geocodingService: MapboxGeocodingService) {
    AddressAutoCompleteScreen(geocodingService = geocodingService)
}