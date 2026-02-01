package com.xmodern.taskmgmt.ui.screens.performance

import androidx.compose.ui.graphics.Color


data class StatItem(
    val label: String,
    val value: String
)

data class PerformanceCategory(
    val id: String,
    val displayName: String,
    val summaryText: String,
    val seriesColor: Color,
    val timeSeries: List<Float>,
    val hardwareName: String,
    val hardwareSubtitle: String? = null,
    val headerRightPrimary: String,
    val headerRightSecondary: String,
    val chartLabel: String,
    val compositionLabel: String? = null,
    val compositionSegments: List<Pair<Float, Color>> = emptyList(),
    val leftStats: List<StatItem> = emptyList(),
    val rightStats: List<StatItem> = emptyList(),
    val metaStats: List<StatItem> = emptyList()
)
