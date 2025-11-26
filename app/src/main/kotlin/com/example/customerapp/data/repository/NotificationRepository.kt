package com.example.customerapp.data.repository

import android.util.Log
import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.Notification
import com.example.customerapp.data.model.NotificationInsert
import com.example.customerapp.data.model.NotificationUpdate
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order

class NotificationRepository {
    
    suspend fun getNotifications(
        userId: String,
        limit: Int,
        offset: Int
    ): List<Notification> {
        return try {
            val result = supabase.postgrest
                .from("notifications")
                // [Tối ưu 3 - Dữ liệu phù hợp] Chỉ lấy cột cần thiết + limit/offset để giảm payload mỗi request
                .select(columns = Columns.list("id,title,body,type,data,is_read,created_at")){
                    filter {
                        eq("user_id", userId)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }

                .decodeList<Notification>()
            
            Log.d("NotificationRepo", "Fetched ${result.size} notifications")
            result
        } catch (e: Exception) {
            Log.e("NotificationRepo", "Error fetching notifications: ${e.message}")
            emptyList()
        }
    }

    suspend fun getUnreadCount(userId: String): Int {
        return try {
            val result = supabase.postgrest
                .from("notifications")
                .select(columns = Columns.list("id")) { // 1. Chỉ lấy cột ID

                    // 2. Đưa lệnh đếm vào TRONG ngoặc nhọn
                    count(Count.EXACT)

                    filter {
                        eq("user_id", userId)
                        eq("is_read", false)
                    }

                    // 3. Giới hạn tải về 0 dòng (chỉ lấy số đếm header)
                    range(0, 0)
                }

            // 4. Lấy kết quả đếm từ Header
            Log.d("NotificationRepo", "Unread count for user $userId: ${result}")
            result.countOrNull()?.toInt() ?: 0

        } catch (e: Exception) {
            Log.e("NotificationRepo", "Lỗi đếm: ${e.message}")
            0
        }
    }
    
    suspend fun markAsRead(notificationId: String): Boolean {
        return try {
            supabase.postgrest
                .from("notifications")
                .update(NotificationUpdate(isRead = true)) {
                    filter {
                        eq("id", notificationId)
                    }
                }
            Log.d("NotificationRepo", "Marked notification $notificationId as read")
            true
        } catch (e: Exception) {
            Log.e("NotificationRepo", "Error marking notification as read: ${e.message}")
            false
        }
    }
    
    suspend fun markAllAsRead(userId: String): Boolean {
        return try {
            supabase.postgrest
                .from("notifications")
                .update(NotificationUpdate(isRead = true))
                {
                    filter {
                        eq("user_id", userId)
                        eq("is_read", false)
                    }
                }
            
            Log.d("NotificationRepo", "Marked all notifications as read for user $userId")
            true
        } catch (e: Exception) {
            Log.e("NotificationRepo", "Error marking all notifications as read: ${e.message}")
            false
        }
    }
    
    suspend fun insertNotification(notification: NotificationInsert): Boolean {
        return try {
            supabase.postgrest
                .from("notifications")
                .insert(notification)
            
            Log.d("NotificationRepo", "Inserted notification: ${notification.title}")
            true
        } catch (e: Exception) {
            Log.e("NotificationRepo", "Error inserting notification: ${e.message}")
            false
        }
    }
    
    suspend fun deleteNotification(notificationId: String): Boolean {
        return try {
            supabase.postgrest
                .from("notifications")
                .delete(){
                    filter {
                        eq("id", notificationId)
                    }
                }
            
            Log.d("NotificationRepo", "Deleted notification $notificationId")
            true
        } catch (e: Exception) {
            Log.e("NotificationRepo", "Error deleting notification: ${e.message}")
            false
        }
    }
} 