package com.atvriders.wsprtxrx.ui.spots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atvriders.wsprtxrx.data.model.Spot
import com.atvriders.wsprtxrx.ui.Format
import com.atvriders.wsprtxrx.ui.SpotsUiState

@Composable
fun SpotDetail(
    spot: Spot,
    ui: SpotsUiState,
    useMiles: Boolean,
    onSearchCall: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(Format.spotTitle(spot), fontWeight = FontWeight.Bold)
        HorizontalDivider()

        DetailRow("Transmitter", "${spot.txCall}  ${spot.txGrid ?: ""}")
        DetailRow("Receiver", "${spot.rxCall}  ${spot.rxGrid ?: ""}")
        DetailRow("Band", spot.band?.label ?: "?")
        DetailRow("Frequency", "${Format.freqMHz(spot.freqHz)} MHz")
        DetailRow("SNR", "${spot.snr} dB")
        DetailRow("Drift", "${spot.drift} Hz")
        spot.powerDbm?.let { DetailRow("Power", "$it dBm") }
        DetailRow("Distance", Format.distance(spot.distanceKm, useMiles))
        DetailRow("Bearing", Format.azimuth(spot.azimuthDeg))
        DetailRow("Time", Format.timeUtc(spot.timeUtc))
        DetailRow("Source", spot.source.label)
        if (spot.mode.isNotBlank()) DetailRow("Mode", spot.mode)

        HorizontalDivider()
        Text("QRZ lookup — ${spot.txCall}", fontWeight = FontWeight.SemiBold)
        when {
            ui.qrzLoading -> CircularProgressIndicator()
            ui.qrz != null -> {
                ui.qrz.name?.let { DetailRow("Name", it) }
                ui.qrz.qth?.let { DetailRow("QTH", it) }
                ui.qrz.grid?.let { DetailRow("Grid", it) }
                ui.qrz.country?.let { DetailRow("Country", it) }
            }
            else -> Text("No QRZ data (set login in Settings for details).", color = androidx.compose.ui.graphics.Color.Gray)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSearchCall(spot.txCall) }) { Text("Search ${spot.txCall}") }
            OutlinedButton(onClick = {
                uriHandler.openUri("https://www.qrz.com/db/${spot.txCall}")
            }) { Text("QRZ.com") }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = androidx.compose.ui.graphics.Color.Gray)
        Text(value, fontFamily = FontFamily.Monospace)
    }
}
