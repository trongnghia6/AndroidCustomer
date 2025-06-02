package com.example.testappcc.screen

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testappcc.data.model.Task
import java.time.LocalDate
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight

@Composable
fun TaskListByDate(date: LocalDate, tasks: List<Task>) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Công việc ngày ${date.dayOfMonth}/${date.monthValue}/${date.year}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        if (tasks.isEmpty()) {
            Text(text = "Không có công việc", fontSize = 14.sp, color = Color.Gray)
        } else {
            LazyColumn {
                items(tasks) { task ->
                    Column(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(text = task.typeService, fontSize = 16.sp)
                        task.time?.let {
                            Text(text = it, fontSize = 14.sp, color = Color.DarkGray)
                        }
                        task.time?.let {
                            Text(text = "Giờ: $it", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
