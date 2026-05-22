package com.xmodern.taskmgmt.service

import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.xmodern.taskmgmt.IRootService
import com.topjohnwu.superuser.ipc.RootService

class RootBackendService : RootService() {

    override fun onCreate() {
        super.onCreate()
        Log.d("TaskManager", "RootBackendService Created (PID: ${android.os.Process.myPid()})")
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d("TaskManager", "RootBackendService Bound")
        return object : IRootService.Stub() {
            override fun getTestString(): String = NativeBridge.hello()

            override fun getProcessList(): String {
                Log.d("TaskManager", "NativeBridge.getProcessList called")
                return NativeBridge.getProcessList()
            }

            override fun getProcessExtendedInfo(pid: Int): String =
                NativeBridge.getProcessExtendedInfo(pid)

            override fun getProcessDeepSnapshot(pid: Int): String =
                NativeBridge.getProcessDeepSnapshot(pid)

            override fun sendSignal(pid: Int, signal: Int): Boolean =
                NativeBridge.sendSignal(pid, signal)

            override fun getKillCandidates(): String = NativeBridge.getKillCandidates()

            override fun executeKillTransaction(packages: String): Long =
                NativeBridge.executeKillTransaction(packages)

            override fun getFreeRam(): Long = NativeBridge.getFreeRam()

            override fun getCpuSnapshotJson(): String = NativeBridge.getCpuSnapshotJson()

            override fun getVulkanInfoJson(): String = NativeBridge.getVulkanInfoJson()

            override fun getGpuSnapshotJson(): String = NativeBridge.getGpuSnapshotJson()

            override fun getMemorySnapshotJson(): String = NativeBridge.getMemorySnapshotJson()

            override fun getDiskSnapshotJson(): String = NativeBridge.getDiskSnapshotJson()

            override fun getNetSnapshotJson(): String = NativeBridge.getNetSnapshotJson()

            override fun getPerformanceMiniSnapshotJson(): String =
                NativeBridge.getPerformanceMiniSnapshotJson()

            override fun getBatterySnapshotJson(): String = NativeBridge.getBatterySnapshotJson()
        }
    }
}
