package com.example.taskmanager.service

import android.util.Log

object NativeBridge {
    init {
        try {
            System.loadLibrary("HardwareAccess")
            Log.d("TaskManager", "Native Library (C++) Loaded Successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("TaskManager", "Failed to load native library", e)
        } catch (e: Exception) {
            Log.e("TaskManager", "Unknown error loading native library", e)
        }
    }

        external fun hello(): String

    external fun getProcessList(): String

    external fun getProcessExtendedInfo(pid: Int): String

    external fun getProcessDeepSnapshot(pid: Int): String

    external fun sendSignal(pid: Int, signal: Int): Boolean

    external fun getKillCandidates(): String

    external fun executeKillTransaction(packages: String): Long

    external fun getFreeRam(): Long

    external fun getCpuSnapshotJson(): String

    external fun getVulkanInfoJson(): String

    external fun getGpuSnapshotJson(): String

    external fun getMemorySnapshotJson(): String
}

                

            

        

    
