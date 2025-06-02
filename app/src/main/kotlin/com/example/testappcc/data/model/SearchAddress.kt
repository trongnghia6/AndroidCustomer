package com.example.testappcc.data.model

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.testappcc.BuildConfig
import com.example.testappcc.core.network.MapboxGeocodingService
import com.example.testappcc.core.network.MapboxPlace
import com.example.testappcc.presentation.search.AddressAutoComplete


@Composable
fun AddressAutoCompleteScreen(geocodingService: MapboxGeocodingService) {
    val coroutineScope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var places by remember { mutableStateOf(emptyList<MapboxPlace>()) }
    var selectedAddress by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Gọi API khi query thay đổi
    LaunchedEffect(query) {
        if (query.isNotEmpty()) {
            delay(300)
            coroutineScope.launch {
                try {
                    val response = geocodingService.searchPlaces(
                        query,
                        BuildConfig.MAPBOX_ACCESS_TOKEN
                    )
                    places = response.features
                    if (response.features.isEmpty()) {
                        snackbarHostState.showSnackbar("Không tìm thấy kết quả.")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Lỗi tìm kiếm: ${e.message}")
                }
            }
        } else {
            places = emptyList()
        }
    }

    val state = MapboxSearchState(
        query = query,
        onQueryChange = { newQuery ->
            query = newQuery
            selectedAddress = null // reset nếu người dùng đang gõ mới
        },
        places = places,
        selectedAddress = selectedAddress,
        onAddressSelected = { address ->
            selectedAddress = address
            query = address // ✅ Tự động điền TextField
            places = emptyList() // Ẩn gợi ý
        },
        snackbarHostState = snackbarHostState
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        AddressAutoComplete(
            state = state,
            modifier = Modifier.padding(padding).padding(16.dp)
        )
    }
}


data class MapboxSearchState(
    val query: String,
    val onQueryChange: (String) -> Unit,
    val places: List<MapboxPlace>,
    val selectedAddress: String?,
    val onAddressSelected: (String) -> Unit,
    val snackbarHostState: SnackbarHostState
)
