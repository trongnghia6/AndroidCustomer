package com.example.customerapp.ui.components

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.example.customerapp.data.model.ReportInsert
import com.example.customerapp.data.repository.ReportRepository
import kotlinx.coroutines.launch

@Composable
fun ReportDialog(
    bookingId: Long,
    providerId: String,
    userId: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reportRepository = remember { ReportRepository() }
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        Log.d("ReportDialog", "Image picker result: ${uris.size} images selected")
        Log.d("ReportDialog", "Current selected images: ${selectedImages.size}")
        
        if (uris.isEmpty()) {
            Log.d("ReportDialog", "No images selected by user")
            return@rememberLauncherForActivityResult
        }
        
        val newImages = selectedImages.toMutableList()
        uris.forEach { uri ->
            if (newImages.size < 5) {
                newImages.add(uri)
                Log.d("ReportDialog", "Added image: $uri")
            } else {
                Log.d("ReportDialog", "Reached maximum 5 images, skipping: $uri")
            }
        }
        selectedImages = newImages
        Log.d("ReportDialog", "Total selected images after update: ${selectedImages.size}")
    }
    
    // Permission launcher for older Android versions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("ReportDialog", "Permission granted: $isGranted")
        if (isGranted) {
            imagePickerLauncher.launch("image/*")
        }
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        Log.d("ReportDialog", "Camera result: $success")
        // Camera result will be handled by the URI provided to the launcher
    }
    
    // Function to handle image selection
    fun selectImages() {
        Log.d("ReportDialog", "Select images clicked")
        Log.d("ReportDialog", "Android version: ${Build.VERSION.SDK_INT}")
        Log.d("ReportDialog", "TIRAMISU version: ${Build.VERSION_CODES.TIRAMISU}")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ doesn't need READ_EXTERNAL_STORAGE permission
                Log.d("ReportDialog", "Android 13+, launching image picker directly")
                imagePickerLauncher.launch("image/*")
            } else {
                // Older Android versions need permission
                Log.d("ReportDialog", "Android < 13, requesting permission first")
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } catch (e: Exception) {
            Log.e("ReportDialog", "Error launching image picker: ${e.message}", e)
            errorMessage = "Lỗi mở thư viện ảnh: ${e.message}"
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Báo cáo vấn đề",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Form fields
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tiêu đề") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = title.isEmpty()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Mô tả chi tiết") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5,
                    isError = description.isEmpty()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Image selection section
                Text(
                    text = "Đính kèm ảnh (tối đa 5 ảnh)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Add image buttons
                if (selectedImages.size < 5) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Gallery button
                        Card(
                            modifier = Modifier
                                .size(80.dp)
                                .clickable {
                                    Log.d("ReportDialog", "Gallery button clicked")
                                    selectImages()
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        contentDescription = "Chọn từ thư viện",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Thư viện",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                        
                        // Camera button
                        Card(
                            modifier = Modifier
                                .size(80.dp)
                                .clickable {
                                    Log.d("ReportDialog", "Camera button clicked")
                                    selectImages() // For now, same as gallery
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "Chụp ảnh",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Camera",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Selected images
                if (selectedImages.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedImages) { uri ->
                            ImagePreview(
                                uri = uri,
                                onRemove = {
                                    selectedImages = selectedImages.filter { it != uri }
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Error message
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hủy")
                    }
                    
                    Button(
                        onClick = {
                            if (title.isNotEmpty() && description.isNotEmpty()) {
                                isLoading = true
                                errorMessage = null
                                
                                scope.launch {
                                    try {
                                        val report = ReportInsert(
                                            userId = userId,
                                            bookingId = bookingId,
                                            providerId =  providerId,
                                            title = title,
                                            description = description
                                        )
                                        
                                        val result = reportRepository.createReport(
                                            context = context,
                                            report = report,
                                            imageUris = selectedImages
                                        )
                                        
                                        result.fold(
                                            onSuccess = {
                                                Log.d("ReportDialog", "Report created successfully")
                                                onSuccess()
                                                onDismiss()
                                            },
                                            onFailure = { error ->
                                                Log.e("ReportDialog", "Error creating report: ${error.message}", error)
                                                errorMessage = "Lỗi tạo báo cáo: ${error.message}"
                                                isLoading = false
                                            }
                                        )
                                    } catch (e: Exception) {
                                        Log.e("ReportDialog", "Exception creating report: ${e.message}", e)
                                        errorMessage = "Lỗi: ${e.message}"
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && title.isNotEmpty() && description.isNotEmpty()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Gửi báo cáo")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(
    uri: Uri,
    onRemove: () -> Unit
) {
    Box {
        Card(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = "Selected image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(
                    Color.Red.copy(alpha = 0.8f),
                    CircleShape
                )
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Xóa ảnh",
                modifier = Modifier.size(16.dp),
                tint = Color.White
            )
        }
    }
}
