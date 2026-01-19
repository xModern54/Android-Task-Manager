package com.example.taskmanager.domain.model

data class ProcessInfo(
    val pid: Int,
    val name: String,
    val packageName: String,
    val cpuUsage: Double, // 0.0 to 100.0
    val ramUsage: Long, // In bytes
    val icon: Any? = null // Placeholder for Drawable or ImageVector
)
