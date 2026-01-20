package com.example.taskmanager.service

import android.content.Intent
import android.os.IBinder
import com.example.taskmanager.IRootService
import com.topjohnwu.superuser.ipc.RootService

class RootBackendService : RootService() {

    override fun onBind(intent: Intent): IBinder {
        return object : IRootService.Stub() {
            override fun getTestString(): String {
                return NativeBridge.hello()
            }

            override fun getProcessListJson(): String {
                return NativeBridge.getProcessListJson()
            }
        }
    }
}
