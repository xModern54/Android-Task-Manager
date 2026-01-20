package com.example.taskmanager.service

import android.util.Log

object NativeBridge {
    init {
        try {
            System.loadLibrary("system_manager")
            Log.d("TaskManager", "Native Library (C++) Loaded Successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("TaskManager", "Failed to load native library", e)
        } catch (e: Exception) {
            Log.e("TaskManager", "Unknown error loading native library", e)
        }
    }

    external fun hello(): String
    external fun getProcessList(): String
}