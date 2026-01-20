package com.example.taskmanager.service

object NativeBridge {
    init {
        System.loadLibrary("rust_backend")
    }

    external fun hello(): String
    
    external fun getProcessListJson(): String
}
