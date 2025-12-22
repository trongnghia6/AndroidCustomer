package com.example.customerapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
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
import com.example.customerapp.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // [Tối ưu 7 - StrictMode] Bật StrictMode trong chế độ DEBUG để phát hiện lỗi
        enableStrictMode()
        
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
    
    /**
     * [Tối ưu 7 - StrictMode]
     * Bật StrictMode trong chế độ DEBUG để phát hiện các vấn đề:
     * - ThreadPolicy: Phát hiện disk reads/writes và network trên main thread
     * - VmPolicy: Phát hiện rò rỉ bộ nhớ (Activity, Closable objects, SQLite)
     * 
     * Chỉ bật trong DEBUG mode để không ảnh hưởng đến production.
     */
    private fun enableStrictMode() {
        if (BuildConfig.DEBUG) {
            // Phát hiện thao tác sai trên Main Thread
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()      // Đọc file trên main thread
                    .detectDiskWrites()     // Ghi file trên main thread
                    .detectNetwork()        // Network call trên main thread
                    .penaltyLog()           // Ghi log khi phát hiện lỗi
                    .build()
            )
            
            // Phát hiện rò rỉ bộ nhớ
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()  // Quên đóng file, stream
                    .detectLeakedSqlLiteObjects()   // Quên đóng database cursor
                    .detectActivityLeaks()          // Activity bị leak
                    .penaltyLog()                   // Ghi log khi phát hiện lỗi
                    .build()
            )
            
            Log.d("MainActivity", "StrictMode enabled for DEBUG build")
        }
    }
    
    /**
     * Xóa cache tin nhắn khi khởi động app
     * Tùy chọn: Có thể bật/tắt hoặc chỉ xóa cache cũ hơn X ngày
     */
    
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