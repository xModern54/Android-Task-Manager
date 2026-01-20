package com.example.taskmanager.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.example.taskmanager.IRootService
import com.topjohnwu.superuser.ipc.RootService

class RootConnectionManager(private val context: Context) {

    private var rootService: IRootService? = null
    private var isBound = false

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
        Log.d("TaskManager", "Attempting to bind RootService...")
        val intent = Intent(context, RootBackendService::class.java)
        try {
            RootService.bind(intent, connection)
        } catch (e: Exception) {
            Log.e("TaskManager", "Failed to bind RootService", e)
        }
    }

    fun unbind() {
        if (isBound) {
            RootService.unbind(connection)
            isBound = false
        }
    }

    fun getProcessList(): String? {
        return try {
            val result = rootService?.processList
            Log.d("TaskManager", "Fetched Data Length: ${result?.length ?: "null"}")
            result
        } catch (e: Exception) {
            Log.e("TaskManager", "Error fetching process list", e)
            null
        }
    }
}