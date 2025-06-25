package com.example.testappcc.presentation.auth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.testappcc.BuildConfig
import com.example.testappcc.core.network.MapboxPlace
import com.example.testappcc.model.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
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
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<MapboxPlace>>(emptyList()) }
    var passwordVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val isValid = phoneNumber.matches(Regex("^0[0-9]{9}$"))
    val isValidEmail = email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"))
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Header Section
            item {
                RegisterHeader()
            }

            // Personal Info Section
            item {
                PersonalInfoSection(
                    name = name,
                    onNameChange = { name = it },
                    email = email,
                    onEmailChange = { email = it },
                    isValidEmail = isValidEmail,
                    password = password,
                    onPasswordChange = { password = it },
                    passwordVisible = passwordVisible,
                    onPasswordVisibilityChange = { passwordVisible = !passwordVisible }
                )
            }

            // Contact Info Section
            item {
                ContactInfoSection(
                    phoneNumber = phoneNumber,
                    onPhoneChange = { phoneNumber = it },
                    isValidPhone = isValid,
                    address = address,
                    onAddressChange = { newAddress ->
                        address = newAddress
                        coroutineScope.launch {
                            if (newAddress.isNotEmpty()) {
                                try {
                                    val result = geocodingService.searchPlaces(
                                        query = newAddress,
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
                    onLocationClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
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
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    }
                )
            }

            // Address Suggestions
            if (suggestions.isNotEmpty()) {
                item {
                    AddressSuggestionsSection(
                        suggestions = suggestions,
                        onSuggestionClick = { suggestion ->
                            address = suggestion.placeName
                            suggestions = emptyList()
                        }
                    )
                }
            }

            // Error Message
            authError?.let { error ->
                item {
                    ErrorMessage(error)
                }
            }

            // Action Buttons
            item {
                ActionButtonsSection(
                    isLoading = isLoading,
                    isFormValid = name.isNotEmpty() && isValidEmail && password.isNotEmpty() && isValid && address.isNotEmpty(),
                    onRegister = {
                        if (email.isNotEmpty() && password.isNotEmpty()) {
                            viewModel.clearError()
                            coroutineScope.launch {
                                viewModel.signUp(
                                    email = email,
                                    password = password,
                                    address = address,
                                    name = name,
                                    phoneNumber = phoneNumber,
                                    onSuccess = {
                                        onRegisterSuccess()
                                    }
                                )
                            }
                        } else {
                            viewModel.authError = "Vui lòng nhập email và mật khẩu"
                        }
                    },
                    onGoBack = goBack
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun RegisterHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Icon/Logo
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Register",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tạo tài khoản mới",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Điền thông tin để tạo tài khoản của bạn",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PersonalInfoSection(
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    isValidEmail: Boolean,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityChange: () -> Unit
) {
    FormCard(
        title = "Thông tin cá nhân",
        icon = Icons.Default.Person,
        description = "Nhập thông tin cơ bản của bạn"
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CustomTextField(
                value = name,
                onValueChange = onNameChange,
                label = "Tên người dùng",
                icon = Icons.Default.Person,
                placeholder = "Nhập tên của bạn"
            )

            CustomTextField(
                value = email,
                onValueChange = onEmailChange,
                label = "Email",
                icon = Icons.Default.Email,
                placeholder = "example@email.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = email.isNotEmpty() && !isValidEmail,
                supportingText = if (email.isNotEmpty() && !isValidEmail) "Email không hợp lệ" else null
            )

            CustomTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "Mật khẩu",
                icon = Icons.Default.Lock,
                placeholder = "Nhập mật khẩu",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onPasswordVisibilityChange) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Close else Icons.Default.Info,
                            contentDescription = if (passwordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ContactInfoSection(
    phoneNumber: String,
    onPhoneChange: (String) -> Unit,
    isValidPhone: Boolean,
    address: String,
    onAddressChange: (String) -> Unit,
    onLocationClick: () -> Unit
) {
    FormCard(
        title = "Thông tin liên hệ",
        icon = Icons.Default.Phone,
        description = "Nhập số điện thoại và địa chỉ của bạn"
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CustomTextField(
                value = phoneNumber,
                onValueChange = onPhoneChange,
                label = "Số điện thoại",
                icon = Icons.Default.Phone,
                placeholder = "0123456789",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = phoneNumber.isNotEmpty() && !isValidPhone,
                supportingText = if (phoneNumber.isNotEmpty() && !isValidPhone) "Số điện thoại không hợp lệ" else null
            )

            CustomTextField(
                value = address,
                onValueChange = onAddressChange,
                label = "Địa chỉ",
                icon = Icons.Default.Home,
                placeholder = "Nhập địa chỉ của bạn",
                trailingIcon = {
                    IconButton(onClick = onLocationClick) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Tự định vị",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun AddressSuggestionsSection(
    suggestions: List<MapboxPlace>,
    onSuggestionClick: (MapboxPlace) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Suggestions",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gợi ý địa chỉ",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            suggestions.take(5).forEach { place ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(place) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = place.placeName,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ActionButtonsSection(
    isLoading: Boolean,
    isFormValid: Boolean,
    onRegister: () -> Unit,
    onGoBack: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onRegister,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading && isFormValid,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Đang đăng ký...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Register",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Đăng ký",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        OutlinedButton(
            onClick = onGoBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Quay lại",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (!isFormValid) {
            Text(
                text = "Vui lòng điền đầy đủ thông tin hợp lệ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FormCard(
    title: String,
    icon: ImageVector,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                errorBorderColor = MaterialTheme.colorScheme.error
            )
        )

        supportingText?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun ErrorMessage(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// Keep the existing getCurrentLocationAndUpdateAddress function unchanged
fun getCurrentLocationAndUpdateAddress(
    context: Context,
    geocodingService: MapboxGeocodingService,
    accessToken: String,
    onAddressFound: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        onError("Quyền vị trí chưa được cấp.")
        Log.d("location","Quyền vị trí chưa được cấp.")
        return
    }

    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1000L
    ).build()

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            if (location != null) {
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
