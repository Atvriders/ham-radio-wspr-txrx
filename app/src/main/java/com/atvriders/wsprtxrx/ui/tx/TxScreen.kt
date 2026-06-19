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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.atvriders.wsprtxrx.R
import com.atvriders.wsprtxrx.core.wspr.WsprMessage
import com.atvriders.wsprtxrx.ui.TxPhase
import com.atvriders.wsprtxrx.ui.TxViewModel

@Composable
fun TxScreen(vm: TxViewModel) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) lastLocation(context)?.let { vm.fillGridFromLocation(it.first, it.second) }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Transmit WSPR", fontWeight = FontWeight.Bold)
        Surface(color = Color(0x22FFB300), modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResourceCompat(context, R.string.tx_disclaimer),
                Modifier.padding(10.dp),
                color = Color(0xFF8D6E00),
            )
        }

        OutlinedTextField(
            value = ui.callsign,
            onValueChange = vm::setCallsign,
            label = { Text("Callsign") },
            isError = ui.callsign.isNotEmpty() && !ui.validCallsign,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ui.grid,
                onValueChange = vm::setGrid,
                label = { Text("Grid (e.g. FN42)") },
                isError = ui.grid.isNotEmpty() && !ui.validGrid,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    lastLocation(context)?.let { vm.fillGridFromLocation(it.first, it.second) }
                } else {
                    locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }) { Text("GPS") }
        }

        PowerPicker(ui.powerDbm, vm::setPower)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Start on even UTC minute")
            Switch(checked = ui.waitForEvenMinute, onCheckedChange = vm::setWaitForEvenMinute)
        }

        when (ui.phase) {
            TxPhase.WAITING -> Text("Waiting for slot — starting in ${ui.secondsUntilStart}s…")
            TxPhase.TRANSMITTING -> {
                Text("Transmitting…")
                LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth())
            }
            TxPhase.DONE -> Text("Transmission complete.", color = Color(0xFF2E7D32))
            TxPhase.IDLE -> {}
        }
        ui.error?.let { Text(it, color = Color(0xFFC62828)) }

        if (ui.phase == TxPhase.WAITING || ui.phase == TxPhase.TRANSMITTING) {
            Button(onClick = vm::stop, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
        } else {
            Button(
                onClick = vm::transmit,
                enabled = ui.canTransmit,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Transmit") }
        }
    }
}

@Composable
private fun PowerPicker(power: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("Power: $power dBm") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WsprMessage.VALID_POWERS.forEach { p ->
                DropdownMenuItem(text = { Text("$p dBm") }, onClick = { onSelect(p); expanded = false })
            }
        }
    }
}

private fun stringResourceCompat(context: Context, id: Int): String = context.getString(id)

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
