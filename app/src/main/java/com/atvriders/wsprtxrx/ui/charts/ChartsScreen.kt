package com.atvriders.wsprtxrx.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atvriders.wsprtxrx.core.Band
import com.atvriders.wsprtxrx.ui.Format
import com.atvriders.wsprtxrx.ui.SpotsViewModel
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun ChartsScreen(vm: SpotsViewModel) {
    val ui by vm.ui.collectAsState()
    val query by vm.query.collectAsState()
    val settings by vm.settings.collectAsState()
    val nowSec = remember(ui.spots) { Instant.now().epochSecond }
    val windowSec = query.timeRangeMinutes * 60L

    val bucketCount = 24
    val buckets = remember(ui.spots, windowSec) {
        ChartData.timeBuckets(ui.spots, nowSec, windowSec, buckets = bucketCount)
    }
    val snr = remember(ui.spots) { ChartData.snrSeries(ui.spots) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Spots over time", fontWeight = FontWeight.Bold)
        SpotCountChart(
            buckets = buckets,
            bandColors = settings.bandColorOverrides,
            startSec = nowSec - windowSec,
            nowSec = nowSec,
            windowMinutes = query.timeRangeMinutes,
            bucketCount = bucketCount,
        )

        HorizontalDivider()
        Text("SNR over time", fontWeight = FontWeight.Bold)
        SnrChart(snr, settings.bandColorOverrides)

        HorizontalDivider()
        Head2HeadSection(vm)
    }
}

private val AXIS_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm'Z'")

private fun formatWindow(minutes: Int): String = when {
    minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)}d"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes}m"
}

private fun axisTime(epochSec: Long): String =
    Instant.ofEpochSecond(epochSec).atZone(ZoneOffset.UTC).format(AXIS_TIME_FMT)

@Composable
private fun SpotCountChart(
    buckets: List<TimeBucket>,
    bandColors: Map<String, Long>,
    startSec: Long,
    nowSec: Long,
    windowMinutes: Int,
    bucketCount: Int,
) {
    val windowLabel = formatWindow(windowMinutes)
    val bucketLabel = formatWindow((windowMinutes / bucketCount.coerceAtLeast(1)).coerceAtLeast(1))
    if (buckets.isEmpty() || buckets.all { it.total == 0 }) {
        Text("No spots in range", color = Color.Gray)
        Text(
            "Spots per $bucketLabel over last $windowLabel",
            color = Color.Gray,
            fontSize = 11.sp,
        )
        return
    }
    val maxTotal = (buckets.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
    val gridColor = Color.LightGray.copy(alpha = 0.5f)

    Row(Modifier.fillMaxWidth().height(160.dp)) {
        // Y-axis: max at top, 0 at bottom.
        Column(
            Modifier.fillMaxHeight().padding(end = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = androidx.compose.ui.Alignment.End,
        ) {
            Text("$maxTotal", color = Color.Gray, fontSize = 11.sp)
            Text("0", color = Color.Gray, fontSize = 11.sp)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            // Horizontal gridlines at 0/50/100% (and the baseline).
            for (frac in listOf(0f, 0.5f, 1f)) {
                val y = frac * size.height
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
            val barW = size.width / buckets.size
            buckets.forEachIndexed { i, bucket ->
                var yTop = size.height
                bucket.countsByBand.forEach { (band, count) ->
                    if (count <= 0) return@forEach
                    // Proportional height, but at least 2px so non-zero buckets stay visible.
                    val h = ((count.toFloat() / maxTotal) * size.height).coerceAtLeast(2f)
                    yTop -= h
                    drawRect(
                        color = Format.bandColor(band, bandColors),
                        topLeft = Offset(i * barW + barW * 0.1f, yTop),
                        size = Size(barW * 0.8f, h),
                    )
                }
            }
        }
    }
    // X-axis: start time (left) to "now" (right).
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(axisTime(startSec), color = Color.Gray, fontSize = 11.sp)
        Text("now (${axisTime(nowSec)})", color = Color.Gray, fontSize = 11.sp)
    }
    Text(
        "Spots per $bucketLabel over last $windowLabel (UTC)",
        color = Color.Gray,
        fontSize = 11.sp,
    )
    BandLegend(buckets.flatMap { it.countsByBand.keys }.distinct(), bandColors)
}

@Composable
private fun SnrChart(points: List<SnrPoint>, bandColors: Map<String, Long>) {
    if (points.size < 2) {
        Text("Not enough data", color = Color.Gray); return
    }
    val minSnr = points.minOf { it.snr }.toFloat()
    val maxSnr = points.maxOf { it.snr }.toFloat()
    val range = (maxSnr - minSnr).coerceAtLeast(1f)
    val tMin = points.first().timeSec
    val tMax = points.last().timeSec
    val tRange = (tMax - tMin).toFloat().coerceAtLeast(1f)
    val gridColor = Color.LightGray.copy(alpha = 0.5f)

    Row(Modifier.fillMaxWidth().height(160.dp)) {
        Column(
            Modifier.fillMaxHeight().padding(end = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = androidx.compose.ui.Alignment.End,
        ) {
            Text("${maxSnr.toInt()} dB", color = Color.Gray, fontSize = 11.sp)
            Text("${minSnr.toInt()} dB", color = Color.Gray, fontSize = 11.sp)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            for (frac in listOf(0f, 0.5f, 1f)) {
                val y = frac * size.height
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
            points.forEach { p ->
                val x = ((p.timeSec - tMin).toFloat() / tRange) * size.width
                val y = size.height - ((p.snr - minSnr) / range) * size.height
                drawCircle(
                    color = Format.bandColor(p.band, bandColors),
                    radius = 3f,
                    center = Offset(x, y),
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(axisTime(tMin), color = Color.Gray, fontSize = 11.sp)
        Text(axisTime(tMax), color = Color.Gray, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandLegend(bands: List<Band>, bandColors: Map<String, Long>) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        bands.forEach { band ->
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(Format.bandColor(band, bandColors)),
                )
                Text(band.label, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun Head2HeadSection(vm: SpotsViewModel) {
    val ui by vm.ui.collectAsState()
    val receivers = remember(ui.spots) { ChartData.receivers(ui.spots) }
    var a by remember { mutableStateOf<String?>(null) }
    var b by remember { mutableStateOf<String?>(null) }

    Text("Head2Head — receiver comparison", fontWeight = FontWeight.Bold)
    Text(
        "Compare two receivers on the transmissions both heard (no averaging).",
        color = Color.Gray,
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StationPicker("Station A", a, receivers) { a = it }
        StationPicker("Station B", b, receivers) { b = it }
    }
    val rows = remember(ui.spots, a, b) {
        if (a != null && b != null) ChartData.head2head(ui.spots, a!!, b!!) else emptyList()
    }
    if (a != null && b != null) {
        if (rows.isEmpty()) {
            Text("No transmissions heard by both.", color = Color.Gray)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TX", fontWeight = FontWeight.SemiBold)
                Text("$a", fontWeight = FontWeight.SemiBold)
                Text("$b", fontWeight = FontWeight.SemiBold)
                Text("Δ", fontWeight = FontWeight.SemiBold)
            }
            rows.take(50).forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(r.txCall, fontFamily = FontFamily.Monospace)
                    Text("${r.snrA}", color = Format.snrColor(r.snrA))
                    Text("${r.snrB}", color = Format.snrColor(r.snrB))
                    Text("${r.snrA - r.snrB}", fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun StationPicker(label: String, selected: String?, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected ?: label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.take(100).forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
            }
        }
    }
}
