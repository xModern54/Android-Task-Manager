package com.example.taskmanager.ui.screens.performance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val Background = Color(0xFF111111)
private val CardBackground = Color(0xFF1A1A1A)
private val DividerColor = Color(0xFF2A2A2A)
private val PrimaryText = Color(0xFFEDEDED)
private val SecondaryText = Color(0xFFA7A7A7)

@Composable
fun PerformanceScreen(
    categories: List<PerformanceCategory>,
    contentPadding: PaddingValues,
    cpuSnapshot: CpuSnapshot?,
    cpuSeries: List<Float>,
    gpuSnapshot: GpuSnapshot?,
    gpuSeries: List<Float>,
    memorySnapshot: MemorySnapshot?,
    memorySeries: List<Float>,
    onCpuPoll: () -> Unit,
    onGpuPoll: () -> Unit,
    onMemoryPoll: () -> Unit
) {
    var selectedId by remember { mutableStateOf("memory") }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(selectedId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            if (selectedId == "cpu") {
                while (isActive) {
                    onCpuPoll()
                    delay(1000)
                }
            } else if (selectedId == "gpu") {
                while (isActive) {
                    onGpuPoll()
                    delay(1000)
                }
            } else if (selectedId == "memory") {
                while (isActive) {
                    onMemoryPoll()
                    delay(500)
                }
            }
        }
    }

    val displayCategories = categories.map { category ->
        if (category.id == "cpu" && cpuSnapshot != null) {
            cpuCategoryFromSnapshot(category, cpuSnapshot, cpuSeries)
        } else if (category.id == "gpu" && gpuSnapshot != null) {
            gpuCategoryFromSnapshot(category, gpuSnapshot, gpuSeries)
        } else if (category.id == "memory" && memorySnapshot != null) {
            memoryCategoryFromSnapshot(category, memorySnapshot, memorySeries)
        } else {
            category
        }
    }

    val selected = displayCategories.firstOrNull { it.id == selectedId } ?: displayCategories.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(contentPadding)
    ) {
        CategorySelectorRow(
            categories = displayCategories,
            selectedId = selected.id,
            onSelect = { selectedId = it }
        )

        MainPanel(
            category = selected,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun CategorySelectorRow(
    categories: List<PerformanceCategory>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(categories) { category ->
            val selected = category.id == selectedId
            CategorySelectorItem(
                category = category,
                selected = selected,
                onClick = { onSelect(category.id) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelectorItem(
    category: PerformanceCategory,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) category.seriesColor else DividerColor
    val container = if (selected) CardBackground.copy(alpha = 0.95f) else CardBackground

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(164.dp)
            .height(90.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.25.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = category.displayName,
                    color = PrimaryText,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = category.summaryText,
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            MiniChartBox(
                series = category.timeSeries,
                color = category.seriesColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            )
        }
    }
}

@Composable
private fun MainPanel(category: PerformanceCategory, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Header(category = category)
        Spacer(modifier = Modifier.height(8.dp))
        ChartBlock(category = category)
        Spacer(modifier = Modifier.height(16.dp))

        // TODO: restore Memory composition block later
        if (false) {
            category.compositionLabel?.let {
                Text(
                    text = it,
                    color = SecondaryText,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                CompositionBar(category = category)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        StatsBlock(category = category)
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun Header(category: PerformanceCategory) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = category.displayName,
            color = PrimaryText,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.alignBy(FirstBaseline)
        )

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.alignBy(FirstBaseline)
        ) {
            if (category.id == "memory") {
                Text(
                    text = category.headerRightPrimary,
                    color = PrimaryText,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = category.headerRightSecondary,
                    color = SecondaryText,
                    style = MaterialTheme.typography.labelMedium
                )
            } else {
                Text(
                    text = category.hardwareName,
                    color = PrimaryText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                category.hardwareSubtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        color = SecondaryText,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartBlock(category: PerformanceCategory) {
    Text(
        text = category.chartLabel,
        color = SecondaryText,
        style = MaterialTheme.typography.labelMedium
    )
    Spacer(modifier = Modifier.height(6.dp))

    Surface(
        color = Background,
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        PerformanceChart(
            series = category.timeSeries,
            color = category.seriesColor
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("60 seconds", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        Text("0", color = SecondaryText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PerformanceChart(series: List<Float>, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val gridColor = DividerColor

        val vLines = 6
        val hLines = 4

        for (i in 0..vLines) {
            val x = width * (i / vLines.toFloat())
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
        }

        for (i in 0..hLines) {
            val y = height * (i / hLines.toFloat())
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        if (series.isEmpty()) return@Canvas

        val maxValue = 100f
        val points = series.mapIndexed { index, value ->
            val x = width * (index / (series.size - 1).toFloat())
            val y = height - (value / maxValue) * height
            Offset(x, y)
        }

        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }

        val areaPath = Path().apply {
            moveTo(points.first().x, height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, height)
            close()
        }

        drawPath(areaPath, color.copy(alpha = 0.42f))
        drawPath(linePath, color, style = Stroke(width = 2f, cap = StrokeCap.Round))
    }
}

@Composable
private fun CompositionBar(category: PerformanceCategory) {
    val borderColor = category.seriesColor.copy(alpha = 0.7f)
    val usedTotal = category.compositionSegments.sumOf { it.first.toDouble() }.toFloat().coerceAtMost(1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Background),
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                color = borderColor,
                size = Size(size.width, size.height),
                style = Stroke(width = 2f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            category.compositionSegments.forEachIndexed { index, segment ->
                val weight = segment.first.coerceAtLeast(0f)
                if (weight > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(weight)
                            .background(segment.second)
                    )
                }
                if (index < category.compositionSegments.lastIndex) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(borderColor.copy(alpha = 0.6f))
                    )
                }
            }

            val remaining = (1f - usedTotal).coerceAtLeast(0f)
            if (remaining > 0f) {
                Spacer(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(remaining)
                )
            }
        }
    }
}

@Composable
private fun StatsBlock(category: PerformanceCategory) {
    if (category.id == "cpu") {
        StatsGrid(
            left = category.leftStats,
            right = category.rightStats
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            PrimaryStatsColumn(category.leftStats, modifier = Modifier.weight(1f))
            PrimaryStatsColumn(category.rightStats, modifier = Modifier.weight(1f))
        }
    }

    if (category.metaStats.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        MetaStatsList(category.metaStats)
    }
}

@Composable
private fun PrimaryStatsColumn(items: List<StatItem>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        items.forEach { item ->
            Text(
                text = item.label,
                color = SecondaryText,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = item.value,
                color = PrimaryText,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun StatsGrid(left: List<StatItem>, right: List<StatItem>) {
    val rows = maxOf(left.size, right.size)
    Column(modifier = Modifier.fillMaxWidth()) {
        for (i in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                StatCell(
                    item = left.getOrNull(i),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    item = right.getOrNull(i),
                    modifier = Modifier.weight(1f).padding(top = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatCell(item: StatItem?, modifier: Modifier = Modifier) {
    if (item == null) {
        Spacer(modifier = modifier.height(0.dp))
        return
    }
    Column(modifier = modifier) {
        Text(
            text = item.label,
            color = SecondaryText,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = item.value,
            color = PrimaryText,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun MetaStatsList(items: List<StatItem>) {
    val labelWidth = 96.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        items.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.label,
                            color = SecondaryText,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(labelWidth)
                        )
                        Text(
                            text = item.value,
                            color = PrimaryText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun MiniChartBox(series: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val grid = DividerColor

            val hLines = 2
            val vLines = 2
            for (i in 0..hLines) {
                val y = h * (i / hLines.toFloat())
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            for (i in 0..vLines) {
                val x = w * (i / vLines.toFloat())
                drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
            }

            if (series.isEmpty()) return@Canvas
            val maxValue = 100f
            val points = series.mapIndexed { index, value ->
                val x = w * (index / (series.size - 1).toFloat())
                val y = h - (value / maxValue) * h
                Offset(x, y)
            }

            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }

            val areaPath = Path().apply {
                moveTo(points.first().x, h)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, h)
                close()
            }

            drawPath(areaPath, color.copy(alpha = 0.4f))
            drawPath(linePath, color, style = Stroke(width = 1.5f, cap = StrokeCap.Round))
        }
    }
}

private fun cpuCategoryFromSnapshot(
    base: PerformanceCategory,
    snapshot: CpuSnapshot,
    series: List<Float>
): PerformanceCategory {
    val usageText = formatPercent(snapshot.usagePercent)
    val freqText = formatGHz(snapshot.maxFreqKHz)
    val summary = if (freqText == "—") {
        "$usageText —"
    } else {
        "$usageText $freqText"
    }
    val subtitle = if (snapshot.coresLogical > 0) {
        "${snapshot.coresPhysical}C / ${snapshot.coresLogical}T"
    } else {
        null
    }
    val config = when {
        snapshot.coreLayoutLabeled.isNotBlank() -> snapshot.coreLayoutLabeled
        snapshot.coreLayout.isNotBlank() -> snapshot.coreLayout
        else -> "—"
    }
    val tempText = if (snapshot.cpuTempC >= 0) {
        String.format("%.1f °C", snapshot.cpuTempC)
    } else {
        "—"
    }

    val cpuSeries = if (series.isEmpty()) {
        List(60) { 0f }
    } else {
        series
    }

    return base.copy(
        summaryText = summary,
        hardwareName = if (snapshot.cpuName.isNotBlank()) snapshot.cpuName else "—",
        hardwareSubtitle = subtitle,
        timeSeries = cpuSeries,
        leftStats = listOf(
            StatItem("Utilization", usageText),
            StatItem("Speed", freqText),
            StatItem("Processes", snapshot.processes.toString()),
            StatItem("Up time", formatUptime(snapshot.uptimeSeconds))
        ),
        rightStats = listOf(
            StatItem("Temperature", tempText),
            StatItem("Threads", snapshot.threads.toString()),
            StatItem("Handles", snapshot.handles.toString())
        ),
        metaStats = listOf(
            StatItem("Cores:", snapshot.coresPhysical.toString()),
            StatItem("Logical:", snapshot.coresLogical.toString()),
            StatItem("Configuration:", config)
        )
    )
}

private fun gpuCategoryFromSnapshot(
    base: PerformanceCategory,
    snapshot: GpuSnapshot,
    series: List<Float>
): PerformanceCategory {
    val utilText = if (snapshot.utilPercent >= 0) {
        String.format("%.1f%%", snapshot.utilPercent)
    } else {
        "—"
    }
    val tempText = if (snapshot.tempC >= 0) {
        String.format("%.1f °C", snapshot.tempC)
    } else {
        "—"
    }
    val summary = when {
        utilText != "—" && tempText != "—" -> "$utilText ($tempText)"
        utilText != "—" -> utilText
        tempText != "—" -> tempText
        else -> base.summaryText
    }

    val gpuSeries = if (series.isEmpty()) List(60) { 0f } else series

    val totalMemory = formatBytesGb(snapshot.dedicatedTotalBytes)
    val vulkanVersion = if (snapshot.vulkanApiVersion.isNotBlank()) snapshot.vulkanApiVersion else "—"
    val driver = if (snapshot.vulkanDriverVersion.isNotBlank()) snapshot.vulkanDriverVersion else "—"
    val driverDate = formatDriverDate(snapshot.driverDateIso)

    return base.copy(
        summaryText = summary,
        hardwareName = if (snapshot.gpuName.isNotBlank()) snapshot.gpuName else base.hardwareName,
        timeSeries = gpuSeries,
        leftStats = listOf(
            StatItem("3D", utilText),
            StatItem("Temperature", tempText),
            StatItem("GPU memory", totalMemory)
        ),
        rightStats = listOf(
            StatItem("Vulkan", vulkanVersion),
            StatItem("Driver", driver),
            StatItem("Driver date", driverDate)
        )
    )
}

private fun memoryCategoryFromSnapshot(
    base: PerformanceCategory,
    snapshot: MemorySnapshot,
    series: List<Float>
): PerformanceCategory {
    val totalGb = formatBytesGb(snapshot.totalBytes)
    val usedGb = formatBytesGb(snapshot.usedBytes)
    val percent = if (snapshot.totalBytes > 0) {
        (snapshot.usedBytes.toDouble() / snapshot.totalBytes.toDouble()) * 100.0
    } else {
        0.0
    }
    val summary = if (snapshot.totalBytes > 0) {
        val pct = String.format("%.0f%%", percent)
        "${usedGb}/${totalGb} ($pct)"
    } else {
        base.summaryText
    }

    val chartSeries = if (series.isEmpty()) List(60) { 0f } else series

    val compressedText = if (snapshot.compressedBytes > 0) {
        "${formatBytesGb(snapshot.usedBytes)} (${formatBytesMb(snapshot.compressedBytes)})"
    } else {
        "${formatBytesGb(snapshot.usedBytes)} (0 MB)"
    }

    val committedText = if (snapshot.committedUsedBytes > 0 && snapshot.committedLimitBytes > 0) {
        "${formatBytesGb(snapshot.committedUsedBytes)}/${formatBytesGb(snapshot.committedLimitBytes)}"
    } else {
        "—"
    }

    return base.copy(
        summaryText = summary,
        headerRightPrimary = totalGb,
        headerRightSecondary = formatBytesGb(snapshot.availableBytes),
        timeSeries = chartSeries,
        leftStats = listOf(
            StatItem("In use (Compressed)", compressedText),
            StatItem("Committed", committedText),
            StatItem("Cached", formatBytesGb(snapshot.cachedBytes))
        ),
        rightStats = listOf(
            StatItem("Available", formatBytesGb(snapshot.availableBytes))
        ),
        metaStats = emptyList()
    )
}

private fun formatPercent(value: Double): String {
    return if (value.isNaN()) {
        "—"
    } else {
        String.format("%.1f%%", value)
    }
}

private fun formatGHz(khz: Long): String {
    if (khz <= 0) return "—"
    val ghz = khz / 1_000_000.0
    return String.format("%.2f GHz", ghz)
}

private fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val s = seconds % 60
    val totalMinutes = seconds / 60
    val m = totalMinutes % 60
    val totalHours = totalMinutes / 60
    val h = totalHours % 24
    val d = totalHours / 24
    return if (d > 0) {
        String.format("%d:%02d:%02d:%02d", d, h, m, s)
    } else {
        String.format("%d:%02d:%02d", h, m, s)
    }
}

private fun formatBytesGb(bytes: Long): String {
    if (bytes <= 0) return "—"
    val gb = bytes / 1_073_741_824.0
    return String.format("%.1f GB", gb)
}

private fun formatBytesMb(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / 1_048_576.0
    return String.format("%.0f MB", mb)
}

private fun formatDriverDate(iso: String): String {
    if (iso.length != 10 || iso[4] != '-' || iso[7] != '-') return "—"
    val year = iso.substring(0, 4)
    val month = iso.substring(5, 7)
    val day = iso.substring(8, 10)
    return "$day.$month.$year"
}
