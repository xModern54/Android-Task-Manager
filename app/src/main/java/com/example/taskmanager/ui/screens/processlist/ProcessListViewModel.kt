package com.example.taskmanager.ui.screens.processlist

import android.app.Application
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanager.domain.cache.AppInfoCache
import com.example.taskmanager.service.RootConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SortOption {
    CPU, RAM, NAME
}

class ProcessListViewModel(application: Application) : AndroidViewModel(application) {

    // Raw list from C++
    private val _rawList = MutableStateFlow<List<RawProcessInfo>>(emptyList())
    
    // Sort Option State
    private val _sortOption = MutableStateFlow(SortOption.RAM)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()
    
    // Final UI List (Cached + Raw + Sorted)
    private val _processList = MutableStateFlow<List<ProcessUiModel>>(emptyList())
    val processList: StateFlow<List<ProcessUiModel>> = _processList.asStateFlow()

    private val rootManager = RootConnectionManager(application)
    private val appCache = AppInfoCache(application)

    init {
        Log.d("TaskManager", "ViewModel init")
        rootManager.bind()
        startPolling()
        observeData()
    }
    
    fun updateSortOption(option: SortOption) {
        _sortOption.value = option
    }

    private fun startPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val rawData = rootManager.getProcessList()
                if (rawData != null) {
                    try {
                        val parsed = parseProcessList(rawData)
                        _rawList.emit(parsed)
                    } catch (e: Exception) {
                        Log.e("TaskManager", "Error parsing process list", e)
                    }
                }
                delay(500)
            }
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(_rawList, appCache.updates, _sortOption) { raw, _, sort ->
                val uiList = raw.map { p ->
                    val uiState = appCache.getAppUiState(p.name, viewModelScope)
                    ProcessUiModel(
                        pid = p.pid,
                        rawName = p.name,
                        label = uiState.label,
                        icon = uiState.icon,
                        isSystem = uiState.isSystem,
                        cpuUsage = p.cpuUsage,
                        ramUsage = p.ramUsage
                    )
                }
                
                when (sort) {
                    SortOption.CPU -> uiList.sortedWith(
                        compareByDescending<ProcessUiModel> { it.cpuUsage }
                            .thenByDescending { it.ramUsage }
                    )
                    SortOption.RAM -> uiList.sortedWith(
                        compareByDescending<ProcessUiModel> { it.ramUsage }
                            .thenByDescending { it.cpuUsage }
                    )
                    SortOption.NAME -> uiList.sortedBy { it.label.lowercase() }
                }
            }.collect {
                _processList.value = it
            }
        }
    }

    private data class RawProcessInfo(
        val pid: Int,
        val name: String,
        val cpuUsage: Double,
        val ramUsage: Long
    )

    private fun parseProcessList(data: String): List<RawProcessInfo> {
        val list = mutableListOf<RawProcessInfo>()
        if (data.isEmpty()) return list

        val lines = data.split("\n")
        for (line in lines) {
            if (line.isBlank()) continue
            
            val parts = line.split("|")
            if (parts.size >= 4) {
                try {
                    val pid = parts[0].toInt()
                    val name = parts[1]
                    val ram = parts[2].toLongOrNull() ?: 0L
                    val cpu = parts[3].toDoubleOrNull() ?: 0.0
                    
                    list.add(RawProcessInfo(
                        pid = pid,
                        name = name,
                        cpuUsage = cpu,
                        ramUsage = ram
                    ))
                } catch (e: NumberFormatException) {
                    // Ignore
                }
            }
        }
        return list
    }

    override fun onCleared() {
        super.onCleared()
        rootManager.unbind()
    }
}