package com.example.customerapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.customerapp.core.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Important: Update the activity's intent
        
        // Handle notification click when app is already running
        val navigateTo = intent.getStringExtra("navigate_to")
        if (navigateTo != null) {
            Log.d("MainActivity", "New notification clicked, navigate to: $navigateTo")
            // TODO: Trigger navigation to specific screen
            // Có thể implement thêm logic để navigate đến screen tương ứng
        }
    }
}