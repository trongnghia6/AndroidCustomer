package com.example.customerapp.core.network

import retrofit2.http.*
import retrofit2.http.POST

data class CreateOrderRequest(val amount: String, val currency: String = "USD")
data class OrderResponse(val id: String, val links: List<Link>)
data class Link(val href: String, val rel: String, val method: String)
data class CaptureResponse(val status: String, val captureId: String? = null)
data class OrderDetailsResponse(
    val orderId: String,
    val status: String,
    val captureInfo: CaptureInfo?,
    val payer: PayerInfo?,
    val purchaseUnits: List<Any>?
)
data class CaptureInfo(
    val captureId: String,
    val status: String,
    val amount: Amount,
    val createTime: String,
    val updateTime: String
)
data class Amount(val value: String, val currency_code: String)
data class PayerInfo(
    val name: PayerName?,
    val email_address: String,
    val payer_id: String
)
data class PayerName(val given_name: String, val surname: String)
data class RefundRequest(val amount: String? = null, val reason: String = "Refund request")
data class RefundResponse(
    val id: String,
    val status: String,
    val amount: Amount?
)

data class PayoutRequest(
    val receiver: String,
    val amount: String,
    val currency: String = "USD"
)

data class PayoutResponse(
    val payoutId: String,
    val status: String,
    val amount: Amount?
)

interface ApiService {
    @POST("create-order")
    suspend fun createOrder(@Body request: CreateOrderRequest): OrderResponse

    @POST("capture-order/{orderID}")
    suspend fun captureOrder(@Path("orderID") orderID: String): CaptureResponse

    @GET("order/{orderID}")
    suspend fun getOrderDetails(@Path("orderID") orderID: String): OrderDetailsResponse

    @POST("refund/{captureID}")
    suspend fun refundCapture(@Path("captureID") captureID: String, @Body request: RefundRequest): RefundResponse

    @POST("payout")
    suspend fun createPayout(@Body request: PayoutRequest): PayoutResponse
}
