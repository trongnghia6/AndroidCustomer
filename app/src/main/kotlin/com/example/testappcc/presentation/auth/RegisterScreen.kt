package com.example.testappcc.screen

import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.testappcc.BuildConfig
import com.example.testappcc.core.network.MapboxPlace
import com.example.testappcc.model.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.testappcc.core.network.MapboxGeocodingService
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    goBack: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
    geocodingService: MapboxGeocodingService
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("customer") }
    var address by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<MapboxPlace>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current


    val isLoading = viewModel.isLoading
    val authError = viewModel.authError

    // Launcher để yêu cầu quyền
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentLocationAndUpdateAddress(
                context = context,
                geocodingService = geocodingService,
                accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN,
                onAddressFound = { address = it },
                onError = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(it)
                    }
                }
            )
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Quyền vị trí bị từ chối")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Đăng nhập",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Tên người dùng (tùy chọn)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mật khẩu") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = address,
            onValueChange = {
                address = it
                coroutineScope.launch {
                    if (it.isNotEmpty()) {
                        try {
                            val result = geocodingService.searchPlaces(
                                query = it,
                                accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
                            )
                            suggestions = result.features
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Lỗi khi tìm kiếm địa chỉ: ${e.message}")
                        }
                    } else {
                        suggestions = emptyList()
                    }
                }
            },
            label = { Text("Địa chỉ") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED) {
                        getCurrentLocationAndUpdateAddress(
                            context = context,
                            geocodingService = geocodingService,
                            accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN,
                            onAddressFound = { address = it },
                            onError = {
                                coroutineScope.launch { snackbarHostState.showSnackbar(it) }
                            }
                        )
                    } else {
                        permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Tự định vị")
                }
            }
        )
        LazyColumn {
            items(suggestions) { place ->
                Text(
                    text = place.placeName ?: "Unknown",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            address = place.placeName ?: ""
                            suggestions = emptyList()
                        }
                        .padding(8.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("customer", "provider", "admin").forEach { roleOption ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = role == roleOption,
                        onClick = { role = roleOption }
                    )
                    Text(roleOption.replaceFirstChar { it.uppercase() })
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        authError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                goBack()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Quay lại")
        }

        Spacer(modifier = Modifier.height(8.dp))

        var isSigningUp by remember { mutableStateOf(false) }

        TextButton(
            onClick = {
                if (!isSigningUp) {
                    isSigningUp = true
                    viewModel.clearError()
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        viewModel.signUp(email, password, address) {
                            isSigningUp = false
                            onRegisterSuccess()
                        }
                    } else {
                        viewModel.authError = "Vui lòng nhập email và mật khẩu"
                        isSigningUp = false
                    }
                }
            },
            enabled = !isLoading && !isSigningUp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Đăng ký")
        }
    }
}
fun getCurrentLocationAndUpdateAddress(
    context: Context,
    geocodingService: MapboxGeocodingService,
    accessToken: String,
    onAddressFound: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        onError("Quyền vị trí chưa được cấp.")
        Log.d("location","Quyền vị trí chưa được cấp.")
        return
    }

    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1000L // 1 giây cập nhật
    ).build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            if (location != null) {
                // Xử lý vị trí như bình thường
                // Dừng cập nhật sau khi lấy được vị trí
                fusedLocationClient.removeLocationUpdates(this)

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = geocodingService.reverseGeocode(
                            longitude = location.longitude,
                            latitude = location.latitude,
                            accessToken = accessToken
                        )
                        val placeName = response.features.firstOrNull()?.placeName
                        withContext(Dispatchers.Main) {
                            if (placeName != null) {
                                onAddressFound(placeName)
                                Log.d("location", placeName)
                            } else {
                                onError("Không tìm thấy địa chỉ.")
                                Log.d("location", "Không tìm thấy địa chỉ.")
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onError("Lỗi reverse geocoding: ${e.message}")
                            Log.d("location", "Lỗi reverse geocoding: ${e.message}")
                        }
                    }
                }
            } else {
                onError("Không thể lấy vị trí hiện tại.")
                Log.d("location", "Không thể lấy vị trí hiện tại.")
            }
        }
    }

    fusedLocationClient.requestLocationUpdates(
        locationRequest,
        locationCallback,
        Looper.getMainLooper()
    )
}

