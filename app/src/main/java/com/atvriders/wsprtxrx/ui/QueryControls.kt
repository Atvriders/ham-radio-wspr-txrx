package com.atvriders.wsprtxrx.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.atvriders.wsprtxrx.core.Band
import com.atvriders.wsprtxrx.data.model.Direction
import com.atvriders.wsprtxrx.data.model.SpotQuery

/**
 * Search + filter bar shared by Spots, Map and Charts. The search field accepts a
 * callsign or grid; the filter button opens a sheet with band/time/distance/power/
 * direction controls.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QueryControls(
    query: SpotQuery,
    recentCalls: List<String>,
    onQueryChange: (SpotQuery) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(query.callsign, query.grid) {
        mutableStateOf(query.callsign ?: query.grid ?: "")
    }
    var showFilters by remember { mutableStateOf(false) }

    val activeFilters = listOfNotNull(
        query.bands.isNotEmpty().takeIf { it },
        (query.maxDistanceKm != null).takeIf { it },
        (query.maxPowerDbm != null).takeIf { it },
        (query.direction != Direction.BOTH).takeIf { it },
        query.uniqueOnly.takeIf { it },
    ).size

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.uppercase() },
            label = { Text("Callsign or grid") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onQueryChange(applyText(query, text))
                onSearch()
            }),
            modifier = Modifier.weight(1f),
        )
        BadgedBox(
            badge = { if (activeFilters > 0) Badge { Text("$activeFilters") } },
        ) {
            IconButton(onClick = { showFilters = true }) {
                Icon(Icons.Filled.FilterList, contentDescription = "Filters")
            }
        }
    }

    if (recentCalls.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            recentCalls.take(8).forEach { call ->
                AssistChip(
                    onClick = {
                        text = call
                        onQueryChange(query.copy(callsign = call, grid = null))
                        onSearch()
                    },
                    label = { Text(call) },
                )
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            FilterSheet(
                query = query,
                onQueryChange = onQueryChange,
                onApply = {
                    showFilters = false
                    onSearch()
                },
            )
        }
    }
}

private fun applyText(query: SpotQuery, text: String): SpotQuery {
    val t = text.trim()
    // A grid is letters+digits with no slash and looks like "FN42"; otherwise treat as call.
    val looksLikeGrid = t.length in 4..6 && t[0].isLetter() && t[1].isLetter() &&
        t[2].isDigit() && t[3].isDigit()
    return if (t.isEmpty()) query.copy(callsign = null, grid = null)
    else if (looksLikeGrid) query.copy(grid = t, callsign = null)
    else query.copy(callsign = t, grid = null)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    query: SpotQuery,
    onQueryChange: (SpotQuery) -> Unit,
    onApply: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Bands")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Band.ordered.forEach { band ->
                FilterChip(
                    selected = band in query.bands,
                    onClick = {
                        val bands = query.bands.toMutableSet()
                        if (!bands.add(band)) bands.remove(band)
                        onQueryChange(query.copy(bands = bands))
                    },
                    label = { Text(band.label) },
                )
            }
        }

        Text("Time range")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 30, 60, 120).forEach { minutes ->
                FilterChip(
                    selected = query.timeRangeMinutes == minutes,
                    onClick = { onQueryChange(query.copy(timeRangeMinutes = minutes)) },
                    label = { Text(if (minutes < 60) "${minutes}m" else "${minutes / 60}h") },
                )
            }
        }

        Text("Direction")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Direction.entries.forEach { dir ->
                FilterChip(
                    selected = query.direction == dir,
                    onClick = { onQueryChange(query.copy(direction = dir)) },
                    label = { Text(dir.name) },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Unique spots only")
            Switch(
                checked = query.uniqueOnly,
                onCheckedChange = { onQueryChange(query.copy(uniqueOnly = it)) },
            )
        }

        androidx.compose.material3.Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply") }
    }
}
