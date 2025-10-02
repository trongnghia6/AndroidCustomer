package com.example.customerapp.core.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.customerapp.core.network.MapboxGeocodingService
import com.example.customerapp.core.network.RetrofitClient
import com.example.customerapp.ui.auth.AuthViewModel
import com.example.customerapp.core.supabase
import com.example.customerapp.ui.auth.LoginScreen
import com.example.customerapp.ui.auth.RegisterScreen
import com.example.customerapp.ui.profile.AvatarScreen
import com.example.customerapp.ui.suggestion.MapboxSuggestionScreen
import com.example.customerapp.ui.home.HomeScreenWrapper
import com.example.customerapp.ui.notifications.NotificationScreen
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AppNavigation(initialRoute: String? = null) {
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                // Nếu có initialRoute từ notification, navigate đến đó
                if (initialRoute == "notifications") {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                    // Delay một chút rồi navigate đến notifications
                    kotlinx.coroutines.delay(500)
                    navController.navigate("notifications")
                } else {
                    navController.navigate("search") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            val authViewModel: AuthViewModel = viewModel()
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onGotoRegister = {
                    navController.navigate("register") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }
        composable("register") {
            val authViewModel : AuthViewModel = viewModel()
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.mapbox.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val geocodingService = retrofit.create(MapboxGeocodingService::class.java)
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate("login") {
                        popUpTo("register") { inclusive = true }
                    }
                },
                goBack = {
                    navController.navigate("login") {
                        popUpTo("register") { inclusive = true }
                    }
                },
                viewModel = authViewModel,
                geocodingService = geocodingService
            )
        }
        composable("search") {
            MapboxSuggestionScreen(RetrofitClient.mapboxGeocodingService)
        }
        composable("home") {
            HomeScreenWrapper(
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        
        composable("avatar") {
            AvatarScreen(navController = navController)
        }
        
        composable("notifications") {
            NotificationScreen(
                navController = navController
            )
        }
    }
}