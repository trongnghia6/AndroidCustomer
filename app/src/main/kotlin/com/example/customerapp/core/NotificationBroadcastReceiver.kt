package com.example.customerapp.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            Log.d("NotificationReceiver", "🔔 Global broadcast received")
            Log.d("NotificationReceiver", "Action: ${intent?.action}")
            Log.d("NotificationReceiver", "Intent extras: ${intent?.extras?.keySet()?.joinToString()}")
            
            if (intent?.action == MyFirebaseMessagingService.NEW_NOTIFICATION_ACTION) {
                val notificationType = intent.getStringExtra(MyFirebaseMessagingService.EXTRA_NOTIFICATION_TYPE)
                Log.d("NotificationReceiver", "📬 Received notification broadcast, type: $notificationType")
                
                // Send local broadcast that screens will listen to
                val localIntent = Intent("com.example.customerapp.LOCAL_NOTIFICATION").apply {
                    putExtra("notification_type", notificationType)
                    flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                }
                Log.d("NotificationReceiver", "🔄 Forwarding to local broadcast - Action: ${localIntent.action}")
                Log.d("NotificationReceiver", "🔄 Local broadcast extras: ${localIntent.extras?.keySet()?.joinToString()}")
                
                try {
                    context?.sendBroadcast(localIntent)
                    Log.d("NotificationReceiver", "✅ Successfully sent local broadcast")
                } catch (e: Exception) {
                    Log.e("NotificationReceiver", "❌ Failed to send local broadcast: ${e.message}", e)
                }
            } else {
                Log.d("NotificationReceiver", "⚠️ Received broadcast with unknown action: ${intent?.action}")
            }
        } catch (e: Exception) {
            Log.e("NotificationReceiver", "❌ Error handling broadcast: ${e.message}", e)
            Log.e("NotificationReceiver", "Stack trace: ", e)
        }
    }
} 