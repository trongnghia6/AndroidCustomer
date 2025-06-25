package com.example.customerapp.screen.home

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import com.example.customerapp.presentation.BottomNavItem
import com.example.customerapp.presentation.viewmodel.HomeViewModel
import com.example.customerapp.presentation.orders.OrdersScreen
import com.example.customerapp.presentation.chat.ChatScreen
import com.example.customerapp.presentation.chat.ChatListScreen
import com.example.customerapp.presentation.checkout.OrderConfirmScreen
import com.example.customerapp.data.model.User
import com.example.customerapp.core.supabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.customerapp.core.network.RetrofitClient
import com.example.customerapp.presentation.orders.OrderDetailScreen
import com.example.customerapp.presentation.service.ProviderDetailScreen
import com.example.customerapp.presentation.service.ServiceDetailScreen
import com.example.customerapp.presentation.userprofile.UserProfileScreen
import com.example.customerapp.presentation.viewmodel.OrderViewModel
import io.github.jan.supabase.postgrest.from

@Composable
fun HomeScreenWrapper(
    onLogout: () -> Unit, 
    navController: NavController? = null
) {
    val internalNavController = rememberNavController()
    val actualNavController = navController ?: internalNavController

    val bottomNavItems = listOf(
        BottomNavItem("Trang chủ", "home_main", Icons.Default.Home),
        BottomNavItem("Đơn hàng", "orders_main", Icons.Default.DateRange),
        BottomNavItem("Trò chuyện", "chat_main",  Icons.Default.Email),
        BottomNavItem("Tài khoản", "profile_main", Icons.Default.Person),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val currentRoute = internalNavController.currentBackStackEntryAsState().value?.destination?.route
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            internalNavController.navigate(item.route) {
                                popUpTo("home_main") { inclusive = false }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.name) },
                        label = { Text(item.name) }
                    )
                }
            }
        },
        content = { innerPadding ->
            NavHost(
                navController = internalNavController,
                startDestination = "home_main",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("home_main") {
                    val homeViewModel: HomeViewModel = viewModel()
                    val isLoading = homeViewModel.isLoading.value
                    if (isLoading) {
                        Text("Đang tải...")
                    } else {
                        HomeScreen(
                            onLogout = onLogout,
                            viewModel = homeViewModel,
                            navController = internalNavController
                        )
                    }
                }

                composable("profile_main") {
                    UserProfileScreen(
                        geocodingService = RetrofitClient.mapboxGeocodingService,
                        onLogout = onLogout,
                        navController = if (navController != null) navController else internalNavController
                    )
                }

                composable("orders_main") {
                    OrdersScreen(
                        onOrderClick = { orderId ->
                            internalNavController.navigate("order_detail/$orderId")
                        }
                    )
                }
                composable(
                    "order_detail/{orderId}",
                    arguments = listOf(navArgument("orderId") { type = NavType.IntType })
                ) { backStackEntry ->
                    val orderId = backStackEntry.arguments?.getInt("orderId") ?: 0
                    Log.d("RenderOrder", "OderId $orderId")
                    val orderViewModel: OrderViewModel = viewModel()
                    OrderDetailScreen(orderId, orderViewModel)
                }

                composable("provider_detail/{providerServiceId}") { backStackEntry ->
                    ProviderDetailScreen(
                        providerServiceId = backStackEntry.arguments?.getString("providerServiceId") ?: "",
                        navController = internalNavController,
                        defaultAvatar = "https://ui-avatars.com/api/?name=User&background=random"
                    )
                }

                composable(
                    route = "chat/{providerId}",
                    arguments = listOf(navArgument("providerId") { type = NavType.StringType })
                ) { backStackEntry ->
                    ChatScreen(
                        providerId = backStackEntry.arguments?.getString("providerId") ?: "",
                        navController = internalNavController
                    )
                }

                composable("chat_main") {
                    ChatListScreen(navController = internalNavController)
                }

                composable(
                    "service_detail/{name}/{description}/{duration}/{serviceId}"
                ) { backStackEntry ->
                    val name = backStackEntry.arguments?.getString("name") ?: ""
                    val description = backStackEntry.arguments?.getString("description") ?: ""
                    val duration = backStackEntry.arguments?.getString("duration")?.toIntOrNull() ?: 0
                    val serviceId = backStackEntry.arguments?.getString("serviceId")?.toIntOrNull() ?: 0

                    ServiceDetailScreen(
                        serviceId = serviceId,
                        serviceName = name,
                        serviceDescription = description,
                        durationMinutes = duration,
                        navController = internalNavController
                    )
                }

                composable(
                    "order_confirm?address={address}&price={price}&provider_service_id={provider_service_id}&durationMinutes={durationMinutes}"
                ) { backStackEntry ->
                    val context = LocalContext.current
                    val userId = remember { context.getSharedPreferences("user_session", Context.MODE_PRIVATE).getString("user_id", null) }
                    val userInfoState = remember { mutableStateOf<User?>(null) }
                    val address = backStackEntry.arguments?.getString("address") ?: ""
                    val price = backStackEntry.arguments?.getString("price")?.toDoubleOrNull() ?: 0.0
                    val providerServiceId = backStackEntry.arguments?.getString("provider_service_id")?.toIntOrNull() ?: 0
                    val durationMinutes = backStackEntry.arguments?.getString("durationMinutes")?.toIntOrNull() ?: 0
                    Log.d("Bookings", "$durationMinutes")

                    LaunchedEffect(userId) {
                        if (userId != null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val users = supabase.from("users").select().decodeList<User>()
                                val currentUser = users.find { user -> user.id == userId }
                                userInfoState.value = currentUser
                            }
                        }
                    }

                    if (userInfoState.value != null) {
                        OrderConfirmScreen(
                            address = address,
                            name = userInfoState.value?.name ?: "Tên của bạn",
                            phone = userInfoState.value?.phone_number ?: "SĐT của bạn",
                            price = price,
                            durationMinutes = durationMinutes,
                            providerServiceId = providerServiceId,
                            onOrderClick = { orderId ->
                                internalNavController.navigate("order_detail/$orderId")
                            },
                            onBackClick = { internalNavController.popBackStack() }
                        )
                    }
                }
            }
        }
    )
}


