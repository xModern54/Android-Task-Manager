package com.xmodern.taskmgmt.ui.screens.processdetail

import android.graphics.drawable.Drawable

data class ProcessDetail(
    val name: String = "",
    val label: String = "",
    val icon: Drawable? = null,
    val pid: Int = 0,
    val ppid: String = "",
    val user: String = "",
    val state: String = "",
    val threads: String = "", // Total count
    val nice: String = "",
    val priority: String = "",
    val oomScore: String = "",
    val elapsedTime: String = "",
    val exePath: String = "",
    // Deep Stats
    val voluntaryCtxSwitches: String = "",
    val nonVoluntaryCtxSwitches: String = "",
    val minorPageFaults: String = "",
    val majorPageFaults: String = "",
    val modules: List<String> = emptyList(),
    val threadList: List<String> = emptyList() // Format: tid:name
)
