package com.example.taskmanager.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.example.taskmanager.IRootService
import com.topjohnwu.superuser.ipc.RootService

class RootConnectionManager(private val context: Context) {

    private var rootService: IRootService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            rootService = IRootService.Stub.asInterface(service)
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            rootService = null
            isBound = false
        }
    }

    fun bind() {
        val intent = Intent(context, RootBackendService::class.java)
        // Libsu specific: binds the service in the root process
        RootService.bind(intent, connection)
    }

    fun unbind() {
        if (isBound) {
            RootService.unbind(connection)
            isBound = false
        }
    }

    fun getProcessListJson(): String? {
        return try {
            rootService?.processListJson
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
