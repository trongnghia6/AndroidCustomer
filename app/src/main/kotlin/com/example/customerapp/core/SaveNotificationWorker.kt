package com.example.customerapp.core

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Worker để lưu notification vào database
 * Sử dụng WorkManager để đảm bảo:
 * - Chạy background an toàn
 * - Tự động retry nếu fail
 * - Không bị kill khi app bị đóng
 */
class SaveNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Lấy data từ input
            val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
            val title = inputData.getString(KEY_TITLE) ?: ""
            val body = inputData.getString(KEY_BODY) ?: ""
            val type = inputData.getString(KEY_TYPE) ?: "general"
            
            // Parse data map
            val dataMap = mutableMapOf<String, String>()
            inputData.keyValueMap.forEach { (key, value) ->
                if (key.startsWith(KEY_DATA_PREFIX)) {
                    val actualKey = key.removePrefix(KEY_DATA_PREFIX)
                    dataMap[actualKey] = value.toString()
                }
            }
            
            Log.d("SaveNotificationWorker", "🔄 Starting to save notification to database")
            Log.d("SaveNotificationWorker", "   - User ID: $userId")
            Log.d("SaveNotificationWorker", "   - Title: $title")
            Log.d("SaveNotificationWorker", "   - Type: $type")
            
            // Tạo JsonObject từ data map
            val jsonData = JsonObject(
                dataMap.mapValues { JsonPrimitive(it.value) }
            )

            val notification = NotificationInsert(
                userId = userId,
                title = title,
                body = body,
                type = type,
                data = jsonData
            )

            // Insert vào Supabase
            supabase.postgrest
                .from("notifications")
                .insert(notification)
            
            Log.d("SaveNotificationWorker", "✅ Notification saved to database successfully")
            
            // Gửi broadcast để thông báo cho UI
            sendBroadcast(type)
            
            Result.success()
        } catch (e: Exception) {
            Log.e("SaveNotificationWorker", "❌ Error saving notification: ${e.message}", e)
            
            // Retry nếu là lỗi network
            if (runAttemptCount < 3) {
                Log.d("SaveNotificationWorker", "⚠️ Retrying... (attempt ${runAttemptCount + 1}/3)")
                Result.retry()
            } else {
                Log.e("SaveNotificationWorker", "❌ Max retries reached, giving up")
                Result.failure()
            }
        }
    }
    
    private fun sendBroadcast(type: String) {
        try {
            val broadcastIntent = Intent(MyFirebaseMessagingService.NEW_NOTIFICATION_ACTION).apply {
                putExtra(MyFirebaseMessagingService.EXTRA_NOTIFICATION_TYPE, type)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                `package` = applicationContext.packageName
            }
            
            applicationContext.sendBroadcast(broadcastIntent)
            Log.d("SaveNotificationWorker", "✅ Successfully sent broadcast")
        } catch (e: Exception) {
            Log.e("SaveNotificationWorker", "❌ Failed to send broadcast: ${e.message}", e)
        }
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_TYPE = "type"
        const val KEY_DATA_PREFIX = "data_"
    }
}

