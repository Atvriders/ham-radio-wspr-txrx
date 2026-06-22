package com.atvriders.wsprtxrx.ui

import androidx.annotation.StringRes
import com.atvriders.wsprtxrx.R
import com.atvriders.wsprtxrx.data.model.Direction
import com.atvriders.wsprtxrx.ui.theme.ThemeMode

/**
 * Maps the app's display enums to localized string resources, keeping the enum
 * definitions themselves free of UI/resource dependencies. Used by the filter, sort and
 * appearance controls so the UI never renders raw enum names (e.g. "BOTH").
 */

@get:StringRes
val Direction.labelRes: Int
    get() = when (this) {
        Direction.TX -> R.string.direction_tx
        Direction.RX -> R.string.direction_rx
        Direction.BOTH -> R.string.direction_both
    }

@get:StringRes
val SpotSort.labelRes: Int
    get() = when (this) {
        SpotSort.TIME -> R.string.sort_time
        SpotSort.SNR -> R.string.sort_snr
        SpotSort.DISTANCE -> R.string.sort_distance
        SpotSort.BAND -> R.string.sort_band
    }

@get:StringRes
val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
