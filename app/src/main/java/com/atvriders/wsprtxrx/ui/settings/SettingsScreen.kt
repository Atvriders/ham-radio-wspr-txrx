package com.atvriders.wsprtxrx.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atvriders.wsprtxrx.core.Band
import com.atvriders.wsprtxrx.data.model.SourceId
import com.atvriders.wsprtxrx.ui.Format
import com.atvriders.wsprtxrx.ui.SettingsViewModel
import com.atvriders.wsprtxrx.ui.theme.ThemeMode

private val PALETTE = listOf(
    0xFFD32F2F, 0xFFF57C00, 0xFFFBC02D, 0xFF7CB342, 0xFF26A69A,
    0xFF1E88E5, 0xFF3949AB, 0xFF8E24AA, 0xFF6D4C41, 0xFF546E7A,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsState()
    var qrzUser by remember(settings.qrzUsername) { mutableStateOf(settings.qrzUsername) }
    var qrzPass by remember(settings.qrzPassword) { mutableStateOf(settings.qrzPassword) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Section("Data sources")
        SourceId.entries.forEach { src ->
            SwitchRow(src.label, src in settings.enabledSources) { vm.toggleSource(src, it) }
        }

        HorizontalDivider()
        Section("QRZ.com login")
        Text("Optional. Paid accounts get the most detail in spot lookups.", color = Color.Gray)
        OutlinedTextField(qrzUser, { qrzUser = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            qrzPass, { qrzPass = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.setQrz(qrzUser, qrzPass) }) { Text("Save QRZ login") }

        HorizontalDivider()
        Section("Default time range")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 30, 60, 120).forEach { m ->
                FilterChip(
                    selected = settings.defaultTimeRangeMinutes == m,
                    onClick = { vm.setTimeRange(m) },
                    label = { Text(if (m < 60) "${m}m" else "${m / 60}h") },
                )
            }
        }

        HorizontalDivider()
        Section("Appearance")
        SwitchRow("Use miles", settings.useMiles) { vm.setUseMiles(it) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { vm.setTheme(mode) },
                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        HorizontalDivider()
        Section("Band colours")
        Button(onClick = { vm.resetBandColors() }) { Text("Reset to defaults") }
        Band.ordered.forEach { band ->
            BandColorRow(band, settings.bandColorOverrides) { color -> vm.setBandColor(band.name, color) }
        }

        if (settings.recentCalls.isNotEmpty()) {
            HorizontalDivider()
            Section("Recent callsigns")
            settings.recentCalls.forEach { call ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(call)
                    IconButton(onClick = { vm.removeRecentCall(call) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandColorRow(band: Band, overrides: Map<String, Long>, onPick: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(band.label)
            Box(Modifier.size(22.dp).clip(CircleShape).background(Format.bandColor(band, overrides)))
        }
        if (expanded) {
            FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PALETTE.forEach { argb ->
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .clickable { onPick(argb); expanded = false },
                    )
                }
            }
        }
    }
}
