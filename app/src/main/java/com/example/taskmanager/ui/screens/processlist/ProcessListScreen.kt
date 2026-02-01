package com.example.taskmanager.ui.screens.processlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskmanager.PerformanceActivity
import com.example.taskmanager.ui.components.process.ProcessRowItem
import com.example.taskmanager.ui.theme.DarkBackground
import com.example.taskmanager.ui.theme.DarkSurface
import com.example.taskmanager.ui.theme.DividerGrey
import com.example.taskmanager.ui.theme.TextGrey
import com.example.taskmanager.ui.theme.TextWhite
import java.util.Locale
import android.content.Intent

// Reusing Heatmap color
private val HeatmapColor = Color(0xFFD32F2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessListScreen(
    viewModel: ProcessListViewModel = viewModel(),
    listState: LazyListState = rememberLazyListState(),
    onProcessClick: (Int) -> Unit
) {
    val processList by viewModel.processList.collectAsState()
    
    // Global Stats
    val totalCpu by viewModel.totalCpuUsage.collectAsState()
    val totalRamUsed by viewModel.totalRamUsed.collectAsState()
    val totalRamSize by viewModel.totalRamSize.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current

    val layoutDirection = LocalLayoutDirection.current
    var menuExpanded by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Search...", color = TextGrey) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = TextWhite,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.updateSearchQuery("")
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (searchQuery.isNotEmpty()) {
                                viewModel.updateSearchQuery("")
                            } else {
                                isSearchActive = false
                            }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextWhite)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            } else {
                TopAppBar(
                    title = { Text("Task Manager", color = TextWhite) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkSurface
                    ),
                    actions = {
                        IconButton(onClick = {
                            context.startActivity(Intent(context, PerformanceActivity::class.java))
                        }) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = "Performance",
                                tint = TextWhite
                            )
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Sort",
                                tint = TextWhite
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sort by Name") },
                                onClick = {
                                    viewModel.updateSortOption(SortOption.NAME)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by CPU") },
                                onClick = {
                                    viewModel.updateSortOption(SortOption.CPU)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by RAM") },
                                onClick = {
                                    viewModel.updateSortOption(SortOption.RAM)
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Priority") },
                                onClick = {
                                    viewModel.updateSortOption(SortOption.PRIORITY)
                                    menuExpanded = false
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Safe Kill") },
                                onClick = {
                                    viewModel.scanForCandidates()
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Search") },
                                onClick = {
                                    isSearchActive = true
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                )
            }
        },
        containerColor = DarkBackground,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) 
    ) { paddingValues ->
        val navBarsPadding = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            // Dynamic Global Header
            ProcessListHeader(totalCpu, totalRamUsed, totalRamSize)

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = navBarsPadding.calculateBottomPadding() + 16.dp,
                    start = navBarsPadding.calculateStartPadding(layoutDirection),
                    end = navBarsPadding.calculateEndPadding(layoutDirection)
                )
            ) {
                items(processList) { process ->
                    Box(modifier = Modifier.clickable { onProcessClick(process.pid) }) {
                        ProcessRowItem(process = process)
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessListHeader(
    totalCpu: Double,
    totalRamUsed: Long,
    totalRamSize: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp) // Fixed height
                .padding(start = 16.dp, end = 0.dp), // Padding matches Row Item except right side handled by cells
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Name Column (Matches weight of RowItem)
            Text(
                text = "Name",
                color = TextGrey,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // CPU Header Cell
            val cpuAlpha = (totalCpu / 100.0).toFloat().coerceIn(0.0f, 1.0f)
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .fillMaxHeight()
                    .background(HeatmapColor.copy(alpha = cpuAlpha)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = "CPU",
                        color = TextGrey,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", totalCpu)}%",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // RAM Header Cell
            val ramAlpha = (totalRamUsed.toDouble() / totalRamSize.toDouble()).toFloat().coerceIn(0.0f, 1.0f)
            val ramGb = totalRamUsed / (1024.0 * 1024.0 * 1024.0)
            
            Box(
                modifier = Modifier
                    .width(90.dp) // Matches RowItem
                    .fillMaxHeight()
                    .background(HeatmapColor.copy(alpha = ramAlpha)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = "Memory",
                        color = TextGrey,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f GB", ramGb),
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Divider(color = DividerGrey, thickness = 1.dp)
    }
}
