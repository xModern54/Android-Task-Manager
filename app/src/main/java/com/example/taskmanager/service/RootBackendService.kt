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

            

                                    override fun getProcessExtendedInfo(pid: Int): String {
                return NativeBridge.getProcessExtendedInfo(pid)
            }

            override fun getProcessDeepSnapshot(pid: Int): String {
                return NativeBridge.getProcessDeepSnapshot(pid)
            }

            override fun sendSignal(pid: Int, signal: Int): Boolean {

            

                        

            

                                                    return NativeBridge.sendSignal(pid, signal)

            

                        

            

                                                }

            

                        

            

                                    

            

                        

            

                                                override fun getKillCandidates(): String {

            

                        

            

                                                    return NativeBridge.getKillCandidates()

            

                        

            

                                                }

            

                        

            

                                    

            

                        

            

                                                            override fun executeKillTransaction(packages: String): Long {

            

                        

            

                                    

            

                        

            

                                                                return NativeBridge.executeKillTransaction(packages)

            

                        

            

                                    

            

                        

            

                                                            }

            

                        

            

                                    

            

                        

            

                                                

            

                        

            

                                    

            

                        

            

                                                            override fun getFreeRam(): Long {

            

                        

            

                                    

            

                        

            

                                                                return NativeBridge.getFreeRam()

            

                        

            

                                    

            

                        

            

                                                            }

                                                            override fun getCpuSnapshotJson(): String {
                                                                return NativeBridge.getCpuSnapshotJson()
                                                            }

                                                            override fun getVulkanInfoJson(): String {
                                                                return NativeBridge.getVulkanInfoJson()
                                                            }

                                                            override fun getGpuSnapshotJson(): String {
                                                                return NativeBridge.getGpuSnapshotJson()
                                                            }

                                                            override fun getMemorySnapshotJson(): String {
                                                                return NativeBridge.getMemorySnapshotJson()
                                                            }

                                                            override fun getDiskSnapshotJson(): String {
                                                                return NativeBridge.getDiskSnapshotJson()
                                                            }



            

                        

            

                                    

            

                        

            

                                                        }

            

                        

            

                                    

            

                        

            

                                                    }

            

                        

            

                                    

            

                        

            

                                                }

            

                        

            

                                    

            

                        

            

                                                

            

                        

            

                                    

            

                        

            
