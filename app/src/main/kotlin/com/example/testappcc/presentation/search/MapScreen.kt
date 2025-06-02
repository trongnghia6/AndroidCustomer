package com.example.testappcc.presentation.search


import androidx.compose.runtime.*
import com.example.testappcc.core.network.MapboxGeocodingService
import com.example.testappcc.data.model.AddressAutoCompleteScreen


@Composable
fun MapboxSuggestionScreen(geocodingService: MapboxGeocodingService) {
    AddressAutoCompleteScreen(geocodingService = geocodingService)
}