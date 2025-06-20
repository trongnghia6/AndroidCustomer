package com.example.testappcc.presentation.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.testappcc.presentation.viewmodel.ServiceDetailViewModel
import com.example.testappcc.data.model.User
import com.example.testappcc.data.model.ProviderService
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.testappcc.core.network.MapboxGeocodingService
import com.example.testappcc.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.testappcc.core.network.RetrofitClient
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun ServiceDetailScreen(
    serviceId: Int,
    serviceName: String,
    serviceDescription: String,
    durationMinutes: Int,
    navController: NavController,
    viewModel: ServiceDetailViewModel = viewModel()
) {
    LaunchedEffect(serviceId) {
        viewModel.loadProviders(serviceId)
    }

    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var userLatLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var isGettingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    // Lấy lat/lng hiện tại khi màn hình load hoặc danh sách providers thay đổi
    LaunchedEffect(key1 = viewModel.providers, key2 = context) {
        if (userLatLng == null && !isGettingLocation) {
            isGettingLocation = true
            locationError = null
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    userLatLng = Pair(location.latitude, location.longitude)
                }
                isGettingLocation = false
            }.addOnFailureListener { e ->
                locationError = "Không lấy được vị trí: ${e.message}"
                isGettingLocation = false
            }
        }
    }

    // Hàm lấy lat/lng từ địa chỉ provider
    suspend fun getLatLngFromAddress(address: String): Pair<Double, Double>? {
        return try {
            val geoService = RetrofitClient.mapboxGeocodingService
            val response = geoService.searchPlaces(address, BuildConfig.MAPBOX_ACCESS_TOKEN)
            val first = response.features.firstOrNull()
            val center = first?.center
            if (center != null && center.size >= 2) Pair(center[1], center[0]) else null
        } catch (e: Exception) {
            Log.e("ServiceDetailScreen", "Geocoding error for address: $address", e)
            null
        }
    }

    // Hàm tính khoảng cách giữa hai điểm
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, result)
        return result[0]
    }

    // State cho filter
    var selectedFilter by remember { mutableStateOf("Tất cả") }

    val defaultAvatar = "https://ui-avatars.com/api/?name=User&background=random"

    // Tính toán khoảng cách cho tất cả providers khi có userLocation
    val providerServicesWithCalculatedDistance by produceState<List<Pair<ProviderService, Float>>>(
        initialValue = emptyList(),
        key1 = viewModel.providers, // Trigger khi providers thay đổi
        key2 = userLatLng          // Trigger khi userLatLng thay đổi
    ) {
        if (userLatLng == null) {
            // Nếu vị trí user chưa có, hiển thị providers với khoảng cách max (không biết)
            value = viewModel.providers.map { it to Float.MAX_VALUE }
        } else {
            // Nếu có vị trí user, tính khoảng cách cho tất cả providers
            val geoService = RetrofitClient.mapboxGeocodingService
            val listWithDistance = viewModel.providers.map { providerService ->
                val address = providerService.user?.address
                var distance = Float.MAX_VALUE // Mặc định khoảng cách max nếu không geocode được
                if (!address.isNullOrBlank()) {
                    // Geocode địa chỉ provider bất đồng bộ
                    val latLng = withContext(Dispatchers.IO) { getLatLngFromAddress(address) }
                    if (latLng != null) {
                        distance = calculateDistance(
                            userLatLng!!.first, userLatLng!!.second,
                            latLng.first, latLng.second
                        )
                    } else {
                        Log.w("ServiceDetailScreen", "Could not geocode address for provider: ${providerService.user?.name}")
                    }
                } else {
                    Log.w("ServiceDetailScreen", "Provider address is null or blank for: ${providerService.user?.name}")
                }
                providerService to distance
            }
            value = listWithDistance
        }
    }

    // Áp dụng filter/sắp xếp trên danh sách đã có khoảng cách
    val filteredProviders = remember(selectedFilter, providerServicesWithCalculatedDistance) {
        when (selectedFilter) {
            "Giá tiền" -> providerServicesWithCalculatedDistance.sortedBy { it.first.customPrice?.toFloat() ?: Float.MAX_VALUE }
            "Gần tôi" -> providerServicesWithCalculatedDistance.sortedBy { it.second }
            "Đánh giá" -> providerServicesWithCalculatedDistance // Chưa có logic đánh giá
            else -> providerServicesWithCalculatedDistance // Mặc định: giữ nguyên thứ tự hoặc theo API trả về
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(serviceName) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Service Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = serviceName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = serviceDescription,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Thời gian: $durationMinutes phút",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // UI filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { selectedFilter = "Tất cả" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedFilter == "Tất cả") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) { Text("Tất cả") }
                Button(
                    onClick = { selectedFilter = "Giá tiền" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedFilter == "Giá tiền") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) { Text("Giá tiền") }
                Button(
                    onClick = { selectedFilter = "Gần tôi" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedFilter == "Gần tôi") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) { Text("Gần tôi") }
                Button(
                    onClick = { selectedFilter = "Đánh giá" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedFilter == "Đánh giá") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) { Text("Đánh giá") }
            }
            if (selectedFilter == "Gần tôi" && isGettingLocation) {
                Text("Đang lấy vị trí hiện tại...", color = MaterialTheme.colorScheme.primary)
            }
            if (selectedFilter == "Gần tôi" && locationError != null) {
                Text(locationError!!, color = MaterialTheme.colorScheme.error)
            }

            // Providers List
            Text(
                text = "Danh sách nhà cung cấp",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when {
                viewModel.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                viewModel.error != null -> {
                    Text(
                        text = "Lỗi: ${viewModel.error}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                viewModel.providers.isEmpty() -> {
                    Text(
                        text = "Không có nhà cung cấp nào cho dịch vụ này",
                        modifier = Modifier.padding(16.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredProviders) { (providerService, distance) ->
                            val user = providerService.user
                            val avatarUrl = user?.avatar ?: defaultAvatar
                            val name = user?.name ?: "Không rõ tên"
                            val description = providerService.customDescription ?: "Không có mô tả"
                            val price = providerService.customPrice ?: 0.0
                            val priceStr = String.format("%,.0f₫", price)
                            val distanceStr = if (distance != Float.MAX_VALUE) String.format("%.0f m", distance) else null

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = name,
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Đánh giá: 5/5",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFFFA000),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = priceStr,
                                                style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                                            )
                                            if (distanceStr != null) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "• $distanceStr",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            // Lấy địa chỉ hiện tại từ userLatLng (nếu có)
                                            if (userLatLng != null) {
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    val geoService = RetrofitClient.mapboxGeocodingService
                                                    val response = geoService.reverseGeocode(
                                                        longitude = userLatLng!!.second,
                                                        latitude = userLatLng!!.first,
                                                        accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
                                                    )
                                                    val currentAddress = response.features.firstOrNull()?.placeName ?: "Không rõ địa chỉ"
                                                    navController.navigate(
                                                        "order_confirm?address=${currentAddress}&price=${price}&provider_service_id=${providerService.id}&durationMinutes=${providerService.durationMinutes}"
                                                    )
                                                }
                                            } else {
                                                navController.navigate(
                                                    "order_confirm?address=Không rõ địa chỉ&price=${price}&provider_service_id=${providerService.id}&durationMinutes=${providerService.durationMinutes}"
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Thuê ngay", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

