package com.example.taskmanager.ui.components.process

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskmanager.domain.model.ProcessInfo
import com.example.taskmanager.ui.theme.DividerGrey
import com.example.taskmanager.ui.theme.HeatmapBaseColor
import com.example.taskmanager.ui.theme.TextGrey
import com.example.taskmanager.ui.theme.TextWhite
import java.util.Locale

@Composable
fun ProcessRowItem(process: ProcessInfo) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Column 1: Name and Package (No Heatmap)
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .padding(start = 16.dp, end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = process.name,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = process.packageName,
                    color = TextGrey,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Column 2: CPU Heatmap
            // Map 0..100% to alpha 0.15..0.90
            val cpuAlpha = calculateAlpha(process.cpuUsage, 100.0)
            
            Box(
                modifier = Modifier
                    .weight(0.2f)
                    .fillMaxHeight()
                    .background(HeatmapBaseColor.copy(alpha = cpuAlpha))
                    .padding(end = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f%%", process.cpuUsage),
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End
                )
            }

            // Column 3: RAM Heatmap
            // Assuming scaling against a 4GB visualization cap for now
            // 4GB = 4 * 1024 * 1024 * 1024 bytes
            val ramMax = 4L * 1024 * 1024 * 1024
            val ramAlpha = calculateAlpha(process.ramUsage.toDouble(), ramMax.toDouble())

            Box(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .background(HeatmapBaseColor.copy(alpha = ramAlpha))
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = formatBytes(process.ramUsage),
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.End
                )
            }
        }
        Divider(color = DividerGrey, thickness = 1.dp)
    }
}

private fun calculateAlpha(value: Double, max: Double): Float {
    val minAlpha = 0.15f
    val maxAlpha = 0.90f
    
    val ratio = (value / max).coerceIn(0.0, 1.0).toFloat()
    return minAlpha + (ratio * (maxAlpha - minAlpha))
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}
