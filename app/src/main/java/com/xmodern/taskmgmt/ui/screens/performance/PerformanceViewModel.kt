package com.xmodern.taskmgmt.ui.screens.performance

import android.app.Application
import android.util.Log
import android.net.wifi.WifiManager
import android.net.ConnectivityManager
import android.net.LinkAddress
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xmodern.taskmgmt.service.RootConnectionManager
import com.xmodern.taskmgmt.util.SocNameMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.math.max


data class CpuSnapshot(
    val cpuName: String,
    val cpuDisplayName: String,
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

data class MiniSnapshot(
    val timestampMs: Long,
    val cpuUtil: Double,
    val cpuMaxFreqKHz: Long,
    val memUsedBytes: Long,
    val memTotalBytes: Long,
    val diskReadBps: Long,
    val diskWriteBps: Long,
    val netIface: String,
    val netRxBytes: Long,
    val netTxBytes: Long,
    val netSendBps: Long,
    val netRecvBps: Long,
    val gpuUtil: Double
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

    private val _miniSnapshot = MutableStateFlow<MiniSnapshot?>(null)
    val miniSnapshot: StateFlow<MiniSnapshot?> = _miniSnapshot.asStateFlow()

    private var lastDiskAvgResponseMs: Double? = null
    private var lastNetIface: String? = null
    private var lastNetRxBytes: Long = 0
    private var lastNetTxBytes: Long = 0
    private var lastNetRxPackets: Long = 0
    private var lastNetTxPackets: Long = 0
    private var lastNetTimestampMs: Long = 0
    private var lastNetSsid: String? = null
    private var lastNetSsidTimeMs: Long = 0
    private var lastNetIpv4: String? = null

    private var selectedCategoryId: String = "memory"
    private val lastFullUpdatedMs = mutableMapOf<String, Long>()
    private var lastMiniNetRxBytes: Long = 0
    private var lastMiniNetTxBytes: Long = 0
    private var lastMiniNetTimestampMs: Long = 0

    private val seriesCapacity = 60

    init {
        rootManager.bind()
    }

    fun setSelectedCategory(id: String) {
        selectedCategoryId = id
    }

    fun refreshCpuSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            val json = rootManager.getCpuSnapshotJson() ?: return@launch
            try {
                val obj = JSONObject(json.toString())
                val snapshot = CpuSnapshot(
                    cpuName = obj.optString("cpuName", "—"),
                    cpuDisplayName = "",
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
                val friendly = SocNameMapper.resolve(getApplication(), snapshot.cpuName)
                _cpuSnapshot.value = snapshot.copy(cpuDisplayName = friendly ?: snapshot.cpuName)
                tempLogCounter++
                if (tempLogCounter % 10 == 0) {
                    Log.d(
                        "TaskManager",
                        "CPU temp ${snapshot.cpuTempC}C raw=${snapshot.cpuTempRaw} src=${snapshot.cpuTempSource} unit=${snapshot.cpuTempUnitAssumption} cand=${snapshot.cpuTempCandidates}"
                    )
                }
                val clamped = snapshot.usagePercent.coerceIn(0.0, 100.0).toFloat()
                pushSeries(_cpuSeries, clamped)
                lastFullUpdatedMs["cpu"] = System.currentTimeMillis()
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
                pushSeries(_gpuSeries, clamped)
                lastFullUpdatedMs["gpu"] = System.currentTimeMillis()
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
                pushSeries(_memorySeries, clamped)
                lastFullUpdatedMs["memory"] = System.currentTimeMillis()
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
                pushSeries(_diskSeries, clamped)
                lastFullUpdatedMs["disk"] = System.currentTimeMillis()
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
                val ssid = if (iface.startsWith("wlan")) getWifiSsidCached(iface) else "—"
                val ipv4 = if (iface.isNotBlank()) getIpv4AddressCached(iface) else "—"

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

                val throughputMbps = max(sendBps, recvBps) * 8.0 / 1_000_000.0
                val chartValue = throughputMbps.coerceIn(0.0, 100.0).toFloat()
                pushSeries(_netSeries, chartValue)
                lastFullUpdatedMs["ethernet"] = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e("TaskManager", "Network snapshot parse error", e)
            }
        }
    }

    fun refreshMiniSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            val json = rootManager.getPerformanceMiniSnapshotJson() ?: return@launch
            try {
                val obj = JSONObject(json)
                val ts = obj.optLong("timestampMs", 0L)

                val cpuObj = obj.optJSONObject("cpu")
                val cpuUtil = cpuObj?.optDouble("util", 0.0) ?: 0.0
                val cpuMaxFreq = cpuObj?.optLong("maxFreqKHz", 0L) ?: 0L

                val memObj = obj.optJSONObject("mem")
                val memUsed = memObj?.optLong("usedBytes", 0L) ?: 0L
                val memTotal = memObj?.optLong("totalBytes", 0L) ?: 0L

                val diskObj = obj.optJSONObject("disk")
                val diskRead = diskObj?.optLong("readBps", 0L) ?: 0L
                val diskWrite = diskObj?.optLong("writeBps", 0L) ?: 0L

                val netObj = obj.optJSONObject("net")
                val netIface = netObj?.optString("iface", "") ?: ""
                val netRx = netObj?.optLong("rxBytes", 0L) ?: 0L
                val netTx = netObj?.optLong("txBytes", 0L) ?: 0L

                val gpuObj = obj.optJSONObject("gpu")
                val gpuUtil = gpuObj?.optDouble("util", 0.0) ?: 0.0

                val miniNetBps = computeMiniNetBps(netRx, netTx, ts)
                _miniSnapshot.value = MiniSnapshot(
                    timestampMs = ts,
                    cpuUtil = cpuUtil,
                    cpuMaxFreqKHz = cpuMaxFreq,
                    memUsedBytes = memUsed,
                    memTotalBytes = memTotal,
                    diskReadBps = diskRead,
                    diskWriteBps = diskWrite,
                    netIface = netIface,
                    netRxBytes = netRx,
                    netTxBytes = netTx,
                    netSendBps = miniNetBps.second,
                    netRecvBps = miniNetBps.first,
                    gpuUtil = gpuUtil
                )

                val now = System.currentTimeMillis()
                if (!shouldUseFull("cpu", now)) {
                    pushSeries(_cpuSeries, cpuUtil.coerceIn(0.0, 100.0).toFloat())
                }

                if (!shouldUseFull("memory", now)) {
                    val pct = if (memTotal > 0) (memUsed.toDouble() / memTotal.toDouble()) * 100.0 else 0.0
                    pushSeries(_memorySeries, pct.coerceIn(0.0, 100.0).toFloat())
                }

                if (!shouldUseFull("disk", now)) {
                    val diskMb = max(diskRead, diskWrite) / 1048576.0
                    pushSeries(_diskSeries, diskMb.coerceIn(0.0, 100.0).toFloat())
                }

                if (!shouldUseFull("ethernet", now)) {
                    val throughputMbps = max(miniNetBps.first, miniNetBps.second) * 8.0 / 1_000_000.0
                    pushSeries(_netSeries, throughputMbps.coerceIn(0.0, 100.0).toFloat())
                }

                if (!shouldUseFull("gpu", now)) {
                    pushSeries(_gpuSeries, gpuUtil.coerceIn(0.0, 100.0).toFloat())
                }
            } catch (e: Exception) {
                Log.e("TaskManager", "Mini snapshot parse error", e)
            }
        }
    }

    private fun shouldUseFull(id: String, nowMs: Long): Boolean {
        if (selectedCategoryId != id) return false
        val last = lastFullUpdatedMs[id] ?: return false
        return nowMs - last < 1200
    }

    private fun computeMiniNetBps(rxBytes: Long, txBytes: Long, timestampMs: Long): Pair<Long, Long> {
        if (timestampMs <= 0) return 0L to 0L
        if (lastMiniNetTimestampMs <= 0) {
            lastMiniNetTimestampMs = timestampMs
            lastMiniNetRxBytes = rxBytes
            lastMiniNetTxBytes = txBytes
            return 0L to 0L
        }
        val dtMs = (timestampMs - lastMiniNetTimestampMs).coerceAtLeast(0L)
        val rx = if (dtMs > 0) ((rxBytes - lastMiniNetRxBytes).coerceAtLeast(0L) * 1000L) / dtMs else 0L
        val tx = if (dtMs > 0) ((txBytes - lastMiniNetTxBytes).coerceAtLeast(0L) * 1000L) / dtMs else 0L
        lastMiniNetTimestampMs = timestampMs
        lastMiniNetRxBytes = rxBytes
        lastMiniNetTxBytes = txBytes
        return rx to tx
    }

    private fun pushSeries(flow: MutableStateFlow<List<Float>>, value: Float) {
        val current = flow.value
        val updated = (current + value).takeLast(seriesCapacity)
        flow.value = if (updated.size < seriesCapacity) {
            val pad = List(seriesCapacity - updated.size) { value }
            pad + updated
        } else {
            updated
        }
    }

    private fun getWifiSsidCached(iface: String): String {
        val now = System.currentTimeMillis()
        if (!lastNetSsid.isNullOrBlank() && now - lastNetSsidTimeMs < 10_000) {
            return lastNetSsid ?: "—"
        }
        return try {
            val wifi = getApplication<Application>().applicationContext.getSystemService(WifiManager::class.java)
            val ssid = wifi?.connectionInfo?.ssid
            val cleaned = cleanSsid(ssid)
            if (cleaned != null) {
                lastNetSsid = cleaned
                lastNetSsidTimeMs = now
                return cleaned
            }
            val rootSsid = readWifiSsidFromRoot()
            if (!rootSsid.isNullOrBlank()) {
                lastNetSsid = rootSsid
                lastNetSsidTimeMs = now
                return rootSsid
            }
            lastNetSsid ?: "—"
        } catch (e: Exception) {
            lastNetSsid ?: "—"
        }
    }

    private fun getIpv4AddressCached(iface: String): String {
        return try {
            val cm = getApplication<Application>().applicationContext.getSystemService(ConnectivityManager::class.java)
            val lp = cm?.getLinkProperties(cm.activeNetwork)
            val ifaceName = lp?.interfaceName ?: iface
            val fromLp = lp?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress && !it.address.isLinkLocalAddress }
                ?.address?.hostAddress
            if (!fromLp.isNullOrBlank()) {
                lastNetIpv4 = fromLp
                return fromLp
            }
            val fallback = getIpv4FromInterface(ifaceName)
            if (!fallback.isNullOrBlank()) {
                lastNetIpv4 = fallback
                return fallback
            }
            lastNetIpv4 ?: "—"
        } catch (e: Exception) {
            lastNetIpv4 ?: "—"
        }
    }

    private fun getIpv4FromInterface(iface: String): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        val list = Collections.list(interfaces)
        val netIf = list.firstOrNull { it.name == iface } ?: return null
        val addrs = Collections.list(netIf.inetAddresses)
        return addrs.firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }

    private fun cleanSsid(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw == "<unknown ssid>" || raw == "0x" || raw == "0x0") return null
        val trimmed = raw.trim().trim('"')
        if (trimmed.isBlank()) return null
        return trimmed
    }

    private fun readWifiSsidFromRoot(): String? {
        val out = runRootCommand("cmd wifi status")
        val ssid = extractSsidFromText(out)
        if (!ssid.isNullOrBlank()) return ssid
        val out2 = runRootCommand("dumpsys wifi | grep -m 1 -E 'SSID|mWifiInfo'")
        return extractSsidFromText(out2)
    }

    private fun extractSsidFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        text.lineSequence().forEach { line ->
            val idx = line.indexOf("SSID:")
            if (idx >= 0) {
                val value = line.substring(idx + 5).trim().trim('"')
                if (value.isNotBlank()) return value
            }
            if (line.contains("SSID=")) {
                val parts = line.split("SSID=")
                if (parts.size >= 2) {
                    val value = parts[1].trim().trim('"')
                    if (value.isNotBlank()) return value
                }
            }
        }
        return null
    }

    private fun runRootCommand(cmd: String): String? {
        return try {
            val proc = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            proc.waitFor(2, TimeUnit.SECONDS)
            proc.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            null
        }
    }


    override fun onCleared() {
        super.onCleared()
        rootManager.unbind()
    }
}
