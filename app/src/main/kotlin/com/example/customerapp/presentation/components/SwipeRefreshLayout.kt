package com.example.customerapp.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SwipeRefreshLayout(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var offsetY by remember { mutableStateOf(0f) }
    val triggerDistance = with(density) { 80.dp.toPx() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (offsetY > triggerDistance && !isRefreshing) {
                            onRefresh()
                        }
                        offsetY = 0f
                    }
                ) { _, dragAmount ->
                    if (dragAmount.y > 0 && offsetY >= 0) {
                        offsetY = (offsetY + dragAmount.y * 0.5f).coerceAtMost(triggerDistance * 1.5f)
                    } else if (dragAmount.y < 0 && offsetY > 0) {
                        offsetY = (offsetY + dragAmount.y).coerceAtLeast(0f)
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier.offset { 
                IntOffset(0, if (isRefreshing) 0 else offsetY.roundToInt()) 
            }
        ) {
            content()
        }
        
        if (isRefreshing || offsetY > 0) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { 
                        IntOffset(0, (offsetY * 0.8f).roundToInt()) 
                    }
                    .size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        }
    }
    
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            offsetY = 0f
        }
    }
}