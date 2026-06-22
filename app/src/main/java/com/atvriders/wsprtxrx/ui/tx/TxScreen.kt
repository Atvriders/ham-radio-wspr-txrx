package com.atvriders.wsprtxrx.ui.tx

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.atvriders.wsprtxrx.R
import com.atvriders.wsprtxrx.core.wspr.WsprMessage
import com.atvriders.wsprtxrx.ui.Format
import com.atvriders.wsprtxrx.ui.TxPhase
import com.atvriders.wsprtxrx.ui.TxViewModel

@Composable
fun TxScreen(
    vm: TxViewModel,
    licenceAcknowledged: Boolean,
    onAcknowledgeLicence: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    // Keep the screen on during the time-critical wait/transmit so the slot isn't missed.
    val active = ui.phase == TxPhase.WAITING || ui.phase == TxPhase.TRANSMITTING
    DisposableEffect(active) {
        if (active) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) lastLocation(context)?.let { vm.fillGridFromLocation(it.first, it.second) }
    }

    // B4: show a one-time in-context rationale before the system location dialog.
    var showLocationRationale by remember { mutableStateOf(false) }
    if (showLocationRationale) {
        AlertDialog(
            onDismissRequest = { showLocationRationale = false },
            title = { Text(stringResource(R.string.tx_location_title)) },
            text = { Text(stringResource(R.string.tx_location_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showLocationRationale = false
                    locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) { Text(stringResource(R.string.action_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { showLocationRationale = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // One-time amateur-licence acknowledgement gate before the first-ever transmit.
    var showLicenceGate by remember { mutableStateOf(false) }
    if (showLicenceGate) {
        AlertDialog(
            onDismissRequest = { showLicenceGate = false },
            title = { Text(stringResource(R.string.tx_licence_title)) },
            text = { Text(stringResource(R.string.tx_licence_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showLicenceGate = false
                    onAcknowledgeLicence()
                    vm.transmit()
                }) { Text(stringResource(R.string.tx_licence_agree)) }
            },
            dismissButton = {
                TextButton(onClick = { showLicenceGate = false }) {
                    Text(stringResource(R.string.tx_licence_cancel))
                }
            },
        )
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tx_title), fontWeight = FontWeight.Bold)
        // High-contrast safety disclaimer (errorContainer roles meet WCAG in both schemes).
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.tx_disclaimer),
                Modifier.padding(10.dp),
                fontWeight = FontWeight.Medium,
            )
        }

        OutlinedTextField(
            value = ui.callsign,
            onValueChange = vm::setCallsign,
            label = { Text(stringResource(R.string.tx_callsign_label)) },
            isError = ui.callsign.isNotEmpty() && !ui.validCallsign,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ui.grid,
                onValueChange = vm::setGrid,
                label = { Text(stringResource(R.string.tx_grid_label)) },
                isError = ui.grid.isNotEmpty() && !ui.validGrid,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    lastLocation(context)?.let { vm.fillGridFromLocation(it.first, it.second) }
                } else {
                    // Show the rationale first; the system request only fires on Allow.
                    showLocationRationale = true
                }
            }) { Text(stringResource(R.string.tx_gps)) }
        }

        PowerPicker(ui.powerDbm, vm::setPower)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tx_even_minute))
            Switch(checked = ui.waitForEvenMinute, onCheckedChange = vm::setWaitForEvenMinute)
        }

        when (ui.phase) {
            TxPhase.WAITING -> Text(stringResource(R.string.tx_waiting, ui.secondsUntilStart))
            TxPhase.TRANSMITTING -> {
                Text(stringResource(R.string.tx_transmitting))
                LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth())
            }
            TxPhase.DONE -> Text(
                stringResource(R.string.tx_complete),
                color = MaterialTheme.colorScheme.primary,
            )
            TxPhase.IDLE -> {}
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (ui.phase == TxPhase.WAITING || ui.phase == TxPhase.TRANSMITTING) {
            Button(onClick = vm::stop, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.tx_stop))
            }
        } else {
            Button(
                onClick = {
                    // Gate the first-ever transmit behind the licence acknowledgement.
                    if (licenceAcknowledged) vm.transmit() else showLicenceGate = true
                },
                enabled = ui.canTransmit,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.tx_transmit)) }
        }
    }
}

@Composable
private fun PowerPicker(power: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.tx_power, Format.powerLabel(power)))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WsprMessage.VALID_POWERS.forEach { p ->
                DropdownMenuItem(
                    text = { Text("$p dBm  ·  ${Format.watts(p)}") },
                    onClick = { onSelect(p); expanded = false },
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun lastLocation(context: Context): Pair<Double, Double>? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )
    for (p in providers) {
        val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
        if (loc != null) return loc.latitude to loc.longitude
    }
    return null
}
