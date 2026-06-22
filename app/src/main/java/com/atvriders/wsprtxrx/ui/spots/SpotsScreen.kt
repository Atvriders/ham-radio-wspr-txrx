package com.atvriders.wsprtxrx.ui.spots

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.atvriders.wsprtxrx.data.model.Spot
import com.atvriders.wsprtxrx.ui.Format
import com.atvriders.wsprtxrx.ui.QueryControls
import com.atvriders.wsprtxrx.ui.SpotSort
import com.atvriders.wsprtxrx.ui.SpotsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotsScreen(vm: SpotsViewModel, widthSizeClass: WindowWidthSizeClass) {
    val ui by vm.ui.collectAsState()
    val query by vm.query.collectAsState()
    val settings by vm.settings.collectAsState()
    val twoPane = widthSizeClass != WindowWidthSizeClass.Compact

    Column(Modifier.fillMaxSize()) {
        QueryControls(
            query = query,
            recentCalls = settings.recentCalls,
            onQueryChange = { newQuery -> vm.updateQuery { newQuery } },
            onSearch = { vm.search() },
        )
        if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        com.atvriders.wsprtxrx.ui.ErrorBanner(ui.error, onRetry = { vm.search() })
        FailuresBanner(ui.failures.map { it.source.label })
        SortRow(ui.sort, vm::setSort)
        HorizontalDivider()

        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                SpotList(
                    spots = ui.spots,
                    selected = ui.selected,
                    useMiles = settings.useMiles,
                    bandColors = settings.bandColorOverrides,
                    onSelect = vm::select,
                    modifier = Modifier.weight(1f),
                )
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxSize()) {
                    val sel = ui.selected
                    if (sel == null) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text("Select a spot", color = Color.Gray)
                        }
                    } else {
                        SpotDetail(sel, ui, settings.useMiles, onSearchCall = vm::searchCallsign)
                    }
                }
            }
        } else {
            SpotList(
                spots = ui.spots,
                selected = null,
                useMiles = settings.useMiles,
                bandColors = settings.bandColorOverrides,
                onSelect = vm::select,
                modifier = Modifier.fillMaxSize(),
            )
            if (ui.selected != null) {
                ModalBottomSheet(onDismissRequest = { vm.select(null) }) {
                    SpotDetail(ui.selected!!, ui, settings.useMiles, onSearchCall = {
                        vm.select(null); vm.searchCallsign(it)
                    })
                }
            }
        }
    }
}

@Composable
private fun FailuresBanner(failedSources: List<String>) {
    if (failedSources.isEmpty()) return
    Surface(color = Color(0x33FF5252), modifier = Modifier.fillMaxWidth()) {
        Text(
            "Unavailable: ${failedSources.joinToString(", ")}",
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = Color(0xFFB71C1C),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortRow(sort: SpotSort, onSort: (SpotSort) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SpotSort.entries.forEach { s ->
            FilterChip(
                selected = sort == s,
                onClick = { onSort(s) },
                label = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}

@Composable
private fun SpotList(
    spots: List<Spot>,
    selected: Spot?,
    useMiles: Boolean,
    bandColors: Map<String, Long>,
    onSelect: (Spot) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (spots.isEmpty()) {
        Box(modifier.fillMaxSize(), Alignment.Center) { Text("No spots", color = Color.Gray) }
        return
    }
    LazyColumn(modifier) {
        items(spots, key = { it.dedupKey() + it.source.name }) { spot ->
            SpotRow(spot, spot == selected, useMiles, bandColors, onSelect)
            HorizontalDivider()
        }
    }
}

@Composable
private fun SpotRow(
    spot: Spot,
    isSelected: Boolean,
    useMiles: Boolean,
    bandColors: Map<String, Long>,
    onSelect: (Spot) -> Unit,
) {
    val bg = if (isSelected) Color(0x223F51B5) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable { onSelect(spot) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Format.bandColor(spot.band, bandColors)),
        )
        Column(Modifier.weight(1f)) {
            Text(Format.spotTitle(spot), fontWeight = FontWeight.SemiBold)
            Text(
                "${spot.band?.label ?: "?"} · ${Format.freqMHz(spot.freqHz)} MHz · ${spot.source.label}",
                fontFamily = FontFamily.Monospace,
                color = Color.Gray,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${spot.snr} dB", color = Format.snrColor(spot.snr), fontWeight = FontWeight.Bold)
            Text(Format.distance(spot.distanceKm, useMiles), color = Color.Gray)
            Text(Format.timeUtc(spot.timeUtc), color = Color.Gray, fontFamily = FontFamily.Monospace)
        }
    }
}
