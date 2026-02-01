package com.example.taskmanager.ui.screens.processlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.taskmanager.ui.theme.DarkBackground
import com.example.taskmanager.ui.theme.DarkSurface
import com.example.taskmanager.ui.theme.TextGrey
import com.example.taskmanager.ui.theme.TextWhite
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KillReviewScreen(
    viewModel: ProcessListViewModel,
    onBack: () -> Unit
) {
    val candidates by viewModel.killCandidates.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Candidates", color = TextWhite) },
                navigationIcon = {
                    IconButton(onClick = { 
                        viewModel.cancelKillReview()
                        onBack() 
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(WindowInsets.navigationBars.asPaddingValues())
                    .padding(16.dp)
            ) {
                Button(
                    onClick = { viewModel.executeKill(candidates) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F) // Deep Red/Orange accent
                    ),
                    shape = RoundedCornerShape(50) // Oval
                ) {
                    Text("Start Execution", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(candidates) { candidate ->
                KillCandidateRow(candidate = candidate, onToggle = { viewModel.toggleCandidate(candidate.packageName) })
            }
        }
    }
}

@Composable
fun KillCandidateRow(
    candidate: KillCandidate,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .clickable(onClick = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            if (candidate.icon != null) {
                Image(
                    painter = rememberDrawablePainter(drawable = candidate.icon),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = candidate.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = TextGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Checkbox
        Checkbox(
            checked = candidate.isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFFD32F2F),
                uncheckedColor = TextGrey,
                checkmarkColor = TextWhite
            )
        )
    }
}
