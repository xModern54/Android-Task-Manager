package com.example.taskmanager.ui.components.process

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskmanager.ui.screens.processlist.ProcessUiModel
import com.example.taskmanager.ui.theme.DividerGrey
import com.example.taskmanager.ui.theme.TextGrey
import com.example.taskmanager.ui.theme.TextWhite
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import java.util.Locale

// Windows-style Deep Red
private val HeatmapBaseColor = Color(0xFFD32F2F)

private fun calculateAlpha(value: Double, max: Double): Float {
    return (value / max).toFloat().coerceIn(0.0f, 1.0f)
}

@Composable
fun ProcessRowItem(process: ProcessUiModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp) // Fixed height for proper filling
                .background(MaterialTheme.colorScheme.background),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // --- LEFT: ICON & NAMES (Weight 1f) ---
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.DarkGray)
                ) {
                    if (process.icon != null) {
                        Image(
                            painter = rememberDrawablePainter(drawable = process.icon),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Labels
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = process.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (process.isSystem) "System Process" else process.rawName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // --- RIGHT: METRICS (Fixed Widths) ---
            
            // 1. CPU Column
            val cpuAlpha = calculateAlpha(process.cpuUsage, 100.0)
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .fillMaxHeight() // Fills the row top-to-bottom
                    .background(HeatmapBaseColor.copy(alpha = cpuAlpha)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f%%", process.cpuUsage),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // 2. RAM Column
            // Visual cap: 1 GB (1073741824 bytes)
            val ramAlpha = calculateAlpha(process.ramUsage.toDouble(), 1073741824.0) 
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .fillMaxHeight()
                    .background(HeatmapBaseColor.copy(alpha = ramAlpha)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = formatBytes(process.ramUsage),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        Divider(color = DividerGrey, thickness = 1.dp)
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f G", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.0f M", mb)
        else -> String.format(Locale.US, "%.0f K", kb)
    }
}