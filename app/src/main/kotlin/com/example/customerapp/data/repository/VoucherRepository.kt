package com.example.customerapp.data.repository

import android.util.Log
import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.Voucher
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VoucherRepository {
    
    suspend fun getActiveVouchers(): List<Voucher> = withContext(Dispatchers.IO) {
        Log.d("VoucherRepository", "Fetching active vouchers")
        try {
            val vouchers = supabase.from("vouchers")
                .select {
                    filter {
                        eq("status", "enable")
                    }
                    order("created_at", order = Order.DESCENDING)
                }
                .decodeList<Voucher>()
            
            Log.d("VoucherRepository", "Fetched ${vouchers.size} active vouchers")
            vouchers
        } catch (e: Exception) {
            Log.e("VoucherRepository", "Error fetching vouchers: ${e.message}", e)
            emptyList()
        }
    }
    
    suspend fun getVoucherById(id: Long): Voucher? = withContext(Dispatchers.IO) {
        try {
            val voucher = supabase.from("vouchers")
                .select {
                    filter {
                        eq("id", id)
                        eq("status", "active")
                    }
                }
                .decodeSingleOrNull<Voucher>()
            
            Log.d("VoucherRepository", "Fetched voucher: $voucher")
            voucher
        } catch (e: Exception) {
            Log.e("VoucherRepository", "Error fetching voucher by id: ${e.message}", e)
            null
        }
    }
}
