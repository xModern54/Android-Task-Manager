package com.example.taskmanager.ui.screens.processlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskmanager.ui.components.process.ProcessListHeader
import com.example.taskmanager.ui.components.process.ProcessRowItem
import com.example.taskmanager.ui.theme.DarkBackground
import com.example.taskmanager.ui.theme.DarkSurface
import com.example.taskmanager.ui.theme.TextWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessListScreen(
    viewModel: ProcessListViewModel = viewModel()
) {
    val processList by viewModel.processList.collectAsState()
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Manager", color = TextWhite) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        containerColor = DarkBackground,
        // We handle insets manually to allow LazyColumn to scroll behind navigation bar
        contentWindowInsets = WindowInsets(0, 0, 0, 0) 
    ) { paddingValues ->
        // paddingValues here will be mostly empty because of WindowInsets(0,0,0,0)
        // But Scaffold still adds TopAppBar height to it.
        
        val navBarsPadding = WindowInsets.navigationBars.asPaddingValues()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            // Fixed Header
            ProcessListHeader()

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground),
                contentPadding = PaddingValues(
                    bottom = navBarsPadding.calculateBottomPadding() + 16.dp,
                    start = navBarsPadding.calculateStartPadding(layoutDirection),
                    end = navBarsPadding.calculateEndPadding(layoutDirection)
                )
            ) {
                items(processList) { process ->
                    ProcessRowItem(process = process)
                }
            }
        }
    }
}
