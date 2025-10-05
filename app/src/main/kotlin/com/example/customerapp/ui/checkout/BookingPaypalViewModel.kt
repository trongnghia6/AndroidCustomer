package com.example.customerapp.ui.checkout

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customerapp.data.model.BookingPaypalState
import com.example.customerapp.data.repository.BookingPaypalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

class BookingPaypalViewModel(private val repository: BookingPaypalRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<BookingPaypalState>(BookingPaypalState.Idle)
    val uiState = _uiState.asStateFlow()
    
    private var currentCaptureId: String? = null
    
    fun getCurrentCaptureId(): String? = currentCaptureId
    private val _bookingId = MutableStateFlow<Int?>(null)
    fun getBookingId(): Int? = _bookingId.value

    var orderSuccess by mutableStateOf(false)
    var orderError by mutableStateOf<String?>(null)
    
    // CustomTabs optimization
    private var customTabsSession: CustomTabsSession? = null
    private var customTabsServiceConnection: CustomTabsServiceConnection? = null
    private val isCustomTabsReady = AtomicBoolean(false)
    var refundStatus by mutableStateOf<String?>(null)
    var refundError by mutableStateOf<String?>(null)

    fun createOrder(amount: Double, currency: String = "USD") {
        viewModelScope.launch {
            try {
                _uiState.value = BookingPaypalState.Loading
                Log.d("PayPalViewModel", "Tạo order với amount: $amount $currency")
                val order = repository.createOrder(amount.toString(), currency)
                Log.d("PayPalViewModel", "Order response: $order")
                val approvalLink = order.links.find { it.rel == "approve" }?.href
                if (approvalLink != null) {
                    Log.d("PayPalViewModel", "Approval URL: $approvalLink")
                    _uiState.value = BookingPaypalState.OrderCreated(approvalLink, order.id)
                    
                    // Preload PayPal URL để tăng tốc độ mở
                    preloadPayPalUrl(approvalLink)
                } else {
                    Log.e("PayPalViewModel", "Không tìm thấy approval link trong response")
                    _uiState.value = BookingPaypalState.Error("Không tìm thấy approval link")
                }
            } catch (e: Exception) {
                Log.e("PayPalViewModel", "Lỗi tạo order: ${e.message}", e)
                _uiState.value = BookingPaypalState.Error(e.message ?: "Lỗi tạo đơn hàng PayPal")
            }
        }
    }

    fun captureOrder(orderId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = BookingPaypalState.Loading
                val capture = repository.captureOrder(orderId)
                _uiState.value = BookingPaypalState.OrderCaptured(capture.status, capture.captureId, orderId)
            } catch (e: Exception) {
                _uiState.value = BookingPaypalState.Error(e.message ?: "Lỗi thanh toán PayPal")
            }
        }
    }

    fun resetState() {
        _uiState.value = BookingPaypalState.Idle
    }



    fun handleOrderCreated(
        userId: String,
        providerServiceId: Int,
        address: String,
        startAt: LocalDateTime,
        endAt: LocalDateTime?,
        durationMinutes: Int?,
        numWorkers: String,
        total: Double,
        approvalUrl: String,
        context: Context
    ) {
        Log.d("PayPalViewModel", "🔄 handleOrderCreated called with approvalUrl: $approvalUrl")
        
        // Mở PayPal URL ngay lập tức để tăng tốc độ phản hồi
        Log.d("PayPalViewModel", "🚀 Opening PayPal URL immediately...")
        openPaypalUrlFast(context, approvalUrl)
        
        viewModelScope.launch {
            try {
                Log.d("PayPalViewModel", "🔄 Creating pending booking...")
                val insertedBooking = repository.createPendingBooking(
                    userId, providerServiceId, address,
                    startAt, endAt, durationMinutes, total, numWorkers
                )
                _bookingId.value = insertedBooking.id
                Log.d("PayPalViewModel", "✅ Created booking with ID: ${insertedBooking.id}")
                
                // Lưu PayPal Order ID vào database
                val currentState = _uiState.value
                if (currentState is BookingPaypalState.OrderCreated) {
                    Log.d("PayPalViewModel", "🔄 Updating PayPal Order ID: ${currentState.orderId}")
                    repository.updatePayPalOrderId(insertedBooking.id, currentState.orderId)
                    Log.d("PayPalViewModel", "✅ Updated PayPal Order ID")
                }
            } catch (e: Exception) {
                Log.e("PayPalViewModel", "❌ Error in handleOrderCreated: ${e.message}", e)
                orderError = e.message
            }
        }
    }

    fun handleOrderCaptured(captureId: String?, paypalOrderId: String? = null) {
        val id = _bookingId.value ?: return
        viewModelScope.launch {
            try {
                Log.d("BookingPaypalViewModel", "🔄 Starting payment result update for bookingId=$id với captureId=$captureId và paypalOrderId=$paypalOrderId")
                repository.updatePaymentResult(id, captureId, paypalOrderId)
                orderSuccess = true
                Log.d("BookingPaypalViewModel", "✅ Successfully updated payment result for bookingId=$id")
            } catch (e: Exception) {
                orderError = e.message
                Log.e("BookingPaypalViewModel", "❌ Error updating payment result for bookingId=$id: ${e.message}", e)
            }
        }
    }
    
    // Xử lý kết quả thanh toán PayPal web
    fun handleWebPaymentResult(bookingId: Int, captureId: String?, paypalOrderId: String?) {
        _bookingId.value = bookingId
        handleOrderCaptured(captureId, paypalOrderId)
    }
    
    // Mở PayPal web payment theo MVVM pattern với tối ưu tốc độ
    fun openPayPalWebPayment(context: Context, paypalOrderId: String, bookingId: Int) {
        try {
            _bookingId.value = bookingId
            val paypalUrl = "https://www.sandbox.paypal.com/checkoutnow?token=$paypalOrderId"
            
            // Preload URL trước khi mở
            preloadPayPalUrl(paypalUrl)
            
            // Mở URL với tối ưu tốc độ
            openPaypalUrlFast(context, paypalUrl)
            Log.d("PayPalViewModel", "🚀 Fast opening PayPal web payment for order: $paypalOrderId")
        } catch (e: Exception) {
            orderError = "Lỗi mở PayPal: ${e.message}"
            Log.e("PayPalViewModel", "Error opening PayPal web payment: ${e.message}", e)
        }
    }

    // Preload PayPal URL để tăng tốc độ
    private fun preloadPayPalUrl(approvalUrl: String) {
        viewModelScope.launch {
            try {
                Log.d("PayPalViewModel", "🔄 Preloading PayPal URL: $approvalUrl")
                // Có thể thêm logic preload ở đây
                // Ví dụ: cache URL hoặc warmup CustomTabs
            } catch (e: Exception) {
                Log.e("PayPalViewModel", "❌ Error preloading PayPal URL: ${e.message}", e)
            }
        }
    }
    
    // Mở PayPal URL với tối ưu tốc độ (public để sử dụng từ UI)
    fun openPaypalUrlFast(context: Context, approvalUrl: String) {
        try {
            Log.d("PayPalViewModel", "🚀 Fast opening PayPal URL: $approvalUrl")
            
            // Sử dụng CustomTabs với tối ưu tốc độ
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setDefaultShareMenuItemEnabled(false)
                .build()
            
            // Thêm flags để tăng tốc độ
            customTabsIntent.intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or 
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            
            customTabsIntent.launchUrl(context, approvalUrl.toUri())
            Log.d("PayPalViewModel", "✅ Successfully launched PayPal URL with fast mode")
        } catch (e: Exception) {
            Log.e("PayPalViewModel", "❌ Error opening PayPal URL fast: ${e.message}", e)
            // Fallback to standard method
            openPaypalUrl(context, approvalUrl)
        }
    }
    
    // Phương thức cũ để fallback
    private fun openPaypalUrl(context: Context, approvalUrl: String) {
        try {
            Log.d("PayPalViewModel", "🔄 Attempting to open PayPal URL: $approvalUrl")
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, approvalUrl.toUri())
            Log.d("PayPalViewModel", "✅ Successfully launched PayPal URL")
        } catch (e: Exception) {
            Log.e("PayPalViewModel", "❌ Error opening PayPal URL: ${e.message}", e)
            try {
                val intent = Intent(Intent.ACTION_VIEW, approvalUrl.toUri())
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d("PayPalViewModel", "✅ Fallback: Opened PayPal URL with ACTION_VIEW")
            } catch (fallbackException: Exception) {
                Log.e("PayPalViewModel", "❌ Fallback also failed: ${fallbackException.message}", fallbackException)
            }
        }
    }



//    fun refundPayment(captureId: String) {
//        viewModelScope.launch {
//            try {
//                val response = repository.refundOrder(captureId)
//                refundStatus = response.status // e.g., "COMPLETED"
//            } catch (e: Exception) {
//                refundError = e.message
//            }
//        }
//    }
}
