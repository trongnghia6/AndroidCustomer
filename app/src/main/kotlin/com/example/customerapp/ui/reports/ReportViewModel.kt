package com.example.customerapp.ui.reports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customerapp.data.model.Report
import kotlinx.coroutines.launch
import com.example.customerapp.core.supabase
import io.github.jan.supabase.postgrest.postgrest

class ReportViewModel : ViewModel() {
    var reports by mutableStateOf<List<Report>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var selectedReport by mutableStateOf<Report?>(null)
        private set

    fun loadReports(userId: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            try {
                val response = supabase.postgrest.from("reports")
                    .select {
                        filter {
                            eq("user_id", userId)
                        }
                    }
                    .decodeList<Report>() // Parse về List<Report>

                reports = response ?: emptyList()
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.message
                reports = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun selectReport(report: Report) {
        selectedReport = report
    }

    fun clearSelectedReport() {
        selectedReport = null
    }

    // Hàm này sẽ được sử dụng để lọc báo cáo theo trạng thái
    fun filterReports(status: String?) {
        reports = if (status == null) {
            reports // hoặc load lại toàn bộ nếu cần
        } else {
            reports.filter { it.status.equals(status, ignoreCase = true) }
        }
    }

    // Hàm này sẽ được sử dụng để sắp xếp báo cáo
    fun sortReports(sortBy: ReportSortOption) {
        reports = when (sortBy) {
            ReportSortOption.DATE_DESC -> reports.sortedByDescending { it.createdAt }
            ReportSortOption.DATE_ASC -> reports.sortedBy { it.createdAt }
            ReportSortOption.STATUS -> reports.sortedBy { it.status.lowercase() }
        }
    }
}

enum class ReportSortOption {
    DATE_DESC,  // Mới nhất trước
    DATE_ASC,   // Cũ nhất trước
    STATUS      // Theo trạng thái
}
