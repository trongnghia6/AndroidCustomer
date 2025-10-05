package com.example.customerapp.data.repository

import android.util.Log
import com.example.customerapp.core.network.*
import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.BookingInsert
import com.example.customerapp.data.model.BookingResponse
import com.example.customerapp.data.model.BookingWithProvider
import com.example.customerapp.data.model.Transaction
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

class BookingPaypalRepository(private val api: ApiService) {
    
    suspend fun createOrder(amount: String, currency: String = "USD"): OrderResponse {
        val request = CreateOrderRequest(amount, currency)
        return api.createOrder(request)
    }

    suspend fun captureOrder(orderId: String): CaptureResponse {
        return api.captureOrder(orderId)
    }

    suspend fun getOrderDetails(orderId: String): OrderDetailsResponse {
        return api.getOrderDetails(orderId)
    }

    suspend fun refundCapture(captureId: String, amount: String? = null, reason: String = "Refund request"): RefundResponse {
        val request = RefundRequest(amount, reason)
        return api.refundCapture(captureId, request)
    }
    

    suspend fun createPendingBooking(
        userId: String,
        providerServiceId: Int,
        address: String,
        startDateTime: LocalDateTime,
        endDateTime: LocalDateTime?,
        durationMinutes: Int?,
        total: Double,
        selectedWorkers: String
    ): BookingResponse {
        val startAtDateTime = startDateTime.atOffset(ZoneOffset.ofHours(7))
            ?: OffsetDateTime.now(ZoneOffset.ofHours(7))
        val endAtDateTime = endDateTime?.atOffset(ZoneOffset.ofHours(7))
            ?: startAtDateTime.plusMinutes(durationMinutes?.toLong() ?: 60L)

        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val startAt = startAtDateTime.format(formatter)
        val endAt = endAtDateTime.format(formatter)

        val booking = BookingInsert(
            customerId = userId,
            providerServiceId = providerServiceId,
            status = "pending",
            location = address,
            startAt = startAt,
            endAt = endAt,
            numWorkers = selectedWorkers.toIntOrNull() ?: 1
        )

        val bookingResult = supabase.from("bookings")
            .insert(booking) { select() }

        val insertedBooking = bookingResult.decodeSingle<BookingResponse>()

        val transaction = Transaction(
            bookingId = insertedBooking.id,
            providerServicesId = providerServiceId,
            amount = total,
            status = "pending",
            paymentMethod = "Paypal"
        )

        supabase.from("transactions").insert(transaction)

        return insertedBooking
    }

    suspend fun updatePaymentResult(bookingId: Int, captureId: String?, paypalOrderId: String? = null) {
        try {
            // Cập nhật transaction status
            supabase.from("transactions").update(
                mapOf(
                    "status" to "completed",
                    "capture_id" to captureId,
                    "paypal_order_id" to paypalOrderId,
                )
            ) {
                filter { eq("booking_id", bookingId) }
            }
            
            // Xử lý payout cho nhà cung cấp
//            processPayoutForCompletedOrder(bookingId)
            
            Log.d("BookingPaypalRepository", "Updated transaction for bookingId=$bookingId with captureId=$captureId, paypalOrderId=$paypalOrderId")
        } catch (e: Exception) {
            Log.e("BookingPaypalRepository", "Error updating transaction for bookingId=$bookingId: ${e.message}", e)
            throw e
        }
    }
    
    // Xử lý payout cho nhà cung cấp khi đơn hàng hoàn tất
     suspend fun processPayoutForCompletedOrder(bookingId: Int) {
        try {
            // Lấy thông tin transaction và booking
            val transactionResult = supabase.from("transactions").select {
                filter { eq("booking_id", bookingId) }
            }.decodeList<Transaction>()
            
            if (transactionResult.isEmpty()) {
                Log.w("BookingPaypalRepository", "No transaction found for bookingId=$bookingId")
                return
            }
            
            val transaction = transactionResult.first()
            val result = supabase.from("bookings")
                .select(
                    columns = Columns.list("id, provider_service_id, provider_services(id, provider_id, users(paypal_email))")
                ) {
                    filter { eq("id", bookingId) }
                }
                .decodeList<BookingWithProvider>()
            
            if (result.isEmpty()) {
                Log.e("BookingPaypalRepository", "No booking found for bookingId=$bookingId")
                return
            }
            
            val bookingWithProvider = result.first()
            val email = bookingWithProvider.providerServices.users.paypalEmail

            // Gọi API payout để chia tiền (sử dụng format đơn giản từ ApiService)
            val payoutRequest = PayoutRequest(
                receiver = email,
                amount = transaction.amount.toString(),
                currency = "USD"
            )

            val response = api.createPayout(payoutRequest)

            
            // Cập nhật payout_id vào transaction
            val payoutIdString = response.payoutId
            val providerServiceIdInt = bookingWithProvider.providerServiceId
            

            try {
                supabase.from("transactions").update(
                    {
                        set("payout_id", payoutIdString)
                        set("provider_services_id", providerServiceIdInt)
                    }
                ) {
                    filter { eq("booking_id", bookingId) }
                }
            } catch (e: Exception) {
                Log.e("BookingPaypalRepository", "Error updating transaction with payoutId for bookingId=$bookingId: ${e.message}", e)
            }


            
            Log.d("BookingPaypalRepository", "Payout processed successfully for bookingId=$bookingId: payoutId=$payoutIdString")
            
        } catch (e: Exception) {
            Log.e("BookingPaypalRepository", "Error processing payout for bookingId=$bookingId: ${e.message}", e)
            // Không throw exception để không ảnh hưởng đến việc cập nhật transaction
        }
    }
    
    suspend fun updatePayPalOrderId(bookingId: Int, paypalOrderId: String) {
        try {
            supabase.from("transactions").update(
                mapOf(
                    "paypal_order_id" to paypalOrderId,
                    "status" to "pending"
                )
            ) {
                filter { eq("booking_id", bookingId) }
            }
            Log.d("BookingPaypalRepository", "Updated PayPal order ID for bookingId=$bookingId with paypalOrderId=$paypalOrderId")
        } catch (e: Exception) {
            Log.e("BookingPaypalRepository", "Error updating PayPal order ID for bookingId=$bookingId: ${e.message}", e)
            throw e
        }
    }


}