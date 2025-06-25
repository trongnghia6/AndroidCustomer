package com.example.testappcc.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testappcc.data.model.Task
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import java.time.YearMonth
import java.time.LocalDate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.testappcc.screen.TaskListByDate

@Composable
fun CalendarView(tasksByDate: Map<LocalDate, List<Task>>) {
    val today = remember { YearMonth.now() }
    val startMonth = remember { today.minusMonths(6) }
    val endMonth = remember { today.plusMonths(6) }

    val calendarState = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = today,
        firstDayOfWeek = firstDayOfWeekFromLocale()
    )

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    Column {
        // Lịch nằm trên
        HorizontalCalendar(
            state = calendarState,
            dayContent = { day ->
                if (day.position == DayPosition.MonthDate) {
                    val taskList = tasksByDate[day.date]
                    val isSelected = day.date == selectedDate

                    Column(
                        modifier = Modifier
                            .padding(4.dp)
                            .background(
                                when {
                                    isSelected -> Color(0xFF81D4FA)
                                    !taskList.isNullOrEmpty() -> Color(0xFFE0F7FA)
                                    else -> Color.Transparent
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                            .clickable {
                                selectedDate = day.date
                            }
                    ) {
                        Text(text = day.date.dayOfMonth.toString())
                        if (!taskList.isNullOrEmpty()) {
                            Text(
                                text = "${taskList.size} công việc",
                                fontSize = 10.sp,
                                color = Color.Blue
                            )
                        }
                    }
                }
            }
        )

        // Danh sách công việc của ngày được chọn
        TaskListByDate(selectedDate, tasksByDate[selectedDate].orEmpty())
    }
}

