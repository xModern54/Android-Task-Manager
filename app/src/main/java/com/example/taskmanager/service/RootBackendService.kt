package com.example.taskmanager.service

import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.taskmanager.IRootService
import com.topjohnwu.superuser.ipc.RootService

class RootBackendService : RootService() {

    override fun onCreate() {
        super.onCreate()
        Log.d("TaskManager", "RootBackendService Created (PID: ${android.os.Process.myPid()})")
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d("TaskManager", "RootBackendService Bound")
        return object : IRootService.Stub() {
            override fun getTestString(): String {
                return NativeBridge.hello()
            }

            override fun getProcessList(): String {
                Log.d("TaskManager", "NativeBridge.getProcessList called")
                return NativeBridge.getProcessList()
            }
        }
    }
}