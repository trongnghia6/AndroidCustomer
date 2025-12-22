package com.example.customerapp.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (intent?.action == MyFirebaseMessagingService.NEW_NOTIFICATION_ACTION) {
                val notificationType = intent.getStringExtra(MyFirebaseMessagingService.EXTRA_NOTIFICATION_TYPE)

                // Send local broadcast that screens will listen to
                val localIntent = Intent("com.example.customerapp.LOCAL_NOTIFICATION").apply {
                    putExtra("notification_type", notificationType)
                    flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                }

                try {
                    context?.sendBroadcast(localIntent)
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