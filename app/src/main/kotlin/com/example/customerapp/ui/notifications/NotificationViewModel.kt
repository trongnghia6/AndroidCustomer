package com.example.customerapp.ui.notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customerapp.data.model.Notification
import com.example.customerapp.data.repository.NotificationRepository
import kotlinx.coroutines.launch

class NotificationViewModel(
    private val repository: NotificationRepository = NotificationRepository()
) : ViewModel() {
    
    var notifications by mutableStateOf<List<Notification>>(emptyList())
        private set
    
    var unreadCount by mutableIntStateOf(0)
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    fun loadNotifications(userId: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                notifications = repository.getNotifications(userId)
                unreadCount = repository.getUnreadCount(userId)
            } catch (e: Exception) {
                errorMessage = "Lỗi khi tải thông báo: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    fun markAsRead(notificationId: String, userId: String) {
        viewModelScope.launch {
            try {
                val success = repository.markAsRead(notificationId)
                if (success) {
                    // Cập nhật local state
                    notifications = notifications.map { notification ->
                        if (notification.id == notificationId) {
                            notification.copy(isRead = true)
                        } else {
                            notification
                        }
                    }
                    // Cập nhật unread count
                    unreadCount = repository.getUnreadCount(userId)
                }
            } catch (e: Exception) {
                errorMessage = "Lỗi khi đánh dấu đã đọc: ${e.message}"
            }
        }
    }
    
    fun markAllAsRead(userId: String) {
        viewModelScope.launch {
            try {
                val success = repository.markAllAsRead(userId)
                if (success) {
                    // Cập nhật local state
                    notifications = notifications.map { it.copy(isRead = true) }
                    unreadCount = 0
                }
            } catch (e: Exception) {
                errorMessage = "Lỗi khi đánh dấu tất cả đã đọc: ${e.message}"
            }
        }
    }
    
    fun deleteNotification(notificationId: String, userId: String) {
        viewModelScope.launch {
            try {
                val success = repository.deleteNotification(notificationId)
                if (success) {
                    // Cập nhật local state
                    notifications = notifications.filter { it.id != notificationId }
                    unreadCount = repository.getUnreadCount(userId)
                }
            } catch (e: Exception) {
                errorMessage = "Lỗi khi xóa thông báo: ${e.message}"
            }
        }
    }
    
    fun refreshNotifications(userId: String) {
        loadNotifications(userId)
    }
    
    fun clearError() {
        errorMessage = null
    }
} 