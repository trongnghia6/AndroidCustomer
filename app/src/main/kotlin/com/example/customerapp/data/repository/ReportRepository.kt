package com.example.customerapp.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.customerapp.core.supabase
import com.example.customerapp.data.model.Report
import com.example.customerapp.data.model.ReportInsert
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.days

class ReportRepository {

    private val storage = supabase.storage
    private val bucket = "report-images"
    suspend fun createReport(
        context: Context,
        report: ReportInsert,
        imageUris: List<Uri>
    ): Result<Report> = withContext(Dispatchers.IO) {
        try {
            Log.d("ReportRepository", "Creating report: ${report.title}")
            
            // Upload images first
            val imageUrls = mutableListOf<String>()
            if (imageUris.isNotEmpty()) {
                for (i in imageUris.indices) {
                    val imageUrl = uploadImage(context, imageUris[i], report.bookingId, i)
                    if (imageUrl != null) {
                        imageUrls.add(imageUrl)
                    }
                }
            }
            
            // Create report with image URLs
            val reportWithImages = report.copy(imageUrls = imageUrls)
            
            Log.d("ReportRepository", "Creating report with imageUrls: $imageUrls")
            
            val result = supabase.from("reports")
                .insert(reportWithImages) {
                    select()
                }
                .decodeSingle<Report>()
            
            Log.d("ReportRepository", "Report created successfully: ${result.id}")
            Result.success(result)
        } catch (e: Exception) {
            Log.e("ReportRepository", "Error creating report: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    private suspend fun uploadImage(
        context: Context,
        uri: Uri,
        bookingId: Long,
        index: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "report_${bookingId}_${index}_$timestamp.jpg"
            
            Log.d("ReportRepository", "Uploading image: $fileName")
            
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val bytes = inputStream.readBytes()
                
                storage.from("report-images")
                    .upload(fileName, bytes)
                
                // Get public URL
                 // 1 year;
                val publicUrl = storage.from("report-images")
                    .createSignedUrl(fileName, expiresIn = 365.days) // 1 year
                
                Log.d("ReportRepository", "Image uploaded successfully: $publicUrl")
                publicUrl
            } else {
                Log.e("ReportRepository", "Could not open input stream for URI: $uri")
                null
            }
        } catch (e: Exception) {
            Log.e("ReportRepository", "Error uploading image: ${e.message}", e)
            null
        }
    }

    suspend fun getReportsByBooking(bookingId: Long): Result<List<Report>> = withContext(Dispatchers.IO) {
        try {
            val result = supabase.from("reports")
                .select {
                    filter {
                        eq("booking_id", bookingId)
                    }
                }
                .decodeList<Report>()
            
            Log.d("ReportRepository", "Retrieved ${result.size} reports for booking $bookingId")
            Result.success(result)
        } catch (e: Exception) {
            Log.e("ReportRepository", "Error getting reports: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun getReportsByUser(userId: String): Result<List<Report>> = withContext(Dispatchers.IO) {
        try {
            val result = supabase.from("reports")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order("created_at", order = Order.ASCENDING)
                }
                .decodeList<Report>()
            
            Log.d("ReportRepository", "Retrieved ${result.size} reports for user $userId")
            Result.success(result)
        } catch (e: Exception) {
            Log.e("ReportRepository", "Error getting user reports: ${e.message}", e)
            Result.failure(e)
        }
    }
}
