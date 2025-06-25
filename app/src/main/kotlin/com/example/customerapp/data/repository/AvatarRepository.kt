package com.example.testappcc.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.testappcc.core.supabase
import com.example.testappcc.data.model.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

class AvatarRepository {
    
    private val storage = supabase.storage
    private val bucket = "avatar"
    
    // Upload ảnh mới và cập nhật database
    suspend fun uploadAndUpdateAvatar(context: Context, userId: String, imageUri: Uri): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                // 1. Lấy avatar cũ để xóa sau
                val oldAvatarUrl = getCurrentAvatarUrl(userId)
                
                // 2. Tạo tên file unique
                val fileName = "${userId}_${UUID.randomUUID()}.jpg"
                
                // 3. Convert Uri to ByteArray
                val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
                val imageBytes = inputStream?.readBytes()
                
                if (imageBytes == null) {
                    return@withContext Result.failure(Exception("Không thể đọc file ảnh"))
                }
                
                // 4. Upload lên Supabase Storage
                storage.from(bucket).upload(fileName, imageBytes) {
                    upsert = false
                }


                // 5. Lấy public URL
                val publicUrl = storage.from(bucket).publicUrl(fileName)
                
                // 6. Cập nhật database
                supabase.from("users").update(
                    mapOf("avatar" to publicUrl)
                ) {
                    filter {
                        eq("id", userId)
                    }
                }
                
                // 7. Xóa avatar cũ nếu có
                if (oldAvatarUrl != null) {
                    deleteOldAvatar(oldAvatarUrl)
                }
                
                Log.d("AvatarRepository", "✅ Upload avatar thành công: $publicUrl")
                Result.success(publicUrl)
            }
        } catch (e: Exception) {
            Log.e("AvatarRepository", "❌ Lỗi upload avatar: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Lấy URL avatar hiện tại của user
    private suspend fun getCurrentAvatarUrl(userId: String): String? {
        return try {
            val response = supabase.from("users").select {
                filter {
                    eq("id", userId)
                }
            }.decodeSingleOrNull<User>()
            
            response?.avatar
        } catch (e: Exception) {
            Log.e("AvatarRepository", "❌ Lỗi lấy avatar cũ: ${e.message}")
            null
        }
    }
    
    // Xóa ảnh cũ từ storage
    private suspend fun deleteOldAvatar(avatarUrl: String) {
        try {
            // Extract filename từ URL
            val fileName = extractFileNameFromUrl(avatarUrl)
            if (fileName != null) {
                storage.from(bucket).delete(fileName)
                Log.d("AvatarRepository", "✅ Đã xóa avatar cũ: $fileName")
            }
        } catch (e: Exception) {
            Log.e("AvatarRepository", "❌ Lỗi xóa avatar cũ: ${e.message}")
        }
    }
    
    // Extract filename từ Supabase storage URL
    private fun extractFileNameFromUrl(url: String): String? {
        return try {
            // URL format: https://xxx.supabase.co/storage/v1/object/public/avatar/filename.jpg
            val parts = url.split("/")
            val bucketIndex = parts.indexOf(bucket)
            if (bucketIndex != -1 && bucketIndex + 1 < parts.size) {
                parts[bucketIndex + 1]
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("AvatarRepository", "❌ Lỗi extract filename: ${e.message}")
            null
        }
    }
    
    // Xóa avatar (set về null)
    suspend fun removeAvatar(userId: String): Result<Boolean> {
        return try {
            withContext(Dispatchers.IO) {
                // 1. Lấy avatar cũ
                val oldAvatarUrl = getCurrentAvatarUrl(userId)
                
                // 2. Cập nhật database (set null)
                supabase.from("users").update(
                    mapOf("avatar" to null)
                ) {
                    filter {
                        eq("id", userId)
                    }
                }
                
                // 3. Xóa file từ storage
                if (oldAvatarUrl != null) {
                    deleteOldAvatar(oldAvatarUrl)
                }
                
                Log.d("AvatarRepository", "✅ Đã xóa avatar")
                Result.success(true)
            }
        } catch (e: Exception) {
            Log.e("AvatarRepository", "❌ Lỗi xóa avatar: ${e.message}")
            Result.failure(e)
        }
    }
} 