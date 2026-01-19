package com.example.taskmanager.ui.screens.processlist

import androidx.lifecycle.ViewModel
import com.example.taskmanager.domain.model.ProcessInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class ProcessListViewModel : ViewModel() {

    private val _processList = MutableStateFlow<List<ProcessInfo>>(emptyList())
    val processList: StateFlow<List<ProcessInfo>> = _processList.asStateFlow()

    init {
        generateMockData()
    }

    private fun generateMockData() {
        val processNames = listOf(
            "System UI" to "com.android.systemui",
            "Kernel" to "root",
            "Chrome" to "com.android.chrome",
            "Telegram" to "org.telegram.messenger",
            "Spotify" to "com.spotify.music",
            "Settings" to "com.android.settings",
            "GMS" to "com.google.android.gms",
            "Launcher" to "com.android.launcher3",
            "Camera" to "com.android.camera2",
            "Phone" to "com.android.dialer"
        )

        val mockList = MutableList(50) { index ->
            val (name, pkg) = processNames.random()
            // Randomize slightly
            val uniquePid = 1000 + index
            
            // Generate realistic-ish values
            val isHighLoad = Random.nextBoolean() && Random.nextBoolean() // Occasional high load
            val cpu = if (isHighLoad) Random.nextDouble(10.0, 99.0) else Random.nextDouble(0.0, 5.0)
            
            val ramBase = 1024 * 1024 * 50L // 50MB min
            val ramMax = 1024 * 1024 * 1024 * 2L // 2GB max
            val ram = Random.nextLong(ramBase, ramMax)

            ProcessInfo(
                pid = uniquePid,
                name = "$name ${index + 1}", // Append index to make unique names visually
                packageName = pkg,
                cpuUsage = cpu,
                ramUsage = ram
            )
        }
        
        // Sort by CPU usage descending to show "heatmap" logic at top initially
        _processList.value = mockList.sortedByDescending { it.cpuUsage }
    }
}
