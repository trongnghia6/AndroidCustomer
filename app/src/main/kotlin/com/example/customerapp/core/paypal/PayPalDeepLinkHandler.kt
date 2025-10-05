package com.example.customerapp.core.paypal

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PayPalResult(
    val status: String,
    val orderId: String? = null
)

object PayPalDeepLinkHandler {
    private val _paypalResult = MutableStateFlow<PayPalResult?>(null)
    val paypalResult: StateFlow<PayPalResult?> = _paypalResult.asStateFlow()
    
    private val processedOrderIds = mutableSetOf<String>()
    
    fun handleDeepLink(uri: Uri?) {
        if (uri != null && uri.scheme == "myapp" && uri.host == "paypal-return") {
            Log.d("PayPalDeepLinkHandler", "PayPal deep link received: $uri")
            
            val status = uri.getQueryParameter("status")
            val orderId = uri.getQueryParameter("orderId")
            
            Log.d("PayPalDeepLinkHandler", "PayPal status: $status, orderId: $orderId")
            
            // Prevent processing the same order multiple times
            if (orderId != null && processedOrderIds.contains(orderId)) {
                Log.d("PayPalDeepLinkHandler", "Order $orderId already processed, ignoring duplicate")
                return
            }
            
            if (orderId != null) {
                processedOrderIds.add(orderId)
            }
            
            _paypalResult.value = PayPalResult(
                status = status ?: "unknown",
                orderId = orderId
            )
        }
    }
    
    fun clearResult() {
        _paypalResult.value = null
    }
    
    fun clearProcessedOrders() {
        processedOrderIds.clear()
    }
}
