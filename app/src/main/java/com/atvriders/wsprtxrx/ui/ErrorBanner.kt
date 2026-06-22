package com.atvriders.wsprtxrx.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.atvriders.wsprtxrx.R

/**
 * A dismissable-less error banner with a Retry action, shown when a whole search fails
 * (no network, DNS, all sources down) — distinct from the "no spots" empty state so a
 * silent failure isn't mistaken for an empty result. Shared across Spots/Map/Charts.
 */
@Composable
fun ErrorBanner(error: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (error == null) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.spots_load_failed_title),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}
