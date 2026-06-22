package com.atvriders.wsprtxrx.ui.map

import android.graphics.PointF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.atvriders.wsprtxrx.data.model.Spot
import com.atvriders.wsprtxrx.data.prefs.AppSettings
import com.atvriders.wsprtxrx.ui.QueryControls
import com.atvriders.wsprtxrx.ui.SpotsViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import com.atvriders.wsprtxrx.core.SolarTerminator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val SRC_POINTS = "spot-points"
private const val SRC_LINES = "spot-lines"
private const val SRC_TERM = "terminator"

/** A station picked by tapping its marker, surfaced in the info popup. */
data class SelectedStation(
    val call: String,
    val role: String,
    val spots: Int,
    val lat: Double,
    val lon: Double,
    val grid: String?,
)

@Composable
fun MapScreen(vm: SpotsViewModel, settings: AppSettings) {
    val ui by vm.ui.collectAsState()
    val query by vm.query.collectAsState()

    Column(Modifier.fillMaxSize()) {
        QueryControls(
            query = query,
            recentCalls = settings.recentCalls,
            onQueryChange = { newQuery -> vm.updateQuery { newQuery } },
            onSearch = { vm.search() },
        )
        Box(Modifier.fillMaxSize()) {
            SpotMap(
                spots = ui.spots,
                bandColors = settings.bandColorOverrides,
                onFilter = { call -> vm.searchCallsign(call) },
            )
        }
    }
}

@Composable
private fun SpotMap(
    spots: List<Spot>,
    bandColors: Map<String, Long>,
    onFilter: (String) -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var pointSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var lineSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var termSource by remember { mutableStateOf<GeoJsonSource?>(null) }
    var selectedStation by remember { mutableStateOf<SelectedStation?>(null) }

    // Fetch the style and inject a globe projection; null means fall back to the
    // plain (mercator) style URL, so the map always works even if this fails.
    var styleSpec by remember { mutableStateOf<StyleSpec?>(null) }
    LaunchedEffect(Unit) { styleSpec = StyleSpec(globeStyleJson()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }) { mv ->
            val spec = styleSpec ?: return@AndroidView // wait until the style decision is made
            mv.getMapAsync { map ->
                if (map.style == null) {
                    map.cameraPosition = CameraPosition.Builder().target(LatLng(20.0, 0.0)).zoom(1.0).build()
                    val builder = spec.json?.let { Style.Builder().fromJson(it) }
                        ?: Style.Builder().fromUri(STYLE_URL)
                    map.setStyle(builder) { style ->
                        val ps = GeoJsonSource(SRC_POINTS)
                        val ls = GeoJsonSource(SRC_LINES)
                        val ts = GeoJsonSource(SRC_TERM)
                        style.addSource(ls)
                        style.addSource(ts)
                        style.addSource(ps)
                        style.addLayer(
                            LineLayer("term-layer", SRC_TERM).withProperties(
                                PropertyFactory.lineColor("#888888"),
                                PropertyFactory.lineWidth(1.2f),
                                PropertyFactory.lineOpacity(0.6f),
                            ),
                        )
                        style.addLayer(
                            LineLayer("line-layer", SRC_LINES).withProperties(
                                PropertyFactory.lineColor(Expression.get("color")),
                                PropertyFactory.lineWidth(1.4f),
                                PropertyFactory.lineOpacity(0.5f),
                            ),
                        )
                        style.addLayer(
                            CircleLayer("point-layer", SRC_POINTS).withProperties(
                                PropertyFactory.circleColor(Expression.get("color")),
                                PropertyFactory.circleRadius(5f),
                                PropertyFactory.circleStrokeColor("#FFFFFF"),
                                PropertyFactory.circleStrokeWidth(1f),
                            ),
                        )
                        pointSource = ps
                        lineSource = ls
                        termSource = ts

                        // Tap a marker to surface its info; tapping empty map clears it.
                        map.addOnMapClickListener { latLng ->
                            val pf: PointF = map.projection.toScreenLocation(latLng)
                            val feats = map.queryRenderedFeatures(pf, "point-layer")
                            val f = feats.firstOrNull()
                            if (f != null) {
                                val call = f.getStringProperty("call") ?: return@addOnMapClickListener false
                                selectedStation = SelectedStation(
                                    call = call,
                                    role = f.getStringProperty("role") ?: "Station",
                                    spots = f.getNumberProperty("spots")?.toInt() ?: 0,
                                    lat = f.getNumberProperty("lat")?.toDouble() ?: latLng.latitude,
                                    lon = f.getNumberProperty("lon")?.toDouble() ?: latLng.longitude,
                                    grid = f.getStringProperty("grid")?.takeIf { it.isNotBlank() },
                                )
                                true
                            } else {
                                selectedStation = null
                                false
                            }
                        }
                    }
                }
            }
        }

        selectedStation?.let { station ->
            StationInfoCard(
                station = station,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                onClose = { selectedStation = null },
                onFilter = {
                    onFilter(station.call)
                    selectedStation = null
                },
            )
        }
    }

    LaunchedEffect(spots, pointSource, lineSource, bandColors) {
        pointSource?.setGeoJson(buildPoints(spots))
        lineSource?.setGeoJson(buildLines(spots, bandColors))
    }
    LaunchedEffect(termSource) {
        termSource?.setGeoJson(buildTerminator())
    }
}

