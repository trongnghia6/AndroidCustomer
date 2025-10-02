package com.example.customerapp.ui.profile

import android.Manifest
//noinspection SuspiciousImport
import android.R
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlin.let

// Function to create a temporary file for camera
private fun createImageFile(context: Context): File {
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir: File = File(context.getExternalFilesDir(null), "Pictures")
    if (!storageDir.exists()) {
        storageDir.mkdirs()
    }
    return File(storageDir, "JPEG_${timeStamp}_.jpg")
}

// Function to get URI from file using FileCustomer
private fun getUriForFile(context: Context, file: File): Uri {
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarChangeScreen(
    onBackClick: () -> Unit,
    avatarViewModel: AvatarViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedAvatarType by remember { mutableStateOf<AvatarType>(AvatarType.Current) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    
    // Image picker launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { avatarViewModel.uploadAvatar(context, it) }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            avatarViewModel.uploadAvatar(context, tempCameraUri!!)
        }
    }
    
    // Permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val imageFile = createImageFile(context)
                val uri = getUriForFile(context, imageFile)
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                galleryLauncher.launch("image/*")
            }
        } else {
            // Permission denied, fallback to gallery
            galleryLauncher.launch("image/*")
        }
    }
    
    // Load user data when screen first opens
    LaunchedEffect(Unit) {
        avatarViewModel.loadCurrentUser(context)
    }
    
    // Show error messages
    avatarViewModel.error?.let { error ->
        LaunchedEffect(error) {
            // You can show a snackbar here
            // For now just clear the error after showing
            avatarViewModel.clearError()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar
        TopAppBar(
            title = { Text("Thay đổi avatar") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại"
                    )
                }
            },
            actions = {
                TextButton(
                    onClick = {
                        when (selectedAvatarType) {
                            is AvatarType.Predefined -> {
                                // For now, predefined avatars are just emojis, 
                                // you can implement actual predefined avatar URLs later
                            }
                            AvatarType.Remove -> {
                                avatarViewModel.removeAvatar(context)
                            }
                            else -> {
                                // Current avatar, no change needed
                            }
                        }
                        onBackClick()
                    },
                    enabled = !avatarViewModel.isUploading
                ) {
                    Text("Lưu")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Current Avatar Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        when {
                            avatarViewModel.isUploading -> {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    CircularProgressIndicator(
                                        progress = avatarViewModel.uploadProgress,
                                        modifier = Modifier.size(100.dp),
                                        strokeWidth = 4.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${(avatarViewModel.uploadProgress * 100).toInt()}%",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            selectedAvatarType == AvatarType.Remove -> {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "?",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp
                                    )
                                }
                            }

                            selectedAvatarType is AvatarType.Predefined -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            (selectedAvatarType as AvatarType.Predefined).avatar.backgroundColor,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (selectedAvatarType as AvatarType.Predefined).avatar.emoji,
                                        fontSize = 48.sp
                                    )
                                }
                            }

                            avatarViewModel.currentUser?.avatar != null -> {
                                AsyncImage(
                                    model = avatarViewModel.currentUser?.avatar,
                                    contentDescription = "Current Avatar",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(R.drawable.ic_menu_gallery),
                                    placeholder = painterResource(R.drawable.ic_menu_gallery)
                                )
                            }
                            
                            else -> {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "?",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Upload Options
            Text(
                text = "Chọn cách tải ảnh",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Camera Option
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { 
                            // Check camera permission
                            when (PackageManager.PERMISSION_GRANTED) {
                                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                                    // Permission granted, launch camera
                                    try {
                                        val imageFile = createImageFile(context)
                                        val uri = getUriForFile(context, imageFile)
                                        tempCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    } catch (e: Exception) {
                                        galleryLauncher.launch("image/*")
                                    }
                                }
                                else -> {
                                    // Request permission
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Chụp ảnh",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Gallery Option
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { 
                            galleryLauncher.launch("image/*")
                        },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Photo,
                            contentDescription = "Gallery",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Thư viện",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Predefined Avatars
            Text(
                text = "Hoặc chọn avatar có sẵn",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(getDefaultAvatars()) { avatar ->
                    PredefinedAvatarItem(
                        avatar = avatar,
                        isSelected = selectedAvatarType is AvatarType.Predefined && 
                                   (selectedAvatarType as AvatarType.Predefined).avatar.id == avatar.id,
                        onClick = { selectedAvatarType = AvatarType.Predefined(avatar) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Remove Avatar Option
            OutlinedButton(
                onClick = { selectedAvatarType = AvatarType.Remove },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Xóa avatar")
            }
        }
    }
}

@Composable
fun PredefinedAvatarItem(
    avatar: DefaultAvatar,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = avatar.backgroundColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = avatar.emoji,
                    fontSize = 24.sp
                )
            }
        }

        if (isSelected) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                shape = CircleShape,
                color = Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Đã chọn",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

sealed class AvatarType {
    object Current : AvatarType()
    object Remove : AvatarType()
    data class Predefined(val avatar: DefaultAvatar) : AvatarType()
    data class Upload(val uri: Uri) : AvatarType()
}

data class DefaultAvatar(
    val id: String,
    val emoji: String,
    val backgroundColor: Color,
    val url: String
)

private fun getDefaultAvatars(): List<DefaultAvatar> {
    return listOf(
        DefaultAvatar("1", "😀", Color(0xFF42A5F5), "default_avatar_1"),
        DefaultAvatar("2", "😊", Color(0xFF66BB6A), "default_avatar_2"),
        DefaultAvatar("3", "😎", Color(0xFFFF7043), "default_avatar_3"),
        DefaultAvatar("4", "🤗", Color(0xFFAB47BC), "default_avatar_4"),
        DefaultAvatar("5", "😇", Color(0xFFFFCA28), "default_avatar_5"),
        DefaultAvatar("6", "🥰", Color(0xFFEF5350), "default_avatar_6"),
        DefaultAvatar("7", "😋", Color(0xFF26A69A), "default_avatar_7"),
        DefaultAvatar("8", "🤔", Color(0xFF78909C), "default_avatar_8"),
        DefaultAvatar("9", "😴", Color(0xFF9575CD), "default_avatar_9"),
        DefaultAvatar("10", "🤩", Color(0xFFFFB74D), "default_avatar_10"),
        DefaultAvatar("11", "😏", Color(0xFF81C784), "default_avatar_11"),
        DefaultAvatar("12", "🤨", Color(0xFFE57373), "default_avatar_12")
    )
} 