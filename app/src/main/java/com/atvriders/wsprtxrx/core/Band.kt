package com.atvriders.wsprtxrx.core

/**
 * Amateur-radio bands relevant to WSPR, with frequency ranges (Hz) used to classify
 * a spot by its reported frequency, and a default display color (0xAARRGGBB).
 */
enum class Band(
    val label: String,
    val rangeHz: LongRange,
    val defaultColor: Long,
    /** Integer band code used by wspr.live / WSPRnet (MHz floor; -1 = LF, 0 = MF). */
    val wsprLiveCode: Int,
) {
    // 2190m (a.k.a. 2200m): amateur allocation 135.7–137.8 kHz; WSPR TX center is
    // 137.5 kHz (dial 136.0 kHz + 1500 Hz audio). The 135–138 kHz range fully covers the
    // real WSPR sub-band, so it is verified correct and intentionally NOT widened (no real
    // WSPR 2190m signals exist near 139 kHz — that is outside the amateur band).
    LF2190("2190m", 135_000L..138_000L, 0xFF5D4037, -1),
    LF630("630m", 472_000L..479_000L, 0xFF795548, 0),
    M160("160m", 1_800_000L..2_000_000L, 0xFF6D4C41, 1),
    M80("80m", 3_500_000L..4_000_000L, 0xFFD32F2F, 3),
    M60("60m", 5_250_000L..5_450_000L, 0xFFE64A19, 5),
    M40("40m", 7_000_000L..7_300_000L, 0xFFF57C00, 7),
    M30("30m", 10_100_000L..10_150_000L, 0xFFFBC02D, 10),
    M20("20m", 14_000_000L..14_350_000L, 0xFF7CB342, 14),
    M17("17m", 18_068_000L..18_168_000L, 0xFF26A69A, 18),
    M15("15m", 21_000_000L..21_450_000L, 0xFF0097A7, 21),
    M12("12m", 24_890_000L..24_990_000L, 0xFF1E88E5, 24),
    M10("10m", 28_000_000L..29_700_000L, 0xFF3949AB, 28),
    M6("6m", 50_000_000L..54_000_000L, 0xFF8E24AA, 50),
    M4("4m", 70_000_000L..70_500_000L, 0xFFAB47BC, 70),
    M2("2m", 144_000_000L..148_000_000L, 0xFFD81B60, 144);

    companion object {
        /** All bands ordered low to high frequency. */
        val ordered: List<Band> get() = entries.sortedBy { it.rangeHz.first }
    }
}

/** Returns the band containing [hz], or null if it falls outside the known ranges. */
fun bandForFreq(hz: Long): Band? = Band.entries.firstOrNull { hz in it.rangeHz }
