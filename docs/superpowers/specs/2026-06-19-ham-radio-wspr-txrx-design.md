# Ham Radio WSPR TX/RX — Design Spec

**Date:** 2026-06-19
**Status:** Approved (proceeding to implementation plan)
**Platform:** Android (native Kotlin + Jetpack Compose)

A native Android amateur-radio app for working with WSPR (Weak Signal Propagation
Reporter) data. It recreates the feature set of the iOS app *WSPR Watch* (by Peter
Marks, VK2TPM) as an original, independent implementation, and adds a full WSPR
**transmit audio encoder**. RX side: fetch and visualize reception "spots" from
WSPRnet-derived and related networks. TX side: encode a proper WSPR message to
audio for acoustic coupling / VOX into an SSB transceiver (a phone cannot emit RF).

This is a clean-room feature recreation: all code is original. The WSPR protocol is
an open, published specification (K1JT / WSJT-X). No code or assets from the iOS app
are used.

---

## 1. Identity & Targets

| Item | Value |
|---|---|
| App label | **Ham Radio WSPR TX/RX** |
| Application ID | `com.atvriders.wsprtxrx` |
| Repo | `github.com/Atvriders/ham-radio-wspr-txrx` (public, branch `master`) |
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| minSdk | 26 (Android 8.0) |
| compileSdk / targetSdk | 35 |
| Module layout | Single app module (Approach A) |
| DI | Lightweight manual DI via `AppContainer` (no Hilt) |
| Build / distribution | GitHub Actions → installable APK (artifact + Release) |

---

## 2. Architecture (MVVM + repository, pluggable sources)

```
UI (Compose screens)
  → ViewModels (StateFlow / UiState)
    → SpotRepository  (single source of truth; merge + dedup + geometry + cache)
        ├─ WsprLiveSource    (OkHttp, wspr.live ClickHouse HTTP, JSON)
        ├─ PskReporterSource (OkHttp, retrieve API, rate-limited + cached)
        └─ RbnSource         (TCP telnet cluster stream → parsed spots)
    → QrzService          (qrz.com XML API; optional login)
    → Room (spot cache)   → DataStore (settings, recent calls, band colors)
Core (pure Kotlin, fully unit-tested):
  Maidenhead · GreatCircle · SolarTerminator · WsprEncoder · WsprAudio
```

- `AppContainer` constructs singletons (OkHttp, Room, DataStore, repository, services)
  and is held by the `Application`. ViewModels get it via a `ViewModelProvider.Factory`.
- All network/IO is `suspend` on `Dispatchers.IO`; UI observes `StateFlow`.
- Each `SpotSource` is independent and degrades gracefully (returns an error state,
  never crashes the app) so one unreachable network does not break the others.

---

## 3. Domain Model & Data Sources

### 3.1 `Spot`
Fields: `txCall`, `txGrid`, `txLat`, `txLon`, `powerDbm`, `rxCall`, `rxGrid`,
`rxLat`, `rxLon`, `freqHz`, `band` (derived), `snr`, `drift`, `distanceKm`,
`azimuthDeg`, `timeUtc` (epoch seconds), `source` (enum), `mode`.

Geometry (`distanceKm`, `azimuthDeg`, lat/lon) is computed locally from Maidenhead
grids via the `GreatCircle`/`Maidenhead` core, so every source yields uniform data.

### 3.2 `SpotQuery`
`callsign?`, `grid?`, `bands: Set<Band>`, `timeRangeMinutes` (15 / 30 / 60 / 120),
`maxDistanceKm?`, `maxPowerDbm?`, `direction` (TX / RX / BOTH), `uniqueOnly: Boolean`.

### 3.3 Sources behind one interface
```kotlin
interface SpotSource {
    val id: SourceId
    suspend fun query(q: SpotQuery): Result<List<Spot>>
}
```

- **WsprLiveSource (primary).** `GET https://db1.wspr.live/?query=<SQL> FORMAT JSONEachRow`.
  Builds a parameterized SELECT against the `rx` table with WHERE clauses for band,
  callsign (tx or rx), time window, distance, power. Drives list, charts, Head2Head.
- **PskReporterSource.** PSKReporter reception-report retrieve endpoint. **Must honor
  the ~5-minute query rate limit**: cache last response per query key, back off, and
  surface "cached / rate-limited" state rather than spamming. Adds FT8/FT4 density.
- **RbnSource.** Connect to the Reverse Beacon Network telnet cluster
  (`telnet.reversebeacon.net:7000`), log in with a callsign, read the streaming spot
  lines, parse into `Spot`. Streaming model adapted to the `query()` contract by
  collecting a bounded, time-boxed snapshot. **Flagged risk:** no clean REST API; if
  unreachable, returns an error state and the app continues on other sources.

`SpotRepository.search(q)` runs the enabled sources, merges, dedups (by tx+rx+freq+time
bucket), sorts, computes geometry, and caches into Room for offline re-display.

### 3.4 `QrzService`
qrz.com XML API: optional session login (credentials in DataStore), `lookup(call)` →
name/QTH/grid/etc. Used in the spot detail sheet. Degrades to "no QRZ data" without
credentials or on error.

---

## 4. Screens (bottom navigation)

1. **Spots** — sortable table (time / SNR / distance / band). Row tap → detail bottom
   sheet: full spot fields, QRZ lookup, "search this callsign" action, "open on qrz.com".
2. **Map** — MapLibre Native **globe** projection with a free, **no-API-key** vector
   style (OpenFreeMap). Color-coded markers: 🟢 receiver, 🔴 transmitter, 🟣 both;
   great-circle path lines between tx/rx; per-band colors (editable); **grey-line /
   terminator overlay** computed from current sun position. Tap marker → info popup.
