package com.example.customerapp.presentation.userprofile

import android.Manifest
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import com.example.customerapp.BuildConfig
import com.example.customerapp.core.network.MapboxPlace
import kotlinx.coroutines.launch
import com.example.customerapp.core.network.MapboxGeocodingService
import com.example.customerapp.core.network.RetrofitClient.mapboxGeocodingService
import com.example.customerapp.data.model.User
import com.example.customerapp.presentation.auth.getCurrentLocationAndUpdateAddress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    viewModel: UserViewModel = viewModel(),
    geocodingService: MapboxGeocodingService,
    onLogout: () -> Unit,
    onAvatarClick: () -> Unit = {}
) {
    val user = viewModel.user
    val isEditing = viewModel.isEditing
    var isChangingPassword by remember { mutableStateOf(false) }
    val isLoading = viewModel.isLoading
    val errorMessage = viewModel.errorMessage
    val context = LocalContext.current

    // Get user_id from SharedPreferences
    val sharedPref = remember(context) {
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
    }
    val userId = remember {
        sharedPref.getString("user_id", null)
    }

    // Load user data once
    LaunchedEffect(userId) {
        userId?.let {
            viewModel.loadUserById(it)
        }
    }

    // Loading state
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Đang tải thông tin...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    // Error or no user state
    if (user == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage ?: "Không thể tải thông tin người dùng",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        return
    }

    // State variables
    var name by remember { mutableStateOf(user.name) }
    var address by remember { mutableStateOf(user.address) }
    var phoneNumber by remember { mutableStateOf(user.phone_number) }
    var suggestions by remember { mutableStateOf<List<MapboxPlace>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Permission launcher
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

    // Password change states
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Gradient background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Spacer(modifier = Modifier.height(60.dp))

                // Profile Header Card
                ProfileHeaderCard(
                    user = user,
                    onAvatarClick = onAvatarClick
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Profile Information Card
                ProfileInfoCard(
                    user = user,
                    isEditing = isEditing,
                    name = name,
                    onNameChange = { name = it },
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
                    phoneNumber = phoneNumber,
                    onPhoneNumberChange = { phoneNumber = it },
                    suggestions = suggestions,
                    onSuggestionClick = { place ->
                        address = place.placeName
                        suggestions = emptyList()
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
                    },
                    onEditClick = { viewModel.toggleEdit() },
                    onSaveClick = {
                        viewModel.updateUser(
                            user.copy(
                                id = userId.toString(),
                                name = name,
                                address = address,
                                phone_number = phoneNumber
                            ),
                            onError = { errorMsg ->
                                updateError = errorMsg
                            }
                        )
                    },
                    onCancelClick = {
                        viewModel.toggleEdit()
                        name = user.name
                        address = user.address
                        phoneNumber = user.phone_number
                    },
                    updateError = updateError
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons Card
                ActionButtonsCard(
                    onChangePasswordClick = { isChangingPassword = true },
                    onLogoutClick = { viewModel.logout(onLogout) }
                )

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }

    // Password Change Dialog
    if (isChangingPassword) {
        ModernPasswordChangeDialog(
            currentPassword = currentPassword,
            onCurrentPasswordChange = { currentPassword = it },
            newPassword = newPassword,
            onNewPasswordChange = { newPassword = it },
            confirmNewPassword = confirmNewPassword,
            onConfirmNewPasswordChange = { confirmNewPassword = it },
            error = passwordError,
            onDismiss = {
                isChangingPassword = false
                currentPassword = ""
                newPassword = ""
                confirmNewPassword = ""
                passwordError = null
            },
            onSave = {
                if (newPassword != confirmNewPassword) {
                    passwordError = "Mật khẩu mới không trùng khớp"
                    return@ModernPasswordChangeDialog
                }

                viewModel.changePassword(
                    user.id, currentPassword, newPassword,
                    onSuccess = {
                        isChangingPassword = false
                        currentPassword = ""
                        newPassword = ""
                        confirmNewPassword = ""
                        passwordError = null
                    },
                    onError = { err ->
                        passwordError = err
                    }
                )
            }
        )
    }
}

@Composable
private fun ProfileHeaderCard(
    user: User,
    onAvatarClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar with ring
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer ring
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Avatar
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onAvatarClick() },
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = user.avatar ?: "",
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape),
                        loading = {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    )
                }

                // Camera icon
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        )
                        .clickable { onAvatarClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Change avatar",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // User name
            Text(
                text = user.name ?: "Người dùng",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // User email
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Role chip
            AssistChip(
                onClick = { },
                label = {
                    Text(
                        text = user.role,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = when (user.role.lowercase()) {
                            "admin" -> Icons.Default.AdminPanelSettings
                            "provider" -> Icons.Default.Business
                            else -> Icons.Default.Person
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun ProfileInfoCard(
    user: User,
    isEditing: Boolean,
    name: String?,
    onNameChange: (String) -> Unit,
    address: String?,
    onAddressChange: (String) -> Unit,
    phoneNumber: String?,
    onPhoneNumberChange: (String) -> Unit,
    suggestions: List<MapboxPlace>,
    onSuggestionClick: (MapboxPlace) -> Unit,
    onLocationClick: () -> Unit,
    onEditClick: () -> Unit,
    onSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
    updateError: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thông tin cá nhân",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!isEditing) {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Chỉnh sửa",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isEditing) {
                // Edit mode
                ModernTextField(
                    value = name ?: "",
                    onValueChange = onNameChange,
                    label = "Họ và tên",
                    leadingIcon = Icons.Default.Person
                )

                Spacer(modifier = Modifier.height(16.dp))

                ModernTextField(
                    value = address ?: "",
                    onValueChange = onAddressChange,
                    label = "Địa chỉ",
                    leadingIcon = Icons.Default.LocationOn,
                    trailingIcon = {
                        IconButton(onClick = onLocationClick) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Định vị",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )

                // Address suggestions
                if (suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 150.dp)
                        ) {
                            items(suggestions) { place ->
                                Text(
                                    text = place.placeName,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSuggestionClick(place) }
                                        .padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (place != suggestions.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ModernTextField(
                    value = phoneNumber ?: "",
                    onValueChange = onPhoneNumberChange,
                    label = "Số điện thoại",
                    leadingIcon = Icons.Default.Phone,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                updateError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancelClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hủy")
                    }

                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lưu")
                    }
                }
            } else {
                // View mode
                InfoItem(
                    icon = Icons.Default.Person,
                    label = "Họ và tên",
                    value = user.name ?: "Chưa cập nhật"
                )

                InfoItem(
                    icon = Icons.Default.LocationOn,
                    label = "Địa chỉ",
                    value = user.address ?: "Chưa cập nhật"
                )

                InfoItem(
                    icon = Icons.Default.Phone,
                    label = "Số điện thoại",
                    value = user.phone_number ?: "Chưa cập nhật"
                )
            }
        }
    }
}

@Composable
private fun ActionButtonsCard(
    onChangePasswordClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Cài đặt tài khoản",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onChangePasswordClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Đổi mật khẩu",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Button(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Đăng xuất",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = trailingIcon,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun InfoItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ModernPasswordChangeDialog(
    currentPassword: String,
    onCurrentPasswordChange: (String) -> Unit,
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    confirmNewPassword: String,
    onConfirmNewPasswordChange: (String) -> Unit,
    error: String?,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Đổi mật khẩu",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = onCurrentPasswordChange,
                    label = { Text("Mật khẩu hiện tại") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = onNewPasswordChange,
                    label = { Text("Mật khẩu mới") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = confirmNewPassword,
                    onValueChange = onConfirmNewPasswordChange,
                    label = { Text("Xác nhận mật khẩu mới") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp)
                )

                error?.let {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Lưu")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Hủy")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

// Wrapper function for MainScreen usage
@Composable
fun UserProfileView(
    onAvatarClick: (() -> Unit)? = null
) {
    UserProfileScreen(
        geocodingService = mapboxGeocodingService,
        onLogout = {},
        onAvatarClick = onAvatarClick ?: {}
    )
}