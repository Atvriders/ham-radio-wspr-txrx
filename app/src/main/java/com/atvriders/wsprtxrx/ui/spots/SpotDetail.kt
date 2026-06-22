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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atvriders.wsprtxrx.R
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

        DetailRow(stringResource(R.string.detail_transmitter), "${spot.txCall}  ${spot.txGrid ?: ""}")
        DetailRow(stringResource(R.string.detail_receiver), "${spot.rxCall}  ${spot.rxGrid ?: ""}")
        DetailRow(stringResource(R.string.detail_band), spot.band?.label ?: stringResource(R.string.band_unknown))
        DetailRow(stringResource(R.string.detail_frequency), stringResource(R.string.detail_frequency_mhz, Format.freqMHz(spot.freqHz)))
        DetailRow(stringResource(R.string.detail_snr), stringResource(R.string.unit_db, spot.snr))
        DetailRow(stringResource(R.string.detail_drift), stringResource(R.string.detail_drift_hz, spot.drift))
        spot.powerDbm?.let { DetailRow(stringResource(R.string.detail_power), stringResource(R.string.detail_power_dbm, it)) }
        DetailRow(stringResource(R.string.detail_distance), Format.distance(spot.distanceKm, useMiles))
        DetailRow(stringResource(R.string.detail_bearing), Format.azimuth(spot.azimuthDeg))
        DetailRow(stringResource(R.string.detail_time), Format.timeUtc(spot.timeUtc))
        DetailRow(stringResource(R.string.detail_source), spot.source.label)
        if (spot.mode.isNotBlank()) DetailRow(stringResource(R.string.detail_mode), spot.mode)

        HorizontalDivider()
        Text(stringResource(R.string.detail_qrz_lookup, spot.txCall), fontWeight = FontWeight.SemiBold)
        when {
            ui.qrzLoading -> CircularProgressIndicator()
            ui.qrz != null -> {
                ui.qrz.name?.let { DetailRow(stringResource(R.string.detail_name), it) }
                ui.qrz.qth?.let { DetailRow(stringResource(R.string.detail_qth), it) }
                ui.qrz.grid?.let { DetailRow(stringResource(R.string.detail_grid), it) }
                ui.qrz.country?.let { DetailRow(stringResource(R.string.detail_country), it) }
            }
            else -> Text(
                stringResource(R.string.detail_no_qrz),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSearchCall(spot.txCall) }) {
                Text(stringResource(R.string.detail_search_call, spot.txCall))
            }
            OutlinedButton(onClick = {
                uriHandler.openUri("https://www.qrz.com/db/${spot.txCall}")
            }) { Text(stringResource(R.string.detail_qrz_com)) }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace)
    }
}
