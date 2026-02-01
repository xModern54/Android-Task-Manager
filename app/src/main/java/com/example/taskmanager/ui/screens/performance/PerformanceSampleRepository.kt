package com.example.taskmanager.ui.screens.performance

import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

object PerformanceSampleRepository {
    fun load(): List<PerformanceCategory> {
        return listOf(
            cpuCategory(),
            memoryCategory(),
            diskCategory("disk0", "Disk 0 (C:)", "0% SSD", Color(0xFF7ED957)),
            diskCategory("disk1", "Disk 1 (D:)", "0% SSD", Color(0xFF7ED957)),
            ethernetCategory(),
            gpuCategory()
        )
    }

    private fun cpuCategory(): PerformanceCategory {
        val color = Color(0xFF5BC7D8)
        return PerformanceCategory(
            id = "cpu",
            displayName = "CPU",
            summaryText = "19% 4.55 GHz",
            seriesColor = color,
            timeSeries = series(seed = 1, base = 18f, variation = 20f, spike = 25f),
            hardwareName = "Qualcomm Snapdragon 8 Gen 2",
            hardwareSubtitle = "8-core",
            headerRightPrimary = "4.55 GHz",
            headerRightSecondary = "8 Cores",
            chartLabel = "CPU usage",
            leftStats = listOf(
                StatItem("Utilization", "19%"),
                StatItem("Speed", "4.55 GHz"),
                StatItem("Processes", "213")
            ),
            rightStats = listOf(
                StatItem("Threads", "3256"),
                StatItem("Handles", "104,521"),
                StatItem("Up time", "0:18:24:10")
            ),
            metaStats = listOf(
                StatItem("Base speed:", "3.20 GHz"),
                StatItem("Sockets:", "1"),
                StatItem("Logical processors:", "8"),
                StatItem("Virtualization:", "Enabled")
            )
        )
    }

    private fun memoryCategory(): PerformanceCategory {
        val color = Color(0xFF6E7CFF)
        return PerformanceCategory(
            id = "memory",
            displayName = "Memory",
            summaryText = "5.1/12.0 GB (43%)",
            seriesColor = color,
            timeSeries = memorySeries(seed = 2),
            hardwareName = "LPDDR5X",
            headerRightPrimary = "32.0 GB",
            headerRightSecondary = "31.9 GB",
            chartLabel = "Memory usage",
            compositionLabel = "Memory composition",
            compositionSegments = listOf(
                0.28f to color.copy(alpha = 0.38f),
                0.06f to color.copy(alpha = 0.2f)
            ),
            leftStats = listOf(
                StatItem("In use (Compressed)", "3.2 GB (0 MB)"),
                StatItem("Committed", "5.1/33.9 GB"),
                StatItem("Paged pool", "372 MB")
            ),
            rightStats = listOf(
                StatItem("Available", "28.7 GB"),
                StatItem("Cached", "5.9 GB"),
                StatItem("Non-paged pool", "214 MB")
            ),
            metaStats = listOf(
                StatItem("Speed:", "4800 MT/s"),
                StatItem("Slots used:", "2 of 2"),
                StatItem("Form factor:", "SODIMM"),
                StatItem("Hardware reserved:", "128 MB")
            )
        )
    }

    private fun memorySeries(seed: Int): List<Float> {
        val rand = Random(seed)
        val result = ArrayList<Float>(60)
        var value = 43f
        for (i in 0 until 60) {
            if (rand.nextFloat() < 0.22f) {
                value += rand.nextInt(-4, 5)
            }
            if (rand.nextFloat() < 0.06f) {
                value += rand.nextInt(-8, 9)
            }
            value = value.coerceIn(25f, 60f)
            result.add(value)
        }
        return result
    }

    private fun diskCategory(id: String, name: String, summary: String, color: Color): PerformanceCategory {
        return PerformanceCategory(
            id = id,
            displayName = name,
            summaryText = summary,
            seriesColor = color,
            timeSeries = series(seed = id.hashCode(), base = 2f, variation = 6f, spike = 8f),
            hardwareName = "UFS 3.1 Internal",
            headerRightPrimary = "SSD",
            headerRightSecondary = "0%",
            chartLabel = "Disk activity",
            leftStats = listOf(
                StatItem("Active time", "0%"),
                StatItem("Average response time", "0.0 ms"),
                StatItem("Read speed", "0 KB/s")
            ),
            rightStats = listOf(
                StatItem("Write speed", "0 KB/s"),
                StatItem("Capacity", "512 GB"),
                StatItem("Formatted", "512 GB")
            ),
            metaStats = listOf(
                StatItem("System disk:", "Yes"),
                StatItem("Page file:", "Yes"),
                StatItem("Type:", "SSD")
            )
        )
    }

    private fun ethernetCategory(): PerformanceCategory {
        val color = Color(0xFFE07AC1)
        return PerformanceCategory(
            id = "ethernet",
            displayName = "Ethernet",
            summaryText = "S: 0 R: 0 Kbps",
            seriesColor = color,
            timeSeries = series(seed = 3, base = 1f, variation = 4f, spike = 10f),
            hardwareName = "Wi‑Fi 6",
            hardwareSubtitle = "wlan0",
            headerRightPrimary = "Wi‑Fi",
            headerRightSecondary = "0 Kbps",
            chartLabel = "Throughput",
            leftStats = listOf(
                StatItem("Send", "0 Kbps"),
                StatItem("Receive", "0 Kbps"),
                StatItem("Packets", "0")
            ),
            rightStats = listOf(
                StatItem("Adapter name", "wlan0"),
                StatItem("SSID", "Mock‑Network"),
                StatItem("IPv4", "192.168.1.103")
            )
        )
    }

    private fun gpuCategory(): PerformanceCategory {
        val color = Color(0xFFB59AF0)
        return PerformanceCategory(
            id = "gpu",
            displayName = "GPU 0",
            summaryText = "0% (49°C)",
            seriesColor = color,
            timeSeries = series(seed = 4, base = 6f, variation = 8f, spike = 12f),
            hardwareName = "Adreno 740",
            headerRightPrimary = "49°C",
            headerRightSecondary = "0%",
            chartLabel = "GPU usage",
            leftStats = listOf(
                StatItem("3D", "0%"),
                StatItem("Copy", "0%"),
                StatItem("Video Decode", "0%")
            ),
            rightStats = listOf(
                StatItem("Dedicated GPU memory", "0.1/4.0 GB"),
                StatItem("Shared GPU memory", "0.1/16.0 GB"),
                StatItem("Driver version", "1.2.0")
            )
        )
    }

    private fun series(seed: Int, base: Float, variation: Float, spike: Float): List<Float> {
        val rand = Random(seed)
        return List(60) { i ->
            val t = i / 59f
            val wave = sin(t * 2f * PI.toFloat() * 1.4f) * (variation * 0.45f)
            val noise = (rand.nextFloat() - 0.5f) * (variation * 0.25f)
            val ramp = if (i > 45) ((i - 45) / 15f) * spike else 0f
            (base + wave + noise + ramp).coerceIn(0f, 100f)
        }
    }
}
