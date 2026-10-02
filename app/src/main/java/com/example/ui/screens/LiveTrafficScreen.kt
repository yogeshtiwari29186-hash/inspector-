package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TrafficState
import com.example.ui.components.RequestCard
import com.example.ui.navigation.Routes
import com.example.viewmodel.TrafficViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTrafficScreen(
    viewModel: TrafficViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (String) -> Unit
) {
    val records by viewModel.trafficRecords.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<TrafficState?>(null) }

    val filteredRecords = records.filter { rec ->
        val matchesSearch = if (searchQuery.isBlank()) true else {
            rec.request.url.contains(searchQuery, ignoreCase = true) ||
                    rec.request.method.contains(searchQuery, ignoreCase = true) ||
                    rec.id.contains(searchQuery, ignoreCase = true)
        }
        val matchesFilter = when (selectedFilter) {
            null -> true
            TrafficState.WAITING -> rec.state == TrafficState.WAITING || rec.state == TrafficState.EDITING
            TrafficState.MODIFIED -> rec.isModified || rec.state == TrafficState.MODIFIED
            else -> rec.state == selectedFilter
        }
        matchesSearch && matchesFilter
    }

    Scaffold(
        containerColor = Color(0xFF020617),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Live Traffic", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "${records.size} captured requests",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.clearLiveTraffic() },
                        modifier = Modifier.testTag("clear_traffic_button")
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear Live Traffic",
                            tint = Color(0xFFEF4444)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by URL, method, or ID...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color(0xFF94A3B8))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("traffic_search_field"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { selectedFilter = null },
                    label = { Text("All (${records.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2563EB),
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = selectedFilter == TrafficState.WAITING,
                    onClick = { selectedFilter = if (selectedFilter == TrafficState.WAITING) null else TrafficState.WAITING },
                    label = { Text("Waiting (${records.count { it.state == TrafficState.WAITING || it.state == TrafficState.EDITING }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF59E0B),
                        selectedLabelColor = Color.Black
                    )
                )

                FilterChip(
                    selected = selectedFilter == TrafficState.FORWARDED,
                    onClick = { selectedFilter = if (selectedFilter == TrafficState.FORWARDED) null else TrafficState.FORWARDED },
                    label = { Text("Forwarded (${records.count { it.state == TrafficState.FORWARDED }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = selectedFilter == TrafficState.MODIFIED,
                    onClick = { selectedFilter = if (selectedFilter == TrafficState.MODIFIED) null else TrafficState.MODIFIED },
                    label = { Text("Modified (${records.count { it.isModified }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFA855F7),
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = selectedFilter == TrafficState.BLOCKED,
                    onClick = { selectedFilter = if (selectedFilter == TrafficState.BLOCKED) null else TrafficState.BLOCKED },
                    label = { Text("Blocked (${records.count { it.state == TrafficState.BLOCKED }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEF4444),
                        selectedLabelColor = Color.White
                    )
                )

                FilterChip(
                    selected = selectedFilter == TrafficState.ERROR,
                    onClick = { selectedFilter = if (selectedFilter == TrafficState.ERROR) null else TrafficState.ERROR },
                    label = { Text("Errors (${records.count { it.state == TrafficState.ERROR }})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF43F5E),
                        selectedLabelColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // List of requests
            if (filteredRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (records.isEmpty()) "Waiting for network traffic..." else "No matching requests found",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (records.isEmpty())
                                "Send requests from your debug app or tap a test button on Dashboard"
                            else
                                "Try changing your search term or filter",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredRecords, key = { it.id }) { record ->
                        RequestCard(
                            record = record,
                            onClick = { onNavigateToDetails(record.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}
