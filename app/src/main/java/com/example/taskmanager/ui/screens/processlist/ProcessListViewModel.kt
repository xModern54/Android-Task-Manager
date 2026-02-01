package com.example.taskmanager.ui.screens.processlist

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanager.domain.cache.AppInfoCache
import com.example.taskmanager.service.RootConnectionManager
import com.example.taskmanager.ui.screens.processdetail.ProcessDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOption {
    CPU, RAM, NAME, PRIORITY
}

data class KillCandidate(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    var isSelected: Boolean = true
)

class ProcessListViewModel(application: Application) : AndroidViewModel(application) {

    // Raw list from C++
    private val _rawList = MutableStateFlow<List<RawProcessInfo>>(emptyList())
    
    // Sort Option State
    private val _sortOption = MutableStateFlow(SortOption.RAM)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    // Search Query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Global Stats
    private val _totalCpuUsage = MutableStateFlow(0.0)
    val totalCpuUsage: StateFlow<Double> = _totalCpuUsage.asStateFlow()

    private val _totalRamUsed = MutableStateFlow(0L)
    val totalRamUsed: StateFlow<Long> = _totalRamUsed.asStateFlow()

    private val _totalRamSize = MutableStateFlow(1L)
    val totalRamSize: StateFlow<Long> = _totalRamSize.asStateFlow()
    
    // Final UI List (Cached + Raw + Sorted)
    private val _processList = MutableStateFlow<List<ProcessUiModel>>(emptyList())
    val processList: StateFlow<List<ProcessUiModel>> = _processList.asStateFlow()

    // Process Detail State
    private val _selectedProcessDetails = MutableStateFlow<ProcessDetail?>(null)
    val selectedProcessDetails: StateFlow<ProcessDetail?> = _selectedProcessDetails.asStateFlow()

    // Kill Candidate State
    private val _killCandidates = MutableStateFlow<List<KillCandidate>>(emptyList())
    val killCandidates: StateFlow<List<KillCandidate>> = _killCandidates.asStateFlow()

    private val _isReviewingKill = MutableStateFlow(false)
    val isReviewingKill: StateFlow<Boolean> = _isReviewingKill.asStateFlow()

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

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openApp(packageName: String, label: String) {
        val pm = getApplication<Application>().packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
            Toast.makeText(getApplication(), "Opening $label", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(getApplication(), "Cannot open $label", Toast.LENGTH_SHORT).show()
        }
    }

