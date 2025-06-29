package com.example.customerapp.data.repository

import android.util.Log
import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.Notification
import com.example.customerapp.data.model.NotificationInsert
import com.example.customerapp.data.model.NotificationUpdate
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

class NotificationRepository {
    
    suspend fun getNotifications(userId: String): List<Notification> {
        return try {
            val result = supabase.postgrest
                .from("notifications")
                .select(columns = Columns.list("*")){
                    filter {
                        eq("user_id", userId)
                    }
                    order(column = "created_at", order = Order.DESCENDING)
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
                .select(columns = Columns.list("id")){
                    filter {
                        eq("user_id", userId)
                        eq("is_read", false)
                    }
                }
                .decodeList<Map<String, String>>()
            
            result.size
        } catch (e: Exception) {
            Log.e("NotificationRepo", "Error fetching unread count: ${e.message}")
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