package com.example.taskmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskmanager.ui.screens.performance.PerformanceScreen
import com.example.taskmanager.ui.screens.performance.PerformanceSampleRepository
import com.example.taskmanager.ui.screens.performance.PerformanceViewModel

class PerformanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PerformanceRoot(onBack = { finish() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerformanceRoot(onBack: () -> Unit) {
    val background = Color(0xFF111111)
    val viewModel: PerformanceViewModel = viewModel()
    val cpuSnapshot by viewModel.cpuSnapshot.collectAsState()
    val cpuSeries by viewModel.cpuSeries.collectAsState()
    val gpuSnapshot by viewModel.gpuSnapshot.collectAsState()
    val gpuSeries by viewModel.gpuSeries.collectAsState()
    val memorySnapshot by viewModel.memorySnapshot.collectAsState()
    val memorySeries by viewModel.memorySeries.collectAsState()
    val diskSnapshot by viewModel.diskSnapshot.collectAsState()
    val diskSeries by viewModel.diskSeries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance", color = Color(0xFFEDEDED)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFEDEDED)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
            )
        },
        containerColor = background
    ) { padding ->
        PerformanceScreen(
            categories = PerformanceSampleRepository.load(),
            contentPadding = padding,
            cpuSnapshot = cpuSnapshot,
            cpuSeries = cpuSeries,
            gpuSnapshot = gpuSnapshot,
            gpuSeries = gpuSeries,
            memorySnapshot = memorySnapshot,
            memorySeries = memorySeries,
            diskSnapshot = diskSnapshot,
            diskSeries = diskSeries,
            onCpuPoll = { viewModel.refreshCpuSnapshot() },
            onGpuPoll = { viewModel.refreshGpuSnapshot() },
            onMemoryPoll = { viewModel.refreshMemorySnapshot() },
            onDiskPoll = { viewModel.refreshDiskSnapshot() }
        )
    }
}
