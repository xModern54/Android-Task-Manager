package com.xmodern.taskmgmt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.xmodern.taskmgmt.ui.screens.processdetail.ProcessDetailScreen
import com.xmodern.taskmgmt.ui.screens.processlist.KillReviewScreen
import com.xmodern.taskmgmt.ui.screens.processlist.ProcessListScreen
import com.xmodern.taskmgmt.ui.screens.processlist.ProcessListViewModel
import com.xmodern.taskmgmt.ui.screens.startup.RootDeniedScreen
import com.xmodern.taskmgmt.ui.theme.TaskManagerTheme
import com.xmodern.taskmgmt.ui.theme.TextWhite
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RootState {
    CHECKING, GRANTED, DENIED
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            var rootState by remember { mutableStateOf(RootState.CHECKING) }
            val scope = rememberCoroutineScope()

            // Function to check root
            fun checkRoot() {
                rootState = RootState.CHECKING
                scope.launch(Dispatchers.IO) {
                    // If a previous shell exists and was denied, we might need to close it to prompt again
                    // depending on libsu configuration, but usually getShell() is sufficient.
                    // For safety on retry:
                    if (Shell.getCachedShell() != null) {
                       try { Shell.getCachedShell()?.close() } catch (e: Exception) { /* Ignore */ }
                    }
                    
                    val isRoot = try {
                        Shell.getShell().isRoot
                    } catch (e: Exception) {
                        false
                    }
                    withContext(Dispatchers.Main) {
                        rootState = if (isRoot) RootState.GRANTED else RootState.DENIED
                    }
                }
            }

            // Initial Check
            LaunchedEffect(Unit) {
                checkRoot()
            }

            TaskManagerTheme {
                when (rootState) {
                    RootState.CHECKING -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = TextWhite)
                        }
                    }
                    RootState.DENIED -> {
                        RootDeniedScreen(onRetry = { checkRoot() })
                    }
                    RootState.GRANTED -> {
                        // Original App Content
                        val viewModel: ProcessListViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                        var selectedPid by remember { mutableStateOf<Int?>(null) }
                        val isReviewingKill by viewModel.isReviewingKill.collectAsState()
                        
                        // Hoist scroll state to preserve position
                        val processListState = rememberLazyListState()

                        // Intercept system back gesture when a process is selected
                        BackHandler(enabled = selectedPid != null) {
                            selectedPid = null
                            viewModel.clearSelectedProcess()
                        }
                        
                        // Intercept back gesture for Kill Review
                        BackHandler(enabled = isReviewingKill) {
                            viewModel.cancelKillReview()
                        }

                        when {
                            isReviewingKill -> {
                                KillReviewScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.cancelKillReview() }
                                )
                            }
                            selectedPid != null -> {
                                ProcessDetailScreen(
                                    pid = selectedPid!!,
                                    viewModel = viewModel,
                                    onBack = { 
                                        selectedPid = null 
                                        viewModel.clearSelectedProcess()
                                    }
                                )
                            }
                            else -> {
                                ProcessListScreen(
                                    viewModel = viewModel,
                                    listState = processListState,
                                    onProcessClick = { pid -> selectedPid = pid }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}