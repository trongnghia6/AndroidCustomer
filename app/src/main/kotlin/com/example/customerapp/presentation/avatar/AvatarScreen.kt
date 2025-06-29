package com.example.customerapp.presentation.avatar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.customerapp.presentation.components.AvatarEditor
import com.example.customerapp.presentation.viewmodel.AvatarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarScreen(
    navController: NavController,
    avatarViewModel: AvatarViewModel = viewModel()
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { 
                Text("Ảnh đại diện") 
            },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại"
                    )
                }
            }
        )
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Avatar Editor Component
            AvatarEditor(
                avatarViewModel = avatarViewModel
            )
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
} 