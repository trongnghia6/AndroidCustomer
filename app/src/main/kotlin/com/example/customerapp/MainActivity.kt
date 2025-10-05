package com.example.customerapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.customerapp.core.navigation.AppNavigation
import com.example.customerapp.core.paypal.PayPalDeepLinkHandler
import com.example.customerapp.data.repository.BookingPaypalRepository
import com.example.customerapp.ui.checkout.BookingPaypalViewModel
import com.example.customerapp.core.network.RetrofitInstance

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle deep link from PayPal
        PayPalDeepLinkHandler.handleDeepLink(intent.data)
        
        // Handle notification click
        val navigateTo = intent?.getStringExtra("navigate_to")
        if (navigateTo != null) {
            Log.d("MainActivity", "Notification clicked, navigate to: $navigateTo")
        }
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(initialRoute = navigateTo)
                }
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Handle PayPal payment result
        if (requestCode == 1001) { // PayPal_REQUEST_CODE
            Log.d("MainActivity", "PayPal payment result: $resultCode")
            // PayPal result sẽ được xử lý bởi PayPalService
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Important: Update the activity's intent
        
        // Handle PayPal deep link when app is already running
        PayPalDeepLinkHandler.handleDeepLink(intent.data)
        
        // Handle notification click when app is already running
        val navigateTo = intent.getStringExtra("navigate_to")
        if (navigateTo != null) {
            Log.d("MainActivity", "New notification clicked, navigate to: $navigateTo")
            // TODO: Trigger navigation to specific screen
            // Có thể implement thêm logic để navigate đến screen tương ứng
        }
    }
}