@Composable
private fun StationInfoCard(
    station: SelectedStation,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
    onFilter: () -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    station.call,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            Text("${station.role} · ${station.spots} spot${if (station.spots == 1) "" else "s"}")
            val coords = String.format("%.2f, %.2f", station.lat, station.lon)
            Text(
                station.grid?.let { "$it · $coords" } ?: coords,
                fontFamily = FontFamily.Monospace,
            )
            TextButton(onClick = onFilter) {
                Text("Filter spots for ${station.call}")
            }
        }
    }
}

private fun roleColor(isTx: Boolean, isRx: Boolean): String = when {
    isTx && isRx -> "#6A1B9A" // purple — both
    isTx -> "#C62828"         // red — transmitter
    else -> "#2E7D32"         // green — receiver
}

private fun roleLabel(isTx: Boolean, isRx: Boolean): String = when {
    isTx && isRx -> "Both (TX & RX)"
    isTx -> "Transmitter"
    else -> "Receiver"
}

private fun buildPoints(spots: List<Spot>): FeatureCollection {
    data class Role(
        var lat: Double,
        var lon: Double,
        var tx: Boolean,
        var rx: Boolean,
        var grid: String?,
        var count: Int,
    )
    val byCall = HashMap<String, Role>()
    for (s in spots) {
        if (s.txLat != null && s.txLon != null) {
            val r = byCall.getOrPut(s.txCall) { Role(s.txLat, s.txLon, false, false, s.txGrid, 0) }
            r.tx = true
            r.count++
            if (r.grid == null) r.grid = s.txGrid
        }
        if (s.rxLat != null && s.rxLon != null) {
            val r = byCall.getOrPut(s.rxCall) { Role(s.rxLat, s.rxLon, false, false, s.rxGrid, 0) }
            r.rx = true
            r.count++
            if (r.grid == null) r.grid = s.rxGrid
        }
    }
    val features = byCall.map { (call, r) ->
        Feature.fromGeometry(Point.fromLngLat(r.lon, r.lat)).apply {
            addStringProperty("call", call)
            addStringProperty("color", roleColor(r.tx, r.rx))
            addStringProperty("role", roleLabel(r.tx, r.rx))
            addNumberProperty("spots", r.count)
            addNumberProperty("lat", r.lat)
            addNumberProperty("lon", r.lon)
            r.grid?.let { addStringProperty("grid", it) }
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun buildLines(spots: List<Spot>, bandColors: Map<String, Long>): FeatureCollection {
    val features = spots.mapNotNull { s ->
        if (s.txLat == null || s.txLon == null || s.rxLat == null || s.rxLon == null) return@mapNotNull null
        val line = LineString.fromLngLats(
            listOf(Point.fromLngLat(s.txLon, s.txLat), Point.fromLngLat(s.rxLon, s.rxLat)),
        )
        val argb = s.band?.let { bandColors[it.name] ?: it.defaultColor } ?: 0xFF9E9E9E
        Feature.fromGeometry(line).apply { addStringProperty("color", hexColor(argb)) }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun buildTerminator(): FeatureCollection {
    val now = Instant.now().epochSecond
    val pts = SolarTerminator.terminatorPolygon(now, steps = 180)
        .map { Point.fromLngLat(it.lon, it.lat) }
    return FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(LineString.fromLngLats(pts))))
}

private fun hexColor(argb: Long): String {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return String.format("#%02X%02X%02X", r, g, b)
}

/** Wraps the (possibly null) resolved style JSON so we can distinguish "still loading". */
private class StyleSpec(val json: String?)

/**
 * Downloads the base style and injects a globe projection. Returns null on any failure,
 * in which case the caller loads the plain style URL (mercator) instead.
 */
private suspend fun globeStyleJson(): String? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = (URL(STYLE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val obj = org.json.JSONObject(text)
        obj.put("projection", org.json.JSONObject().put("type", "globe"))
        obj.toString()
    }.getOrNull()
}
