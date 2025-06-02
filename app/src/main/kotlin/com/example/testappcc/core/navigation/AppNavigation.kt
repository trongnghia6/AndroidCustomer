package com.example.testappcc.core.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.testappcc.core.network.MapboxGeocodingService
import com.example.testappcc.core.network.RetrofitClient
import com.example.testappcc.model.viewmodel.AuthViewModel
import com.example.testappcc.core.supabase
import com.example.testappcc.screen.LoginScreen
import com.example.testappcc.presentation.search.MapboxSuggestionScreen
import com.example.testappcc.screen.RegisterScreen
import com.example.testappcc.screen.home.HomeScreenWrapper
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
//    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                navController.navigate("search") {
                    popUpTo("login") { inclusive = true }
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
    }
}