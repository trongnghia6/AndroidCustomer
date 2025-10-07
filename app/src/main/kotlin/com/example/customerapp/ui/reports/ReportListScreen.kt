package com.example.customerapp.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.customerapp.data.model.Report
import com.example.customerapp.data.model.ReportStatus
import java.text.SimpleDateFormat
import java.util.*
import java.text.ParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportListScreen(
    viewModel: ReportViewModel = viewModel(),
    userId: String,
    onBackClick: () -> Unit
) {
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedReport by remember { mutableStateOf<Report?>(null) }

    // Load reports when screen is first displayed
    LaunchedEffect(userId) {
        viewModel.loadReports(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Báo cáo của tôi") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    // Filter button
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Lọc")
                    }
                    // Sort button
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sắp xếp")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (viewModel.reports.isEmpty()) {
                EmptyReportList(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(viewModel.reports) { report ->
                        ReportCard(
                            report = report,
                            onClick = { selectedReport = report }
                        )
                    }
                }
            }

            // Filter menu
            FilterMenu(
                expanded = showFilterMenu,
                onDismiss = { showFilterMenu = false },
                onFilterSelected = { status ->
                    viewModel.filterReports(status)
                    showFilterMenu = false
                }
            )

            // Sort menu
            SortMenu(
                expanded = showSortMenu,
                onDismiss = { showSortMenu = false },
                onSortSelected = { sortOption ->
                    viewModel.sortReports(sortOption)
                    showSortMenu = false
                }
            )

            // Show report detail dialog when a report is selected
            selectedReport?.let { report ->
                ReportDetailDialog(
                    report = report,
                    onDismiss = { selectedReport = null }
                )
            }
        }
    }
}

@Composable
private fun ReportCard(
    report: Report,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = report.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                StatusChip(status = report.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = report.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Order ID
                Text(
                    text = "Đơn hàng: #${report.bookingId}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // Created date
                Text(
                    text = formatDateTime(report.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatDateTime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val targets = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    for (p in patterns) {
        try {
            val parsed = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw)
            if (parsed != null) return targets.format(parsed)
        } catch (_: ParseException) {}
    }
    return raw // fallback to raw string
}

@Composable
fun StatusChip(status: String) {
    val reportStatus = ReportStatus.from(status)
    val (backgroundColor, contentColor, text) = when (reportStatus) {
        ReportStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Chờ xử lý"
        )
        ReportStatus.PROCESSING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Đang xử lý"
        )
        ReportStatus.RESOLVED -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Đã giải quyết"
        )
        ReportStatus.REJECTED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Từ chối"
        )
    }

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyReportList(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Report,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Chưa có báo cáo nào",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Các báo cáo bạn gửi sẽ xuất hiện ở đây",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun FilterMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onFilterSelected: (String?) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Tất cả") },
            onClick = { onFilterSelected(null) },
            leadingIcon = {
                Icon(Icons.Default.List, contentDescription = null)
            }
        )
        ReportStatus.values().forEach { status ->
            DropdownMenuItem(
                text = {
                    Text(
                        when (status) {
                            ReportStatus.PENDING -> "Chờ xử lý"
                            ReportStatus.PROCESSING -> "Đang xử lý"
                            ReportStatus.RESOLVED -> "Đã giải quyết"
                            ReportStatus.REJECTED -> "Từ chối"
                        }
                    )
                },
                onClick = { onFilterSelected(status.value) },
                leadingIcon = {
                    Icon(
                        when (status) {
                            ReportStatus.PENDING -> Icons.Default.Pending
                            ReportStatus.PROCESSING -> Icons.Default.Refresh
                            ReportStatus.RESOLVED -> Icons.Default.CheckCircle
                            ReportStatus.REJECTED -> Icons.Default.Cancel
                        },
                        contentDescription = null
                    )
                }
            )
        }
    }
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSortSelected: (ReportSortOption) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Mới nhất trước") },
            onClick = { onSortSelected(ReportSortOption.DATE_DESC) },
            leadingIcon = {
                Icon(Icons.Default.ArrowDownward, contentDescription = null)
            }
        )
        DropdownMenuItem(
            text = { Text("Cũ nhất trước") },
            onClick = { onSortSelected(ReportSortOption.DATE_ASC) },
            leadingIcon = {
                Icon(Icons.Default.ArrowUpward, contentDescription = null)
            }
        )
        DropdownMenuItem(
            text = { Text("Theo trạng thái") },
            onClick = { onSortSelected(ReportSortOption.STATUS) },
            leadingIcon = {
                Icon(Icons.Default.Sort, contentDescription = null)
            }
        )
    }
}
