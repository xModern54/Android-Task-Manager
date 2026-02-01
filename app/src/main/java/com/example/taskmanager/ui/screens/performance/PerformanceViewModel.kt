package com.example.taskmanager.ui.screens.performance

import android.app.Application
import android.util.Log
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanager.service.RootConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections


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
    val driverDateIso: String,
    val dedicatedBudgetBytes: Long,
    val dedicatedUsedBytes: Long,
    val sharedBudgetBytes: Long,
    val sharedUsedBytes: Long,
    val dedicatedTotalBytes: Long,
    val sharedTotalBytes: Long,
    val hasMemoryBudget: Boolean,
    val error: String
)

data class MemorySnapshot(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
    val cachedBytes: Long,
    val compressedBytes: Long,
    val committedUsedBytes: Long,
    val committedLimitBytes: Long,
    val timestampMs: Long
)

data class DiskSnapshot(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long,
    val readBps: Long,
    val writeBps: Long,
    val activeTimePct: Double,
    val avgResponseMs: Double,
    val avgResponseMsDisplay: Double?,
    val mountPoint: String,
    val fsType: String,
    val blockDevice: String,
    val timestampMs: Long,
    val error: String
)

data class NetSnapshot(
    val iface: String,
    val adapterLabel: String,
    val ssid: String,
    val ipv4: String,
    val sendBps: Long,
    val recvBps: Long,
    val packetsTotal: Long
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

    private val _memorySnapshot = MutableStateFlow<MemorySnapshot?>(null)
    val memorySnapshot: StateFlow<MemorySnapshot?> = _memorySnapshot.asStateFlow()

    private val _memorySeries = MutableStateFlow<List<Float>>(emptyList())
    val memorySeries: StateFlow<List<Float>> = _memorySeries.asStateFlow()

    private val _diskSnapshot = MutableStateFlow<DiskSnapshot?>(null)
    val diskSnapshot: StateFlow<DiskSnapshot?> = _diskSnapshot.asStateFlow()

    private val _diskSeries = MutableStateFlow<List<Float>>(emptyList())
    val diskSeries: StateFlow<List<Float>> = _diskSeries.asStateFlow()

    private val _netSnapshot = MutableStateFlow<NetSnapshot?>(null)
    val netSnapshot: StateFlow<NetSnapshot?> = _netSnapshot.asStateFlow()

    private val _netSeries = MutableStateFlow<List<Float>>(emptyList())
    val netSeries: StateFlow<List<Float>> = _netSeries.asStateFlow()

    private var lastDiskAvgResponseMs: Double? = null
    private var lastNetIface: String? = null
    private var lastNetRxBytes: Long = 0
    private var lastNetTxBytes: Long = 0
    private var lastNetRxPackets: Long = 0
    private var lastNetTxPackets: Long = 0
    private var lastNetTimestampMs: Long = 0

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
                    driverDateIso = obj.optString("driverDateIso", ""),
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

    fun refreshMemorySnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            val json = rootManager.getMemorySnapshotJson() ?: return@launch
            try {
                val obj = JSONObject(json)
                val snapshot = MemorySnapshot(
                    totalBytes = obj.optLong("totalBytes", 0L),
                    usedBytes = obj.optLong("usedBytes", 0L),
                    availableBytes = obj.optLong("availableBytes", 0L),
                    cachedBytes = obj.optLong("cachedBytes", 0L),
                    compressedBytes = obj.optLong("compressedBytes", 0L),
                    committedUsedBytes = obj.optLong("committedUsedBytes", 0L),
                    committedLimitBytes = obj.optLong("committedLimitBytes", 0L),
                    timestampMs = obj.optLong("timestampMs", 0L)
                )
                _memorySnapshot.value = snapshot
                val percent = if (snapshot.totalBytes > 0) {
                    (snapshot.usedBytes.toDouble() / snapshot.totalBytes.toDouble()) * 100.0
                } else {
                    0.0
                }
                val clamped = percent.coerceIn(0.0, 100.0).toFloat()
                val current = _memorySeries.value
                val updated = (current + clamped).takeLast(60)
                _memorySeries.value = if (updated.size < 60) {
                    val pad = List(60 - updated.size) { clamped }
                    pad + updated
                } else {
                    updated
                }
            } catch (e: Exception) {
                Log.e("TaskManager", "Memory snapshot parse error", e)
            }
        }
    }

    fun refreshDiskSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            val json = rootManager.getDiskSnapshotJson() ?: return@launch
            try {
                val obj = JSONObject(json)
                val rawAvg = obj.optDouble("avgResponseMs", 0.0)
                if (rawAvg > 0.0) {
                    lastDiskAvgResponseMs = rawAvg
                }
                val snapshot = DiskSnapshot(
                    totalBytes = obj.optLong("totalBytes", 0L),
                    usedBytes = obj.optLong("usedBytes", 0L),
                    availableBytes = obj.optLong("availableBytes", 0L),
                    readBps = obj.optLong("readBps", 0L),
                    writeBps = obj.optLong("writeBps", 0L),
                    activeTimePct = obj.optDouble("activeTimePct", 0.0),
                    avgResponseMs = rawAvg,
                    avgResponseMsDisplay = lastDiskAvgResponseMs,
                    mountPoint = obj.optString("mountPoint", "/data"),
                    fsType = obj.optString("fsType", ""),
                    blockDevice = obj.optString("blockDevice", ""),
                    timestampMs = obj.optLong("timestampMs", 0L),
                    error = obj.optString("error", "")
                )
                _diskSnapshot.value = snapshot
                val clamped = snapshot.activeTimePct.coerceIn(0.0, 100.0).toFloat()
                val current = _diskSeries.value
                val updated = (current + clamped).takeLast(60)
                _diskSeries.value = if (updated.size < 60) {
                    val pad = List(60 - updated.size) { clamped }
                    pad + updated
                } else {
                    updated
                }
            } catch (e: Exception) {
                Log.e("TaskManager", "Disk snapshot parse error", e)
            }
        }
    }

    fun refreshNetSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            val json = rootManager.getNetSnapshotJson() ?: return@launch
            try {
                val obj = JSONObject(json)
                val iface = obj.optString("iface", "")
                val rxBytes = obj.optLong("rxBytes", 0L)
                val txBytes = obj.optLong("txBytes", 0L)
                val rxPackets = obj.optLong("rxPackets", 0L)
                val txPackets = obj.optLong("txPackets", 0L)
                val timestampMs = obj.optLong("timestampMs", 0L)

                if (iface.isNotBlank() && iface != lastNetIface) {
                    lastNetIface = iface
                    lastNetRxBytes = rxBytes
                    lastNetTxBytes = txBytes
                    lastNetRxPackets = rxPackets
                    lastNetTxPackets = txPackets
                    lastNetTimestampMs = timestampMs
                }

                val dtMs = (timestampMs - lastNetTimestampMs).coerceAtLeast(0L)
                val sendBps = if (dtMs > 0) {
                    ((txBytes - lastNetTxBytes).coerceAtLeast(0L) * 1000L) / dtMs
                } else {
                    0L
                }
                val recvBps = if (dtMs > 0) {
                    ((rxBytes - lastNetRxBytes).coerceAtLeast(0L) * 1000L) / dtMs
                } else {
                    0L
                }

                lastNetRxBytes = rxBytes
                lastNetTxBytes = txBytes
                lastNetRxPackets = rxPackets
                lastNetTxPackets = txPackets
                lastNetTimestampMs = timestampMs

                val adapterLabel = if (iface.startsWith("wlan")) "Wi‑Fi" else "Ethernet"
                val ssid = if (iface.startsWith("wlan")) getWifiSsid() else "—"
                val ipv4 = if (iface.isNotBlank()) getIpv4Address(iface) else "—"

                val snapshot = NetSnapshot(
                    iface = iface,
                    adapterLabel = adapterLabel,
                    ssid = ssid,
                    ipv4 = ipv4,
                    sendBps = sendBps,
                    recvBps = recvBps,
                    packetsTotal = rxPackets + txPackets
                )
                _netSnapshot.value = snapshot

                val throughputMbps = maxOf(sendBps, recvBps) * 8.0 / 1_000_000.0
                val chartValue = throughputMbps.coerceIn(0.0, 100.0).toFloat()
                val current = _netSeries.value
                val updated = (current + chartValue).takeLast(60)
                _netSeries.value = if (updated.size < 60) {
                    val pad = List(60 - updated.size) { chartValue }
                    pad + updated
                } else {
                    updated
                }
            } catch (e: Exception) {
                Log.e("TaskManager", "Network snapshot parse error", e)
            }
        }
    }

    private fun getWifiSsid(): String {
        return try {
            val wifi = getApplication<Application>().applicationContext.getSystemService(WifiManager::class.java)
            val ssid = wifi?.connectionInfo?.ssid ?: return "—"
            if (ssid == "<unknown ssid>" || ssid.isBlank()) "—" else ssid.trim('"')
        } catch (e: Exception) {
            "—"
        }
    }

    private fun getIpv4Address(iface: String): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "—"
            val list = Collections.list(interfaces)
            val netIf = list.firstOrNull { it.name == iface } ?: return "—"
            val addrs = Collections.list(netIf.inetAddresses)
            addrs.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress ?: "—"
        } catch (e: Exception) {
            "—"
        }
    }


    override fun onCleared() {
        super.onCleared()
        rootManager.unbind()
    }
}
