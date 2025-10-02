package com.example.customerapp.model.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.example.customerapp.ui.home.CalendarView
import androidx.compose.runtime.getValue
import com.example.customerapp.data.model.TaskViewModel

@Composable
fun TaskCalendarScreen(viewModel: TaskViewModel) {

    LaunchedEffect(Unit) {
        viewModel.fetchTasks()
    }
    val taskList by viewModel.tasks.collectAsState()

    // Nhóm công việc theo ngày
    val tasksByDate = remember(taskList) {
        taskList.groupBy { it.date }
    }

    CalendarView(tasksByDate)
}
