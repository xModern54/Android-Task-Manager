package com.xmodern.taskmgmt.ui.screens.processdetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xmodern.taskmgmt.ui.screens.processlist.ProcessListViewModel
import com.xmodern.taskmgmt.ui.theme.DarkBackground
import com.xmodern.taskmgmt.ui.theme.DarkSurface
import com.xmodern.taskmgmt.ui.theme.TextGrey
import com.xmodern.taskmgmt.ui.theme.TextWhite
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessDetailScreen(
    pid: Int,
    viewModel: ProcessListViewModel,
    onBack: () -> Unit
) {
    val processDetail by viewModel.selectedProcessDetails.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Statistics", "Modules", "Threads")

    LaunchedEffect(pid) {
        while (isActive) {
            viewModel.fetchProcessDetails(pid)
            delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Process Details", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        if (processDetail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading...", color = TextGrey)
            }
        } else {
            val detail = processDetail!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Header Area (Fixed)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBackground)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon & Names
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.DarkGray)
                        ) {
                            if (detail.icon != null) {
                                Image(
                                    painter = rememberDrawablePainter(drawable = detail.icon),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(16.dp))
                        Column {
                            Text(
                                text = detail.label,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = detail.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "PID: ${detail.pid}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextGrey.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ActionButton(
                            icon = Icons.Default.PlayArrow,
                            label = "Open",
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            onClick = { viewModel.openApp(detail.name, detail.label) }
                        )
                        ActionButton(
                            icon = Icons.Default.Warning,
                            label = "Stop",
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            onClick = { viewModel.forceStopApp(detail.name, detail.label) }
                        )
                        ActionButton(
                            icon = Icons.Default.Delete,
                            label = "Kill",
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.error,
                            onClick = { 
                                viewModel.killProcess(detail.pid, detail.label) { onBack() } 
                            }
                        )
                    }
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurface,
                    contentColor = TextWhite,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }

                // Tab Content
                when (selectedTab) {
                    0 -> OverviewTab(detail)
                    1 -> StatisticsTab(detail)
                    2 -> ModulesTab(detail.modules)
                    3 -> ThreadsTab(detail.threadList)
                }
            }
        }
    }
}

@Composable
fun OverviewTab(detail: ProcessDetail) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DetailCard("General Information") {
            DetailRow("State", detail.state)
            DetailRow("PPID", detail.ppid)
            DetailRow("User ID", detail.user)
            DetailRow("Path", detail.exePath)
        }
        DetailCard("Time") {
            DetailRow("Elapsed Time", detail.elapsedTime)
        }
    }
}

@Composable
fun StatisticsTab(detail: ProcessDetail) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DetailCard("CPU Scheduling") {
            DetailRow("Nice Value", detail.nice)
            DetailRow("Priority", detail.priority)
            DetailRow("Voluntary Switches", detail.voluntaryCtxSwitches)
            DetailRow("Non-Voluntary Switches", detail.nonVoluntaryCtxSwitches)
        }
        
        DetailCard("Memory Management") {
            DetailRow("OOM Score", detail.oomScore)
            DetailRow("Minor Page Faults", detail.minorPageFaults)
            DetailRow("Major Page Faults", detail.majorPageFaults)
        }
    }
}

@Composable
fun ModulesTab(modules: List<String>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
// ...
        if (modules.isEmpty()) {
            item { Text("No modules found or access denied.", color = TextGrey) }
        } else {
            items(modules) { moduleStr ->
                // Format: Filename|FullPath|SizeBytes
                val parts = moduleStr.split("|")
                val filename = parts.getOrNull(0) ?: "?"
                val fullPath = parts.getOrNull(1) ?: "?"
                val sizeBytes = parts.getOrNull(2)?.toLongOrNull() ?: 0L

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT SIDE: Icon + Names
                    Column(modifier = Modifier.weight(1f)) {
                        // 1. Filename (High Visibility)
                        Text(
                            text = filename,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 2. Full Path (Low Visibility)
                        Text(
                            text = fullPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // RIGHT SIDE: Size
                    Text(
                        text = android.text.format.Formatter.formatFileSize(context, sizeBytes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                androidx.compose.material3.Divider(color = Color.DarkGray, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun ThreadsTab(threads: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (threads.isEmpty()) {
            item { Text("No threads info available.", color = TextGrey) }
        } else {
            items(threads) { threadInfo ->
                // Format tid:name
                val parts = threadInfo.split(":", limit = 2)
                val tid = parts.getOrNull(0) ?: "?"
                val name = parts.getOrNull(1) ?: "Unknown"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "TID: $tid",
                        color = TextGrey,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                androidx.compose.material3.Divider(color = Color.DarkGray, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = color
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = null)
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextWhite, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextGrey, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            color = TextWhite,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
