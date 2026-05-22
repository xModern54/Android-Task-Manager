package com.xmodern.taskmgmt

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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

enum class AppScreen {
    LIST, DETAIL, KILL_REVIEW
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
                    val cached = Shell.getCachedShell()
                    if (cached != null && !cached.isRoot) {
                        try { cached.close() } catch (e: Exception) { /* Ignore */ }
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

            TaskManagerTheme(forceDark = true) {
                LogDynamicColorsIfChanged()
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

                        val currentScreen = when {
                            isReviewingKill -> AppScreen.KILL_REVIEW
                            selectedPid != null -> AppScreen.DETAIL
                            else -> AppScreen.LIST
                        }

                        AnimatedContent(
                            targetState = currentScreen,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                if (initialState == AppScreen.LIST && targetState == AppScreen.DETAIL) {
                                    (slideInHorizontally { width -> width } + fadeIn())
                                        .togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                                        .using(null)
                                } else if (initialState == AppScreen.DETAIL && targetState == AppScreen.LIST) {
                                    (slideInHorizontally { width -> -width } + fadeIn())
                                        .togetherWith(slideOutHorizontally { width -> width } + fadeOut())
                                        .using(null)
                                } else {
                                    (fadeIn() togetherWith fadeOut()).using(null)
                                }
                            },
                            label = "screen_transition"
                        ) { screen ->
                            when (screen) {
                                AppScreen.LIST -> {
                                    ProcessListScreen(
                                        viewModel = viewModel,
                                        listState = processListState,
                                        onProcessClick = { pid -> selectedPid = pid }
                                    )
                                }
                                AppScreen.DETAIL -> {
                                    val pid = selectedPid
                                    if (pid != null) {
                                        ProcessDetailScreen(
                                            pid = pid,
                                            viewModel = viewModel,
                                            onBack = { 
                                                selectedPid = null 
                                                viewModel.clearSelectedProcess()
                                            }
                                        )
                                    }
                                }
                                AppScreen.KILL_REVIEW -> {
                                    KillReviewScreen(
                                        viewModel = viewModel,
                                        onBack = { viewModel.cancelKillReview() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogDynamicColorsIfChanged() {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching { context.getColor(android.R.color.system_accent1_500) }.getOrNull()
    } else {
        null
    }
    val last = remember { mutableStateOf<Pair<Int, Int?>?>(null) }
    SideEffect {
        val current = Pair(primary, accent)
        if (last.value != current) {
            val primaryHex = "0x" + primary.toUInt().toString(16)
            val accentHex = accent?.let { "0x" + it.toUInt().toString(16) } ?: "null"
            Log.d("TaskManager", "DynamicColor primary=$primaryHex system_accent1_500=$accentHex")
            last.value = current
        }
    }
}
