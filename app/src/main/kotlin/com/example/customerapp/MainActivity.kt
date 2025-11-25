package com.example.customerapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.customerapp.core.navigation.AppNavigation
import com.example.customerapp.core.paypal.PayPalDeepLinkHandler

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // [Tối ưu 4 - Cấp độ API] Xin POST_NOTIFICATIONS cho API 33+ tại onCreate
        requestNotificationPermissionIfNeeded()
        
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
    
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(permission),
                    REQUEST_POST_NOTIFICATIONS
                )
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
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
            Log.d("MainActivity", "POST_NOTIFICATIONS granted=$granted")
        }
    }
    
    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 2001
    }
}