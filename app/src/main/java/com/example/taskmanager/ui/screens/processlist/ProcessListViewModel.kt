package com.example.taskmanager.ui.screens.processlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanager.domain.model.NativeProcessInfo
import com.example.taskmanager.domain.model.ProcessInfo
import com.example.taskmanager.service.RootConnectionManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ProcessListViewModel(application: Application) : AndroidViewModel(application) {

    private val _processList = MutableStateFlow<List<ProcessInfo>>(emptyList())
    val processList: StateFlow<List<ProcessInfo>> = _processList.asStateFlow()

    private val rootManager = RootConnectionManager(application)
    private val gson = Gson()

    init {
        rootManager.bind()
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val json = rootManager.getProcessListJson()
                if (json != null) {
                    try {
                        val type = object : TypeToken<List<NativeProcessInfo>>() {}.type
                        val nativeList: List<NativeProcessInfo> = gson.fromJson(json, type)
                        
                        val uiList = nativeList.map { native ->
                            ProcessInfo(
                                pid = native.pid,
                                name = native.name,
                                packageName = "", // Not parsed yet
                                cpuUsage = 0.0, // Not implemented yet
                                ramUsage = native.ramUsage
                            )
                        }
                        
                        _processList.value = uiList.sortedBy { it.pid }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                delay(2000) // Poll every 2 seconds
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        rootManager.unbind()
    }
}