    fun forceStopApp(packageName: String, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $packageName"))
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "$label Stopped", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("TaskManager", "Error force stopping $packageName", e)
            }
        }
    }

    fun killProcess(pid: Int, label: String, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = rootManager.sendSignal(pid, 9) // 9 = SIGKILL
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(getApplication(), "$label Killed", Toast.LENGTH_SHORT).show()
                    onSuccess()
                } else {
                    Toast.makeText(getApplication(), "Failed to kill $label", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Safe Kill Logic
    fun scanForCandidates() {
        viewModelScope.launch(Dispatchers.IO) {
            val packages = rootManager.getKillCandidates()
            val candidates = packages.map { pkg ->
                val uiState = appCache.getAppUiState(pkg, this)
                KillCandidate(pkg, uiState.label, uiState.icon, true)
            }
            _killCandidates.value = candidates
            _isReviewingKill.value = true
        }
    }

    fun executeKill(candidates: List<KillCandidate>) {
        cancelKillReview()

        viewModelScope.launch(Dispatchers.IO) {
            val packagesToKill = candidates.filter { it.isSelected }.map { it.packageName }
            if (packagesToKill.isEmpty()) return@launch

            val freedBytes = rootManager.executeKillTransaction(packagesToKill)
            val freedMb = freedBytes / (1024 * 1024)
            
            withContext(Dispatchers.Main) {
                val msg = if (freedMb > 0) "Optimized! Freed $freedMb MB." else "Optimized!"
                Toast.makeText(getApplication(), msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun cancelKillReview() {
        _isReviewingKill.value = false
        _killCandidates.value = emptyList()
    }

    fun toggleCandidate(pkg: String) {
        _killCandidates.value = _killCandidates.value.map {
            if (it.packageName == pkg) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun fetchProcessDetails(pid: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            // Use the new Deep Snapshot API
            val data = rootManager.getProcessDeepSnapshot(pid)
            if (data != null) {
                val rawDetail = parseProcessDetail(data)
                val uiState = appCache.getAppUiState(rawDetail.name, this)
                
                _selectedProcessDetails.value = rawDetail.copy(
                    label = uiState.label,
                    icon = uiState.icon
                )
            }
        }
    }

    fun clearSelectedProcess() {
        _selectedProcessDetails.value = null
    }

    private fun parseProcessDetail(data: String): ProcessDetail {
        val sections = data.split("\n")
        val overviewMap = mutableMapOf<String, String>()
        val statsMap = mutableMapOf<String, String>()
        var modulesList = emptyList<String>()
        var threadsList = emptyList<String>()

        for (section in sections) {
            if (section.startsWith("OVERVIEW:")) {
                val content = section.substringAfter("OVERVIEW:")
                content.split("|").forEach { pair ->
                    val parts = pair.split("=", limit = 2)
                    if (parts.size == 2) overviewMap[parts[0]] = parts[1]
                }
            } else if (section.startsWith("STATS:")) {
                val content = section.substringAfter("STATS:")
                content.split("|").forEach { pair ->
                    val parts = pair.split("=", limit = 2)
                    if (parts.size == 2) statsMap[parts[0]] = parts[1]
                }
            } else if (section.startsWith("MODULES:")) {
                val content = section.substringAfter("MODULES:")
                if (content.isNotEmpty()) {
                    modulesList = content.split(";")
                }
            } else if (section.startsWith("THREADS:")) {
                val content = section.substringAfter("THREADS:")
                if (content.isNotEmpty()) {
                    threadsList = content.split("|")
                }
            }
        }

        return ProcessDetail(
            name = overviewMap["Name"] ?: "",
            pid = overviewMap["PID"]?.toIntOrNull() ?: 0,
            ppid = overviewMap["PPID"] ?: "",
            user = overviewMap["User"] ?: "",
            state = overviewMap["State"] ?: "",
            threads = threadsList.size.toString(), // Count from list or use overviewMap["Threads"]
            nice = overviewMap["Nice"] ?: "",
            priority = overviewMap["Priority"] ?: "",
            oomScore = overviewMap["OomScore"] ?: "",
            elapsedTime = overviewMap["ElapsedTime"] ?: "",
            exePath = overviewMap["ExePath"] ?: "",
            
            voluntaryCtxSwitches = statsMap["VoluntaryCtxSwitches"] ?: "0",
            nonVoluntaryCtxSwitches = statsMap["NonVoluntaryCtxSwitches"] ?: "0",
            minorPageFaults = statsMap["MinorPageFaults"] ?: "0",
            majorPageFaults = statsMap["MajorPageFaults"] ?: "0",
            modules = modulesList,
            threadList = threadsList
        )
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
            combine(_rawList, appCache.updates, _sortOption, _searchQuery) { raw, _, sort, query ->
                val uiList = raw.map { p ->
                    val uiState = appCache.getAppUiState(p.name, viewModelScope)
                    ProcessUiModel(
                        pid = p.pid,
                        rawName = p.name,
                        label = uiState.label,
                        icon = uiState.icon,
                        isSystem = uiState.isSystem,
                        cpuUsage = p.cpuUsage,
                        ramUsage = p.ramUsage,
                        nice = p.nice
                    )
                }.filter { 
                    query.isEmpty() || 
                    it.label.contains(query, ignoreCase = true) || 
                    it.rawName.contains(query, ignoreCase = true)
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
                    SortOption.PRIORITY -> uiList.sortedBy { it.nice }
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
        val ramUsage: Long,
        val nice: Int
    )

    private fun parseProcessList(data: String): List<RawProcessInfo> {
        val list = mutableListOf<RawProcessInfo>()
        if (data.isEmpty()) return list

        val lines = data.split("\n")
        
        var startIndex = 0
        if (lines.isNotEmpty() && lines[0].startsWith("HEAD|")) {
            try {
                val parts = lines[0].split("|")
                if (parts.size >= 4) {
                    _totalCpuUsage.value = parts[1].toDoubleOrNull() ?: 0.0
                    _totalRamUsed.value = parts[2].toLongOrNull() ?: 0L
                    _totalRamSize.value = parts[3].toLongOrNull() ?: 1L
                }
                startIndex = 1 
            } catch (e: Exception) {
                Log.e("TaskManager", "Error parsing HEAD", e)
            }
        }

        for (i in startIndex until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            
            val parts = line.split("|")
            if (parts.size >= 4) {
                try {
                    val pid = parts[0].toInt()
                    val name = parts[1]
                    val ram = parts[2].toLongOrNull() ?: 0L
                    val cpu = parts[3].toDoubleOrNull() ?: 0.0
                    val nice = if (parts.size >= 5) parts[4].toIntOrNull() ?: 0 else 0
                    
                    list.add(RawProcessInfo(
                        pid = pid,
                        name = name,
                        cpuUsage = cpu,
                        ramUsage = ram,
                        nice = nice
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
