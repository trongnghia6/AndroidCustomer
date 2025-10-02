package com.example.customerapp.ui.orders

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.customerapp.data.model.Order
import com.example.customerapp.data.model.OrderItem

class OrderViewModel : ViewModel() {
    private val _order = MutableStateFlow<Order?>(null)
    val order: StateFlow<Order?> = _order

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // Giả lập lấy dữ liệu đơn hàng
    fun loadOrder(orderId: String) {
        _order.value = Order(
            id = orderId,
            fromAddress = "Đồ ăn | Cháo Lòng Phương Linh - Phùng Khoang",
            toAddress = "19 Ngõ 201 Phùng Khoang, Trung Văn, Nam Từ Liêm, Hà Nội",
            items = listOf(
                OrderItem(
                    name = "Ăn Ngon Rẻ - Cháo Lòng Đầy Đủ có tiêu,ớt",
                    price = 27000,
                    quantity = 1,
                    imageUrl = "https://images.foody.vn/res/g75/747845/prof/s640x400/foody-upload-api-foody-mobile-foody-upload-api-foo-190709110013.jpg"
                )
            ),
            total = 27000,
            shippingFee = 0,
            surcharge = 3000,
            note = null,
            phone = "01065-707137966",
            orderTime = "19:38",
            paymentStatus = "Tiền mặt"
        )
    }

    fun refreshOrder(orderId: String) {
        _isRefreshing.value = true
        // Giả lập delay, thực tế bạn gọi API ở đây
        loadOrder(orderId)
        _isRefreshing.value = false
    }
} 