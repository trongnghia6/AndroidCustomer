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

    var isAppending by mutableStateOf(false)
        private set

    var hasMore by mutableStateOf(true)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val pageSize = 20
    private var currentOffset = 0
    
    fun loadNotifications(userId: String, refresh: Boolean = false) {
        if (isLoading) return
        viewModelScope.launch {
            if (refresh) {
                currentOffset = 0
                hasMore = true
                notifications = emptyList()
            }
            isLoading = true
            errorMessage = null
            
            try {
                val chunk = repository.getNotifications(
                    userId = userId,
                    limit = pageSize,
                    offset = currentOffset
                )
                notifications = if (currentOffset == 0) chunk else notifications + chunk
                currentOffset = notifications.size
                hasMore = chunk.size == pageSize
                unreadCount = repository.getUnreadCount(userId)
            } catch (e: Exception) {
                errorMessage = "Lỗi khi tải thông báo: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMoreNotifications(userId: String) {
        if (isLoading || isAppending || !hasMore) return
        viewModelScope.launch {
            isAppending = true
            try {
                val chunk = repository.getNotifications(
                    userId = userId,
                    limit = pageSize,
                    offset = currentOffset
                )
                notifications = notifications + chunk
                currentOffset = notifications.size
                hasMore = chunk.size == pageSize
            } catch (e: Exception) {
                errorMessage = "Lỗi khi tải thêm thông báo: ${e.message}"
            } finally {
                isAppending = false
            }
        }
    }
    
    fun markAsRead(notificationId: String, userId: String) {
        // [Tối ưu 6 - Phản hồi] Optimistic update để UI phản hồi ngay, rollback nếu API lỗi
        val previousList = notifications
        val previousUnread = unreadCount
        val target = previousList.find { it.id == notificationId } ?: return
        if (target.isRead) return

        notifications = previousList.map { notification ->
            if (notification.id == notificationId) notification.copy(isRead = true) else notification
        }
        unreadCount = (previousUnread - 1).coerceAtLeast(0)

        viewModelScope.launch {
            try {
                val success = repository.markAsRead(notificationId)
                if (!success) {
                    notifications = previousList
                    unreadCount = previousUnread
                }
            } catch (e: Exception) {
                notifications = previousList
                unreadCount = previousUnread
                errorMessage = "Lỗi khi đánh dấu đã đọc: ${e.message}"
            }
        }
    }
    
    fun markAllAsRead(userId: String) {
        if (notifications.isEmpty() || unreadCount == 0) return
        // [Tối ưu 6 - Phản hồi] Đánh dấu tất cả bằng optimistic update để tránh chờ network
        val previousList = notifications
        val previousUnread = unreadCount
        notifications = notifications.map { it.copy(isRead = true) }
        unreadCount = 0

        viewModelScope.launch {
            try {
                val success = repository.markAllAsRead(userId)
                if (!success) {
                    notifications = previousList
                    unreadCount = previousUnread
                }
            } catch (e: Exception) {
                notifications = previousList
                unreadCount = previousUnread
                errorMessage = "Lỗi khi đánh dấu tất cả đã đọc: ${e.message}"
            }
        }
    }
    
    fun deleteNotification(notificationId: String, userId: String) {
        // [Tối ưu 6 - Phản hồi] Xóa thông báo theo optimistic update để tránh giật lag
        val previousList = notifications
        val previousUnread = unreadCount
        val previousOffset = currentOffset
        val target = previousList.find { it.id == notificationId } ?: return

        notifications = previousList.filter { it.id != notificationId }
        if (!target.isRead) {
            unreadCount = (previousUnread - 1).coerceAtLeast(0)
        }
        currentOffset = notifications.size

        viewModelScope.launch {
            try {
                val success = repository.deleteNotification(notificationId)
                if (!success) {
                    notifications = previousList
                    unreadCount = previousUnread
                    currentOffset = previousOffset
                }
            } catch (e: Exception) {
                notifications = previousList
                unreadCount = previousUnread
                currentOffset = previousOffset
                errorMessage = "Lỗi khi xóa thông báo: ${e.message}"
            }
        }
    }
    
    fun refreshNotifications(userId: String) {
        loadNotifications(userId, refresh = true)
    }
    
    fun clearError() {
        errorMessage = null
    }
}