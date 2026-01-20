package com.example.taskmanager.ui.screens.processlist

import android.graphics.drawable.Drawable

data class ProcessUiModel(
    val pid: Int,
    val rawName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val cpuUsage: Double,
    val ramUsage: Long
)
