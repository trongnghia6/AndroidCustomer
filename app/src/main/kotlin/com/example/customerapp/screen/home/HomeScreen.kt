package com.example.customerapp.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.customerapp.presentation.components.ServiceTypeItem
import com.example.customerapp.presentation.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
    navController: NavHostController
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                navigationIcon = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Đăng xuất") },
                            onClick = {
                                expanded = false
                                val sharedPref = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
                                sharedPref.edit().clear().apply()
                                onLogout()
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Search")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            if (viewModel.selectedTypeId == null) {
                // Hiển thị danh sách ServiceType
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(viewModel.serviceTypes) { type ->
                        ServiceTypeItem(
                            serviceType = type,
                            onClick = { viewModel.loadServicesByType(type.id) }
                        )
                    }
                }
            } else {
                // Hiển thị danh sách Service theo ServiceType đã chọn
                Row(
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Services", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "← Back",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            viewModel.clearSelection()
                        }
                    )
                }

                LazyColumn {
                    items(viewModel.services) { service ->
                        ListItem(
                            headlineContent = { Text(service.name) },
                            supportingContent = {
                                Column {
                                    Text(service.description ?: "")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(service.durationMinutes?.let { "$it phút" } ?: "Chưa rõ") // giả sử service có thuộc tính duration kiểu String
                                }
                            },
                            leadingContent = {
                                AsyncImage(
                                    model = service.imageUrl,  // url icon của service
                                    contentDescription = service.name,
                                    modifier = Modifier.size(40.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    navController.navigate(
                                        "service_detail/${service.name}/${service.description ?: ""}/${service.durationMinutes ?: 0}/${service.id}"
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}

