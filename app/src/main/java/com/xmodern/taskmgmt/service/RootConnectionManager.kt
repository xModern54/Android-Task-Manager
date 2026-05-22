package com.xmodern.taskmgmt.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.xmodern.taskmgmt.IRootService
import com.topjohnwu.superuser.ipc.RootService

class RootConnectionManager private constructor(private val context: Context) {

    private var rootService: IRootService? = null
    private var isBound = false

    companion object {
        @Volatile
        private var instance: RootConnectionManager? = null

        fun getInstance(context: Context): RootConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: RootConnectionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("TaskManager", "RootService Connected")
            rootService = IRootService.Stub.asInterface(service)
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("TaskManager", "RootService Disconnected")
            rootService = null
            isBound = false
        }
    }

    fun bind() {
        if (isBound || rootService != null) {
            Log.d("TaskManager", "RootService is already bound or binding, skipping bind.")
            return
        }
        Log.d("TaskManager", "Attempting to bind RootService...")
        val intent = Intent(context, RootBackendService::class.java)
        try {
            RootService.bind(intent, connection)
        } catch (e: Exception) {
            Log.e("TaskManager", "Failed to bind RootService", e)
        }
    }

    fun unbind() {
        // We keep the binding alive globally to prevent the daemon from stopping
        // and requesting root access repeatedly when minimizing/restoring the app.
        // However, we can keep the unbind function as a no-op or conditionally unbind.
        Log.d("TaskManager", "RootConnectionManager unbind ignored to maintain persistent root daemon.")
    }

    fun getProcessList(): String? {
        return try {
            rootService?.processList
        } catch (e: Exception) {
            Log.e("TaskManager", "Error fetching process list", e)
            null
        }
    }

    fun getProcessExtendedInfo(pid: Int): String? {
        return try {
            rootService?.getProcessExtendedInfo(pid)
        } catch (e: Exception) {
            Log.e("TaskManager", "Error fetching extended info", e)
            null
        }
    }

    fun getProcessDeepSnapshot(pid: Int): String? {
        return try {
            rootService?.getProcessDeepSnapshot(pid)
        } catch (e: Exception) {
            Log.e("TaskManager", "Error fetching deep snapshot", e)
            null
        }
    }

    fun sendSignal(pid: Int, signal: Int): Boolean {
        return try {
            rootService?.sendSignal(pid, signal) ?: false
        } catch (e: Exception) {
            Log.e("TaskManager", "Error sending signal", e)
            false
        }
    }

    fun getKillCandidates(): List<String> {
        return try {
            rootService?.killCandidates?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
        } catch (e: Exception) {
            Log.e("TaskManager", "Error getting candidates", e)
            emptyList()
        }
    }

    fun executeKillTransaction(packages: List<String>): Long {
        return try {
            val payload = packages.joinToString("|")
            rootService?.executeKillTransaction(payload) ?: 0L
        } catch (e: Exception) {
            Log.e("TaskManager", "Error executing transaction", e)
            0L
        }
    }

    fun getFreeRam(): Long {
        return try {
            rootService?.freeRam ?: 0L
        } catch (e: Exception) {
            Log.e("TaskManager", "Error getting free RAM", e)
            0L
        }
    }

    fun getCpuSnapshotJson(): String? {
        return try {
            rootService?.cpuSnapshotJson
        } catch (e: Exception) {
            Log.e("TaskManager", "Error getting CPU snapshot", e)
            null
        }
    }

    fun getVulkanInfoJson(): String? {
        return try {
            rootService?.vulkanInfoJson
        } catch (e: Exception) {
            Log.e("TaskManager", "Error getting Vulkan info", e)
            null
        }
    }

    fun getGpuSnapshotJson(): String? {
        return try {
            rootService?.gpuSnapshotJson
        } catch (e: Exception) {
            Log.e("TaskManager", "Error getting GPU snapshot", e)
            null
        }
    }

    fun getMemorySnapshotJson(): String? {
        return try {
            rootService?.memorySnapshotJson
        } catch (e: Exception) {
            Log.e("TaskManager", "Error getting memory snapshot", e)
            null
        }
    }

    fun getDiskSnapshotJson(): String? {
        return try {
            rootService?.diskSnapshotJson
        } catch (e: Exception) {
            Log.e("TaskManager", "Error getting disk snapshot", e)
            null
        }
    }

    fun getNetSnapshotJson(): String? {
        return try {
            rootService?.netSnapshotJson
        } catch (e: Exception) {
            Log.e("TaskManager", "Error getting network snapshot", e)
            null
        }
    }

    fun getPerformanceMiniSnapshotJson(): String? {
        return try {
            rootService?.performanceMiniSnapshotJson
        } catch (e: Exception) {
            Log.e("TaskManager", "Error getting mini snapshot", e)
            null
        }
    }

    fun getBatterySnapshotJson(): String? {
        return try {
            rootService?.batterySnapshotJson
        } catch (e: Exception) {
            Log.e("TaskManager", "Error getting battery snapshot", e)
            null
        }
    }
}
