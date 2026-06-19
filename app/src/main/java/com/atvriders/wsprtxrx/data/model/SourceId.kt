package com.atvriders.wsprtxrx.data.model

/** Identifies which network a spot came from. */
enum class SourceId(val label: String) {
    WSPR_LIVE("wspr.live"),
    PSK_REPORTER("PSKReporter"),
    RBN("Reverse Beacon Network"),
}