3. **Charts** — drawn on Compose `Canvas` (no heavy chart dependency):
   (a) spot count over time, band-colored bars; (b) SNR over time, line. Time axis
   extends to "now" so a stop in reports is visible. **Head2Head:** choose two stations,
   list transmissions heard by both with each station's SNR side by side — **no averaging**.
4. **TX** — callsign / 4–6 char grid / power (dBm) inputs; **GPS auto-grid** from device
   location; encode a real WSPR message; **schedule transmission start to the even UTC
   minute**; play via `AudioTrack`; live countdown + progress bar; prominent notice that
   output is audio for acoustic coupling and that the operator is responsible for
   licensing and any RF.
5. **Settings** — enable/disable data sources; QRZ login; band-color editor; default time
   range; units (km/mi); theme (system/dark/light); manage recently-used callsigns
   (swipe-delete, reorder).

Cross-cutting: a **search bar** (callsign *or* Maidenhead grid) whose value persists
across Spots / Map / Charts, and a **filter panel** (bands, time range, max distance,
max power, unique toggle, direction).

### 4.1 Responsive / adaptive layout (required)

The UI must adapt fluidly to screen size and posture — phones, tablets, and folding
phones (folded and unfolded) — with panels resizing/reflowing dynamically:

- **Navigation** uses `NavigationSuiteScaffold`: bottom navigation bar on **compact**
  width (phone portrait / folded), navigation **rail** on **medium** width, navigation
  **drawer** on **expanded** width (tablet / unfolded).
- **Window size class** (`androidx.compose.material3:material3-window-size-class`)
  drives breakpoints; layouts recompose on configuration/fold changes automatically.
- **Two-pane** layouts on medium/expanded width via the material3-adaptive library:
  - **Spots**: `ListDetailPaneScaffold` — list + spot detail side by side on wide
    screens, single-pane with navigation on compact.
  - **Map**: map + filter/spot panel as a supporting pane on wide screens; full-bleed
    map with a bottom sheet on compact.
- **Fold awareness**: the adaptive library consumes `WindowInfoTracker` so panes avoid
  the hinge and respond to book/tabletop postures.
- All screens use weight/`fillMaxWidth` and size-class checks rather than fixed dp
  widths so content reflows rather than truncates.

Dependencies: `material3-window-size-class`, `material3-adaptive-navigation-suite`,
and `androidx.compose.material3.adaptive:{adaptive,adaptive-layout,adaptive-navigation}`.

---

## 5. TX Encoder (must be exactly correct)

- `WsprEncoder` (pure Kotlin):
  1. Pack callsign + 4-char grid + power(dBm) into the 50-bit WSPR source message.
  2. Rate-1/2, constraint-length K=32 convolutional encoder → 162 bits.
  3. Bit-reversal interleave → 162 positions.
  4. Merge with the 162-symbol sync vector → 162 four-level (0–3) channel symbols.
- `WsprAudio`:
  - 4-FSK, **sample rate 12000 Hz**, **tone spacing 12000/8192 ≈ 1.46484 Hz**, **baud
    12000/8192 ≈ 1.46484** (8192 samples per symbol), total ≈ **110.6 s** for 162 symbols.
  - Configurable audio center frequency (default 1500 Hz, USB).
  - Phase-continuous tone synthesis written to `AudioTrack` (streaming mode).
  - Scheduler aligns symbol 0 to ~1 s past an even UTC minute.
- **Verification:** unit tests assert the generated 162-symbol sequence matches published
  WSPR reference vectors for known (call, grid, power) inputs. TDD this component first.

---

## 6. CI/CD — the APK

`.github/workflows/build.yml`:
1. Checkout, set up JDK 17, set up Android SDK + cache Gradle.
2. Run JVM unit tests (`./gradlew test`).
3. `./gradlew assembleRelease`.
4. **Signing:** if secrets `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` /
   `KEY_PASSWORD` are present, decode the keystore and sign a stable release APK.
   Otherwise fall back to building an installable **debug-signed** APK.
5. Upload the APK as a workflow **artifact**; on a pushed tag (`v*`), attach it to a
   **GitHub Release**.

A green build on `master` yields a downloadable, sideloadable APK with no secrets
required; adding the signing secrets later gives a stable upgrade signature.

---

## 7. Testing Strategy

JVM unit tests (run in CI; this is where correctness is enforced, since no local
emulator is available):
- `WsprEncoder` output vs published reference symbol vectors.
- `Maidenhead` ↔ lat/lon round-trip; `GreatCircle` distance/azimuth vs known pairs.
- `SolarTerminator` sun position sanity checks.
- `SpotQuery` → wspr.live SQL builder.
- Each source parser against captured sample payloads (fixtures).

UI is verified manually via the installed APK (the repository owner sideloads CI output).

---

## 8. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| RBN has no clean REST API | Telnet streaming snapshot behind `SpotSource`; graceful "unavailable" on failure. |
| PSKReporter rate limits | Per-query cache + backoff; show cached/rate-limited state. |
| No local Android toolchain | CI is the build/verify loop; pure logic covered by CI unit tests. |
| Map globe / free tiles | MapLibre Native + OpenFreeMap (no API key); fall back to flat projection if globe unsupported on a device. |
| WSPR encoder correctness | TDD against reference vectors before wiring audio. |
| APK signing secrets | Default to debug-signed installable APK; optional release signing via secrets. |

---

## 9. Out of Scope (v1)

- iOS / cross-platform builds.
- USB-serial CAT / PTT rig control (possible later; not in v1).
- WSPR *decoding* from received audio (RX here means network spots, not local demod).
- Google Play listing / store submission (sideload APK only).
