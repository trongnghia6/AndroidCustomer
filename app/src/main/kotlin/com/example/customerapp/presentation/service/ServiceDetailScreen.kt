package com.example.customerapp.presentation.service

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.MailOutline
import androidx.compose.material.icons.sharp.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.customerapp.BuildConfig
import com.example.customerapp.core.network.RetrofitClient
import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.ProviderService
import com.example.customerapp.data.model.Review
import com.example.customerapp.data.repository.ProviderServiceRepository
import com.example.customerapp.presentation.viewmodel.ServiceDetailViewModel
import com.google.android.gms.location.LocationServices
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale


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
    var ratingsMap by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    val defaultAvatar = "https://ui-avatars.com/api/?name=User&background=random"

    // Lấy vị trí người dùng
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

    // Lấy rating trung bình cho mỗi provider_service_id
    LaunchedEffect(viewModel.providers) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ratings = supabase.from("service_ratings").select().decodeList<Review>()
                val ratingsByProvider = ratings.groupBy { it.providerServiceId.toString() }
                ratingsMap = ratingsByProvider.mapValues { entry ->
                    val avg = entry.value.map { it.rating }.average().toFloat()
                    if (avg.isNaN()) 0f else avg
                }
            } catch (e: Exception) {
                Log.e("ServiceDetailScreen", "Error loading ratings: ${e.message}")
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
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, result)
        return result[0]
    }

    // State cho filter
    var selectedFilter by remember { mutableStateOf("Tất cả") }

    // Tính toán khoảng cách cho tất cả providers khi có userLocation
    val providerServicesWithCalculatedDistance by produceState<List<Pair<ProviderService, Float>>>(
        initialValue = emptyList(),
        key1 = viewModel.providers,
        key2 = userLatLng
    ) {
        if (userLatLng == null) {
            value = viewModel.providers.map { it to Float.MAX_VALUE }
        } else {
            val listWithDistance = viewModel.providers.map { providerService ->
                val address = providerService.user?.address
                var distance = Float.MAX_VALUE
                if (!address.isNullOrBlank()) {
                    val latLng = withContext(Dispatchers.IO) { getLatLngFromAddress(address) }
                    if (latLng != null) {
                        distance = calculateDistance(
                            userLatLng!!.first, userLatLng!!.second,
                            latLng.first, latLng.second
                        )
                    }
                }
                providerService to distance
            }
            value = listWithDistance
        }
    }

    // Áp dụng filter/sắp xếp
    val filteredProviders = remember(selectedFilter, providerServicesWithCalculatedDistance, ratingsMap) {
        when (selectedFilter) {
            "Giá tiền" -> providerServicesWithCalculatedDistance.sortedBy { it.first.customPrice?.toFloat() ?: Float.MAX_VALUE }
            "Gần tôi" -> providerServicesWithCalculatedDistance.sortedBy { it.second }
            "Đánh giá" -> providerServicesWithCalculatedDistance.sortedByDescending {
                ratingsMap[it.first.id.toString()] ?: 0f
            }
            else -> providerServicesWithCalculatedDistance
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = serviceName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                        )
                    )
                )
        ) {
            // Service Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = serviceName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = serviceDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Thời gian: $durationMinutes phút",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

// Filter Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Tất cả", "Giá tiền", "Gần tôi", "Đánh giá").forEachIndexed { index, filter ->
                    Button(
                        onClick = { selectedFilter = filter },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedFilter == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedFilter == filter) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp), // Giảm padding ngang
                        modifier = Modifier.weight(0.22f) // Giảm weight để tạo khoảng cách
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            if (selectedFilter == "Gần tôi" && isGettingLocation) {
                Text(
                    text = "Đang lấy vị trí hiện tại...",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (selectedFilter == "Gần tôi" && locationError != null) {
                Text(
                    text = locationError!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Providers List
            Text(
                text = "Danh sách nhà cung cấp",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                ),
                modifier = Modifier.padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurface
            )

            when {
                viewModel.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                viewModel.error != null -> {
                    Text(
                        text = "Lỗi: ${viewModel.error}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                viewModel.providers.isEmpty() -> {
                    Text(
                        text = "Không có nhà cung cấp nào cho dịch vụ này",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            val averageRating = ratingsMap[providerService.id.toString()] ?: 0f
                            val ratingText = if (averageRating > 0) String.format("%.1f/5", averageRating) else "Chưa có đánh giá"

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        navController.navigate("provider_detail/${providerService.id}")
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = name,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 18.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Sharp.Star,
                                                contentDescription = null,
                                                tint = Color(0xFFFFC107),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = ratingText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFFFA000),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = priceStr,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    color = Color(0xFFE53935),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                            if (distanceStr != null) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "• $distanceStr",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Button(
                                        onClick = {
                                            CoroutineScope(Dispatchers.Main).launch {
                                                val geoService = RetrofitClient.mapboxGeocodingService
                                                val currentAddress = if (userLatLng != null) {
                                                    val response = geoService.reverseGeocode(
                                                        longitude = userLatLng!!.second,
                                                        latitude = userLatLng!!.first,
                                                        accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
                                                    )
                                                    response.features.firstOrNull()?.placeName ?: "Không rõ địa chỉ"
                                                } else {
                                                    "Không rõ địa chỉ"
                                                }
                                                navController.navigate(
                                                    "order_confirm?address=$currentAddress&price=$price&provider_service_id=${providerService.id}&durationMinutes=${providerService.durationMinutes}"
                                                )
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text(
                                            text = "Thuê ngay",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
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
@Composable
fun ProviderDetailScreen(
    providerServiceId: String,
    navController: NavController,
    defaultAvatar: String
) {
    val providerRepo = remember { ProviderServiceRepository() }
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var providerService by remember { mutableStateOf<ProviderService?>(null) }
    var userLatLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var ratings by remember { mutableStateOf<List<Review>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Lấy vị trí người dùng
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        userLatLng = Pair(location.latitude, location.longitude)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProviderDetailScreen", "Failed to get location: ${e.message}")
                }
        } else {
            Log.w("ProviderDetailScreen", "Location permission not granted")
            // Gợi ý: có thể hiện dialog yêu cầu quyền nếu cần
        }
    }

    // Lấy dữ liệu nhà cung cấp và đánh giá
    LaunchedEffect(providerServiceId) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                providerService = providerRepo.getProviderServiceById(providerServiceId.toInt())
                val ratingResult = supabase.from("service_ratings").select {
                    filter { eq("provider_service_id", providerServiceId) }
                }.decodeList<Review>()
                ratings = ratingResult
                isLoading = false
            } catch (e: Exception) {
                error = e.message
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    )
                )
            )
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            error != null -> {
                Text(
                    text = "Lỗi: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            providerService != null -> {
                val user = providerService!!.user
                val avatarUrl = user?.avatar ?: defaultAvatar
                val name = user?.name ?: "Không rõ tên"
                val description = providerService!!.customDescription ?: "Không có mô tả"
                val price = providerService!!.customPrice ?: 0.0
                val priceStr = String.format(Locale("vi", "VN"), "%,.0f₫", price)
                val averageRating = if (ratings.isNotEmpty()) {
                    String.format(Locale("vi", "VN"), "%.1f/5", ratings.map { it.rating }.average())
                } else {
                    "Chưa có đánh giá"
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = name,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 24.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Sharp.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = averageRating,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFFFFA000),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                navController.navigate("chat/${user?.id}")
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = Icons.Sharp.MailOutline,
                                contentDescription = "Nhắn tin",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Thông tin chi tiết
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Thông tin dịch vụ",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Giá: $priceStr",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color(0xFFE53935),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Thời gian: ${providerService!!.durationMinutes} phút",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Danh sách đánh giá
                    Text(
                        text = "Đánh giá từ khách hàng",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (ratings.isEmpty()) {
                        Text(
                            text = "Chưa có đánh giá nào",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        ) {
                            items(ratings) { rating ->
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(animationSpec = tween(300)),
                                    exit = fadeOut(animationSpec = tween(300))
                                ) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                (1..5).forEach { star ->
                                                    Icon(
                                                        imageVector = Icons.Sharp.Star,
                                                        contentDescription = null,
                                                        tint = if (star <= rating.rating) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                rating.createdAt?.let {
                                                    val formattedTime = try {
                                                        val instant = OffsetDateTime.parse(it)
                                                        instant.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                                                    } catch (e: Exception) {
                                                        it
                                                    }
                                                    Text(
                                                        text = formattedTime,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (rating.comment != null) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = rating.comment,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            CoroutineScope(Dispatchers.Main).launch {
                                val geoService = RetrofitClient.mapboxGeocodingService
                                val currentAddress = userLatLng?.let { latLng ->
                                    val response = geoService.reverseGeocode(
                                        longitude = latLng.second,
                                        latitude = latLng.first,
                                        accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
                                    )
                                    response.features.firstOrNull()?.placeName ?: "Không rõ địa chỉ"
                                } ?: "Không rõ địa chỉ"
                                navController.navigate(
                                    "order_confirm?address=${URLEncoder.encode(currentAddress, "UTF-8")}&price=$price&provider_service_id=$providerServiceId&durationMinutes=${providerService!!.durationMinutes}"
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Thuê ngay",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}