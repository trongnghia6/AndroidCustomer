package com.example.testappcc.presentation.userprofile

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.example.testappcc.BuildConfig
import com.example.testappcc.core.network.MapboxPlace
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.LocationOn
import androidx.core.content.ContextCompat
import com.example.testappcc.core.network.MapboxGeocodingService
import com.example.testappcc.presentation.auth.getCurrentLocationAndUpdateAddress


@Composable
fun UserProfileScreen(
    viewModel: UserViewModel = viewModel(),
    geocodingService: MapboxGeocodingService,
    onLogout: () -> Unit,
    navController: NavController
) {
    val user = viewModel.user
    val isEditing = viewModel.isEditing
    var isChangingPassword by remember { mutableStateOf(false) }
    val isLoading = viewModel.isLoading
    val errorMessage = viewModel.errorMessage
    val context = LocalContext.current

    // Lấy user_id từ SharedPreferences
    val sharedPref = remember(context) {
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
    }
    val userId = remember {
        sharedPref.getString("user_id", null)
    }

    // Gọi loadUserById một lần duy nhất
    LaunchedEffect(userId) {
        userId?.let {
            viewModel.loadUserById(it)
        }
    }

    if (isLoading) {
        // Hiển thị loading
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Đang tải thông tin người dùng...")
        }
        return
    }

    if (user == null) {
        // Chưa có user, chưa load hoặc chưa có dữ liệu
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Chưa có dữ liệu người dùng")
        }
        return
    }

    var name by remember { mutableStateOf(user.name) }
    var email by remember { mutableStateOf(user.email) }
    var role by remember { mutableStateOf(user.role) }
    var address by remember { mutableStateOf(user.address) }
    var phoneNumber by remember { mutableStateOf(user.phone_number) }

    var suggestions by remember { mutableStateOf<List<MapboxPlace>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
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

    // Các trạng thái mật khẩu đổi mật khẩu
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }




    if (errorMessage != null) {
        // Hiển thị lỗi
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Lỗi: $errorMessage")
        }
        return
    }



    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Thông tin người dùng",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Email - luôn đọc
                    Text(
                        text = "Email",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Role - luôn đọc
                    Text(
                        text = "Role",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = role,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (isEditing) {
                        OutlinedTextField(
                            value = name ?: "",
                            onValueChange = { name = it },
                            label = { Text("Tên") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = address ?: "",
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
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val maxSuggestionsHeight = 150.dp

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxSuggestionsHeight)
                        ) {
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
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = phoneNumber ?: "",
                            onValueChange = { phoneNumber = it },
                            label = { Text("Số điện thoại") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.toggleEdit()
                                    // Reset lại các biến về user cũ
                                    name = user.name
                                    address = user.address
                                    phoneNumber = user.phone_number
                                }
                            ) {
                                Text("Hủy")
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Button(
                                onClick = {
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
                                }
                            ) {
                                Text("Lưu")
                            }
                            updateError?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    } else {
                        InfoRow(label = "Tên", value = user.name ?: "Chưa cập nhật")
                        InfoRow(label = "Địa chỉ", value = user.address ?: "Chưa cập nhật")
                        InfoRow(label = "Số điện thoại", value = user.phone_number ?: "Chưa cập nhật")

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { viewModel.toggleEdit() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Chỉnh sửa")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Nút thay đổi ảnh đại diện
                        Button(
                            onClick = { navController.navigate("avatar") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Thay đổi ảnh đại diện")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Nút đổi mật khẩu
                        Button(
                            onClick = { isChangingPassword = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Đổi mật khẩu")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = { viewModel.logout(onLogout) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Đăng xuất", color = MaterialTheme.colorScheme.onError)
            }

            // Box đổi mật khẩu hiện lên khi isChangingPassword = true
            if (isChangingPassword) {
                PasswordChangeDialog(
                    currentPassword = currentPassword,
                    onCurrentPasswordChange = { currentPassword = it },
                    newPassword = newPassword,
                    onNewPasswordChange = { newPassword = it },
                    confirmNewPassword = confirmNewPassword,
                    onConfirmNewPasswordChange = { confirmNewPassword = it },
                    error = passwordError,
                    onDismiss = {
                        isChangingPassword = false
                        // Reset các trường nhập mật khẩu
                        currentPassword = ""
                        newPassword = ""
                        confirmNewPassword = ""
                        passwordError = null
                    },
                    onSave = {
                        // Kiểm tra mật khẩu mới trùng nhau
                        if (newPassword != confirmNewPassword) {
                            passwordError = "Mật khẩu mới không trùng khớp"
                            return@PasswordChangeDialog
                        }

                        // Gọi viewModel update mật khẩu (bạn cần triển khai)
                        viewModel.changePassword( user.id,currentPassword, newPassword,
                            onSuccess = {
                                isChangingPassword = false
                                // Reset các trường
                                currentPassword = ""
                                newPassword = ""
                                confirmNewPassword = ""
                                passwordError = null
                            },
                            onError = { err ->
                                passwordError = err
                            })
                    }
                )
            }
        }
    }
}

@Composable
fun PasswordChangeDialog(
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
        title = { Text("Đổi mật khẩu") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = onCurrentPasswordChange,
                    label = { Text("Mật khẩu hiện tại") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = onNewPasswordChange,
                    label = { Text("Mật khẩu mới") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmNewPassword,
                    onValueChange = onConfirmNewPasswordChange,
                    label = { Text("Nhập lại mật khẩu mới") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Lưu")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
