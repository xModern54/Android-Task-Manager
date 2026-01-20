package com.example.taskmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.taskmanager.ui.screens.processlist.ProcessListScreen
import com.example.taskmanager.ui.theme.TaskManagerTheme
import com.topjohnwu.superuser.Shell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize Root Shell
        Shell.getShell()
        
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TaskManagerTheme {
                ProcessListScreen()
            }
        }
    }
}
