package com.example.customerapp.data.model

open class BookingPaypalState {
    object Idle : BookingPaypalState()
    object Loading : BookingPaypalState()
    data class OrderCreated(val approvalUrl: String, val orderId: String) : BookingPaypalState()
    data class OrderCaptured(val status: String, val captureId: String?, val orderId: String) : BookingPaypalState()
    data class Error(val message: String) : BookingPaypalState()
}