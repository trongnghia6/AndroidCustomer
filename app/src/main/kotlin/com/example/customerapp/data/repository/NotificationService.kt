package com.example.customerapp.data.repository

import android.util.Log
import com.example.customerapp.BuildConfig
import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.NotificationInsert
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationService {
    private val httpClient = HttpClient(CIO){
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
            })
        }
    }
    private val repository = NotificationRepository()
    
    companion object {
        const val TYPE_LOGIN_SUCCESS = "login_success"
        const val TYPE_BOOKING = "booking"
        const val TYPE_ORDER = "order"
        const val TYPE_SYSTEM = "system"
        const val TYPE_GENERAL = "general"
        const val TYPE_MESSAGE = "message"
        const val TYPE_BOOKING_UPDATE = "booking_update"
    }
    
    /**
     * Gửi thông báo đăng nhập thành công
     */
    suspend fun sendLoginSuccessNotification(userId: String, userName: String = "bạn"): Boolean {
        val title = "Đăng nhập thành công! 🎉"
        val body = "Chào mừng $userName quay trở lại! Hãy khám phá các dịch vụ mới nhất của chúng tôi."
        
        return sendNotificationToUser(
            userId = userId,
            title = title,
            body = body,
            type = TYPE_LOGIN_SUCCESS,
            data = mapOf(
                "action" to "welcome",
                "timestamp" to System.currentTimeMillis().toString()
            )
        )
    }
    
    /**
     * Gửi thông báo booking thành công
     */
    suspend fun sendBookingSuccessNotification(userId: String, bookingId: String, serviceName: String): Boolean {
        val title = "Đặt lịch thành công ✅"
        val body = "Lịch hẹn $serviceName của bạn đã được xác nhận. Mã đặt lịch: #$bookingId"
        
        return sendNotificationToUser(
            userId = userId,
            title = title,
            body = body,
            type = TYPE_BOOKING,
            data = mapOf(
                "booking_id" to bookingId,
                "service_name" to serviceName,
                "action" to "view_booking"
            )
        )
    }
    
    /**
     * Gửi thông báo booking với trạng thái
     */
    suspend fun sendBookingNotification(userId: String, bookingId: String, status: String): Boolean {
        val (title, body) = when (status) {
            "confirmed" -> "Đặt lịch đã xác nhận ✅" to "Lịch hẹn #$bookingId đã được xác nhận."
            "cancelled" -> "Lịch hẹn đã hủy ❌" to "Lịch hẹn #$bookingId đã bị hủy."
            "completed" -> "Lịch hẹn hoàn thành 🎉" to "Lịch hẹn #$bookingId đã hoàn thành."
            "rescheduled" -> "Lịch hẹn đã đổi lịch 🔄" to "Lịch hẹn #$bookingId đã được đổi lịch."
            else -> "Cập nhật lịch hẹn 📋" to "Lịch hẹn #$bookingId đã được cập nhật trạng thái: $status"
        }
        
        return sendNotificationToUser(
            userId = userId,
            title = title,
            body = body,
            type = TYPE_BOOKING,
            data = mapOf(
                "booking_id" to bookingId,
                "status" to status,
                "action" to "view_booking"
            )
        )
    }
    
    /**
     * Gửi thông báo đơn hàng mới
     */
    suspend fun sendOrderNotification(userId: String, orderId: String, status: String): Boolean {
        val title = when (status) {
            "created" -> "Đơn hàng mới được tạo 📦"
            "confirmed" -> "Đơn hàng đã được xác nhận ✅"
            "processing" -> "Đơn hàng đang được xử lý ⚙️"
            "completed" -> "Đơn hàng hoàn thành 🎉"
            else -> "Cập nhật đơn hàng"
        }
        
        val body = "Đơn hàng #$orderId đã được cập nhật trạng thái: $status"
        
        return sendNotificationToUser(
            userId = userId,
            title = title,
            body = body,
            type = TYPE_ORDER,
            data = mapOf(
                "order_id" to orderId,
                "status" to status,
                "action" to "view_order"
            )
        )
    }
    
    /**
     * Gửi thông báo hệ thống
     */
    suspend fun sendSystemNotification(userId: String, title: String, body: String): Boolean {
        return sendNotificationToUser(
            userId = userId,
            title = title,
            body = body,
            type = TYPE_SYSTEM
        )
    }
    
    /**
     * Function chính để gửi notification - CHỈ CHO MANUAL NOTIFICATIONS
     * AUTO NOTIFICATIONS (booking/message) được xử lý bởi database triggers
     */
    private suspend fun sendNotificationToUser(
        userId: String, 
        title: String, 
        body: String, 
        type: String = TYPE_GENERAL,
        data: Map<String, String>? = null
    ): Boolean {
        return try {
            // ✅ CHỈ SAVE VÀO DATABASE - KHÔNG GỌI FCM
            // (Triggers sẽ xử lý FCM cho auto notifications)
            
            Log.d("NotificationService", "📝 Saving manual notification to database only")
            
            val notificationInsert = NotificationInsert(
                userId = userId,
                title = title,
                body = body,
                type = type,
                data = data
            )
            
            val saveSuccess = repository.insertNotification(notificationInsert)
            if (!saveSuccess) {
                Log.e("NotificationService", "Failed to save notification to database")
                return false
            }
            
            Log.d("NotificationService", "✅ Manual notification saved successfully")
            Log.d("NotificationService", "ℹ️ FCM will be handled by triggers for auto notifications")
            return true
            
        } catch (e: Exception) {
            Log.e("NotificationService", "Error sending notification: ${e.message}", e)
            false
        }
    }
    
    /**
     * Lấy FCM tokens của user
     */
    private suspend fun getFCMTokens(userId: String): List<String> {
        return try {
            withContext(Dispatchers.IO + SupervisorJob()) {
                Log.d("NotificationService", "Fetching FCM tokens for user: $userId")
                
                val result = supabase.postgrest
                    .from("user_push_tokens")
                    .select(columns = Columns.list("token")) {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<Map<String, String>>()
                
                val tokens = result.mapNotNull { it["token"] }.filter { it.isNotBlank() }
                Log.d("NotificationService", "Found ${tokens.size} FCM tokens for user $userId")
                
                tokens
            }
        } catch (e: Exception) {
            Log.e("NotificationService", "Error fetching FCM tokens: ${e.message}")
            Log.e("NotificationService", "Error details: ", e)
            emptyList()
        }
    }
    
    /**
     * Gửi FCM notification đến một token cụ thể
     */
    private suspend fun sendFCMNotification(
        token: String, 
        title: String, 
        body: String, 
        type: String,
        data: Map<String, String>?
    ): Boolean {
        return withContext(Dispatchers.IO + SupervisorJob()) {
            try {
                val fcmData = mutableMapOf("type" to type)
                data?.let { fcmData.putAll(it) }
                
                Log.d("NotificationService", "Sending FCM to token: ${token.take(20)}...")
                Log.d("NotificationService", "FCM payload - Title: $title, Body: $body, Type: $type")
                
                // Gửi qua Supabase Edge Function
                val response = httpClient.post("https://uyxudwhglwwbvbnjgwmw.supabase.co/functions/v1/send-push") {
                    contentType(ContentType.Application.Json)
                    headers {
                        append("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}") // Sử dụng Anon Key hoặc Service Role Key
                    }
                    setBody(FcmPayload(token, title, body, fcmData))
                }
                
                if (response.status.isSuccess()) {
                    Log.d("NotificationService", "✅ FCM sent successfully to token: ${token.take(10)}...")
                    true
                } else {
                    val responseText = try { response.bodyAsText() } catch (e: Exception) { "Unable to read response" }
                    Log.e("NotificationService", "❌ FCM failed with status: ${response.status}")
                    Log.e("NotificationService", "❌ Response body: $responseText")
                    
                    if (response.status.value == 401) {
                        Log.e("NotificationService", "❌ 401 Unauthorized - Firebase service account not configured in Supabase!")
                        Log.e("NotificationService", "❌ Please check FIREBASE_SERVICE_ACCOUNT_SETUP.md for setup instructions")
                    }
                    false
                }
                
            } catch (e: Exception) {
                Log.e("NotificationService", "❌ Error sending FCM: ${e.message}")
                Log.e("NotificationService", "❌ Error details: ", e)
                false
            }
        }
    }
    
    /**
     * Test function - gửi notification test
     */
    suspend fun sendTestNotification(userId: String): Boolean {
        return sendNotificationToUser(
            userId = userId,
            title = "🧪 Test Notification",
            body = "Đây là thông báo test để kiểm tra hệ thống notification. Thời gian: ${System.currentTimeMillis()}",
            type = TYPE_SYSTEM,
            data = mapOf(
                "test" to "true",
                "timestamp" to System.currentTimeMillis().toString()
            )
        )
    }
    
    /**
     * Gửi thông báo khi có booking mới hoặc cập nhật
     */
    suspend fun sendBookingUpdateNotification(
        userId: String, 
        bookingId: String, 
        action: String, // "created", "updated", "confirmed", "cancelled", "completed"
        serviceName: String? = null
    ): Boolean {
        val (title, body) = when (action) {
            "created" -> "Đặt lịch mới 📅" to "Bạn đã đặt lịch thành công. Mã booking: #$bookingId"
            "updated" -> "Cập nhật lịch hẹn ✏️" to "Lịch hẹn #$bookingId đã được cập nhật thông tin."
            "confirmed" -> "Lịch hẹn đã xác nhận ✅" to "Lịch hẹn #$bookingId đã được xác nhận bởi nhà cung cấp dịch vụ."
            "cancelled" -> "Lịch hẹn đã hủy ❌" to "Lịch hẹn #$bookingId đã bị hủy. Vui lòng liên hệ để biết thêm chi tiết."
            "completed" -> "Dịch vụ hoàn thành 🎉" to "Dịch vụ trong lịch hẹn #$bookingId đã hoàn thành. Cảm ơn bạn đã sử dụng dịch vụ!"
            else -> "Cập nhật booking 📋" to "Booking #$bookingId có cập nhật mới."
        }
        
        return sendNotificationToUser(
            userId = userId,
            title = title,
            body = if (serviceName != null) "$body\nDịch vụ: $serviceName" else body,
            type = TYPE_BOOKING_UPDATE,
            data = mapOf(
                "booking_id" to bookingId,
                "action" to action,
                "service_name" to (serviceName ?: ""),
                "navigate_to" to "booking_detail"
            )
        )
    }
    
    /**
     * Gửi thông báo khi nhận tin nhắn mới
     */
    suspend fun sendNewMessageNotification(
        userId: String,
        senderId: String,
        senderName: String,
        messagePreview: String,
        conversationId: String? = null
    ): Boolean {
        val title = "Tin nhắn mới từ $senderName 💬"
        val body = if (messagePreview.length > 50) {
            "${messagePreview.take(47)}..."
        } else {
            messagePreview
        }
        
        return sendNotificationToUser(
            userId = userId,
            title = title,
            body = body,
            type = TYPE_MESSAGE,
            data = mapOf(
                "sender_id" to senderId,
                "sender_name" to senderName,
                "conversation_id" to (conversationId ?: ""),
                "navigate_to" to "chat_detail"
            )
        )
    }

    /**
     * Test function - kiểm tra FCM tokens
     */
    suspend fun testFCMTokenRetrieval(userId: String): List<String> {
        Log.d("NotificationService", "=== Testing FCM Token Retrieval ===")
        val tokens = getFCMTokens(userId)
        Log.d("NotificationService", "Retrieved ${tokens.size} tokens for user $userId")
        tokens.forEachIndexed { index, token ->
            Log.d("NotificationService", "Token $index: ${token.take(20)}...")
        }
        Log.d("NotificationService", "=====================================")
        return tokens
    }
}
@kotlinx.serialization.Serializable
data class FcmPayload(
    val token: String,
    val title: String,
    val body: String,
    val data: Map<String, String>
)
