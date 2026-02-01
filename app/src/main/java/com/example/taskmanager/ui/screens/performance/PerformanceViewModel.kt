package com.example.taskmanager.ui.screens.performance

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanager.service.RootConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject


data class CpuSnapshot(
    val cpuName: String,
    val coresPhysical: Int,
    val coresLogical: Int,
    val coreLayout: String,
    val coreLayoutLabeled: String,
    val cpuTempC: Double,
    val cpuTempSource: String,
    val cpuTempRaw: Long,
    val cpuTempCandidates: String,
    val cpuTempUnitAssumption: String,
    val usagePercent: Double,
    val maxFreqKHz: Long,
    val processes: Int,
    val threads: Int,
    val handles: Long,
    val uptimeSeconds: Long
)

data class GpuSnapshot(
    val gpuName: String,
    val utilPercent: Double,
    val tempC: Double,
    val vulkanApiVersion: String,
    val vulkanDriverVersion: String,
    val dedicatedBudgetBytes: Long,
    val dedicatedUsedBytes: Long,
    val sharedBudgetBytes: Long,
    val sharedUsedBytes: Long,
    val dedicatedTotalBytes: Long,
    val sharedTotalBytes: Long,
    val hasMemoryBudget: Boolean,
    val error: String
)

class PerformanceViewModel(application: Application) : AndroidViewModel(application) {
    private val rootManager = RootConnectionManager(application)
    private var tempLogCounter = 0

    private val _cpuSnapshot = MutableStateFlow<CpuSnapshot?>(null)
    val cpuSnapshot: StateFlow<CpuSnapshot?> = _cpuSnapshot.asStateFlow()

    private val _cpuSeries = MutableStateFlow<List<Float>>(emptyList())
    val cpuSeries: StateFlow<List<Float>> = _cpuSeries.asStateFlow()

    private val _gpuSnapshot = MutableStateFlow<GpuSnapshot?>(null)
    val gpuSnapshot: StateFlow<GpuSnapshot?> = _gpuSnapshot.asStateFlow()

    private val _gpuSeries = MutableStateFlow<List<Float>>(emptyList())
    val gpuSeries: StateFlow<List<Float>> = _gpuSeries.asStateFlow()

    init {
        rootManager.bind()
    }

    fun refreshCpuSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            val json = rootManager.getCpuSnapshotJson() ?: return@launch
            try {
                val obj = JSONObject(json.toString())
                val snapshot = CpuSnapshot(
                    cpuName = obj.optString("cpuName", "—"),
                    coresPhysical = obj.optInt("coresPhysical", 0),
                    coresLogical = obj.optInt("coresLogical", 0),
                    coreLayout = obj.optString("coreLayout", ""),
                    coreLayoutLabeled = obj.optString("coreLayoutLabeled", ""),
                    cpuTempC = obj.optDouble("cpuTempC", -1.0),
                    cpuTempSource = obj.optString("cpuTempSource", ""),
                    cpuTempRaw = obj.optLong("cpuTempRaw", 0L),
                    cpuTempCandidates = obj.optString("cpuTempCandidates", ""),
                    cpuTempUnitAssumption = obj.optString("cpuTempUnitAssumption", ""),
                    usagePercent = obj.optDouble("usagePercent", 0.0),
                    maxFreqKHz = obj.optLong("maxFreqKHz", 0L),
                    processes = obj.optInt("processes", 0),
                    threads = obj.optInt("threads", 0),
                    handles = obj.optLong("handles", 0L),
                    uptimeSeconds = obj.optLong("uptimeSeconds", 0L)
                )
                _cpuSnapshot.value = snapshot
                tempLogCounter++
                if (tempLogCounter % 10 == 0) {
                    Log.d(
                        "TaskManager",
                        "CPU temp ${snapshot.cpuTempC}C raw=${snapshot.cpuTempRaw} src=${snapshot.cpuTempSource} unit=${snapshot.cpuTempUnitAssumption} cand=${snapshot.cpuTempCandidates}"
                    )
                }
                val clamped = snapshot.usagePercent.coerceIn(0.0, 100.0).toFloat()
                val current = _cpuSeries.value
                val updated = (current + clamped).takeLast(60)
                _cpuSeries.value = if (updated.size < 60) {
                    val pad = List(60 - updated.size) { clamped }
                    pad + updated
                } else {
                    updated
                }
            } catch (e: Exception) {
                Log.e("TaskManager", "CPU snapshot parse error", e)
            }
        }
    }

    fun refreshGpuSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            val json = rootManager.getGpuSnapshotJson() ?: return@launch
            try {
                val obj = JSONObject(json)
                val snapshot = GpuSnapshot(
                    gpuName = obj.optString("gpuName", "—"),
                    utilPercent = obj.optDouble("utilPercent", -1.0),
                    tempC = obj.optDouble("tempC", -1.0),
                    vulkanApiVersion = obj.optString("vulkanApiVersion", ""),
                    vulkanDriverVersion = obj.optString("vulkanDriverVersion", ""),
                    dedicatedBudgetBytes = obj.optLong("dedicatedBudgetBytes", 0L),
                    dedicatedUsedBytes = obj.optLong("dedicatedUsedBytes", 0L),
                    sharedBudgetBytes = obj.optLong("sharedBudgetBytes", 0L),
                    sharedUsedBytes = obj.optLong("sharedUsedBytes", 0L),
                    dedicatedTotalBytes = obj.optLong("dedicatedTotalBytes", 0L),
                    sharedTotalBytes = obj.optLong("sharedTotalBytes", 0L),
                    hasMemoryBudget = obj.optBoolean("hasMemoryBudget", false),
                    error = obj.optString("error", "")
                )
                _gpuSnapshot.value = snapshot
                val clamped = snapshot.utilPercent.coerceIn(0.0, 100.0).toFloat()
                val current = _gpuSeries.value
                val updated = (current + clamped).takeLast(60)
                _gpuSeries.value = if (updated.size < 60) {
                    val pad = List(60 - updated.size) { clamped }
                    pad + updated
                } else {
                    updated
                }
            } catch (e: Exception) {
                Log.e("TaskManager", "GPU snapshot parse error", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        rootManager.unbind()
    }
}
