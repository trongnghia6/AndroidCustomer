package com.example.customerapp.presentation.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.example.customerapp.presentation.viewmodel.AvatarViewModel

@Composable
fun AvatarEditor(
    modifier: Modifier = Modifier,
    avatarViewModel: AvatarViewModel = viewModel()
) {
    val context = LocalContext.current
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            avatarViewModel.uploadAvatar(context, it)
            avatarViewModel.hideImagePicker()
        }
    }
    
    // Load user khi component được tạo
    LaunchedEffect(Unit) {
        avatarViewModel.loadCurrentUser(context)
    }
    
    // Show image picker when needed
    if (avatarViewModel.showImagePicker) {
        LaunchedEffect(avatarViewModel.showImagePicker) {
            imagePickerLauncher.launch("image/*")
        }
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar container
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 4.dp,
                    color = if (avatarViewModel.isUploading) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                avatarViewModel.isUploading -> {
                    // Show progress during upload
                    CircularProgressIndicator(
                        progress = avatarViewModel.uploadProgress,
                        modifier = Modifier.size(180.dp),
                        strokeWidth = 8.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                avatarViewModel.currentUser?.avatar != null -> {
                    // Show current avatar
                    AsyncImage(
                        model = avatarViewModel.currentUser?.avatar,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clickable { avatarViewModel.showImagePicker() },
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = android.R.drawable.ic_menu_gallery)
                    )
                }
                
                else -> {
                    // Show default avatar icon
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Default Avatar",
                        modifier = Modifier
                            .size(180.dp)
                            .clickable { avatarViewModel.showImagePicker() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Upload overlay when uploading
            if (avatarViewModel.isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${(avatarViewModel.uploadProgress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // User name
        avatarViewModel.currentUser?.let { user ->
            Text(
                text = user.name ?: "Người dùng",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = user.email,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Change avatar button
            OutlinedButton(
                onClick = { 
                    if (!avatarViewModel.isUploading) {
                        avatarViewModel.showImagePicker()
                    }
                },
                enabled = !avatarViewModel.isUploading
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Thay đổi")
            }
            
            // Remove avatar button (only show if has avatar)
            if (avatarViewModel.currentUser?.avatar != null) {
                OutlinedButton(
                    onClick = { 
                        if (!avatarViewModel.isUploading) {
                            avatarViewModel.removeAvatar(context)
                        }
                    },
                    enabled = !avatarViewModel.isUploading,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Xóa")
                }
            }
        }
        
        // Error message
        avatarViewModel.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Lỗi",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { avatarViewModel.clearError() }
                    ) {
                        Text("Đóng")
                    }
                }
            }
        }
    }
} 