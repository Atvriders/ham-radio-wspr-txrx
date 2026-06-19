# Ham Radio WSPR TX/RX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a native Android app that fetches/visualizes WSPR reception spots and encodes a transmittable WSPR audio signal, with an installable APK built by GitHub Actions.

**Architecture:** Single-module Compose (Material 3) app, MVVM with `StateFlow`, a `SpotRepository` fronting pluggable `SpotSource`s (wspr.live / PSKReporter / RBN), pure-Kotlin core (Maidenhead, great-circle, solar terminator, WSPR encoder/audio), Room cache + DataStore settings, manual DI via `AppContainer`.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, OkHttp + kotlinx.serialization, Room, DataStore, MapLibre Native Android, AndroidX Lifecycle/Navigation-Compose, JUnit4 for JVM unit tests, Gradle (Kotlin DSL), GitHub Actions.

## Global Constraints

- Application ID: `com.atvriders.wsprtxrx`; app label: **Ham Radio WSPR TX/RX**.
- minSdk 26, compileSdk 35, targetSdk 35, JDK 17, Kotlin 2.0.x, AGP 8.5+.
- Single Gradle module `:app`. Manual DI only (no Hilt).
- All pure-logic lives under `core/` packages and MUST be JVM-unit-testable with no Android deps.
- No API keys committed. Map uses MapLibre + OpenFreeMap (keyless). 
- Network sources degrade gracefully (return `Result.failure`, never crash).
- TX is audio-only (acoustic coupling); UI must show a licensing/RF-responsibility notice.
- Repo: `github.com/Atvriders/ham-radio-wspr-txrx`, branch `master`, public.
- CI must produce an installable APK with no required secrets (debug-signed fallback).

---

## Phase 0 — Scaffold + green CI

### Task 0.1: Gradle project skeleton

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts` (root), `app/build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `.gitignore`.
- Create: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, mipmap/adaptive icon resources.
- Create: `app/src/main/java/com/atvriders/wsprtxrx/MainActivity.kt`, `WsprApp.kt` (Application).

**Interfaces:**
- Produces: a buildable empty Compose app showing a "Ham Radio WSPR TX/RX" scaffold.

- [ ] Configure `libs.versions.toml` with all dependency versions (Compose BOM, OkHttp, kotlinx-serialization, Room, DataStore, MapLibre, lifecycle, navigation-compose, junit).
- [ ] `app/build.gradle.kts`: applicationId, sdk levels, Compose enabled, packaging, signingConfig debug, `testOptions`.
- [ ] Minimal `MainActivity` with `setContent { WsprTheme { Scaffold { Text("Ham Radio WSPR TX/RX") } } }`.
- [ ] Commit `feat: gradle skeleton + empty compose app`.

### Task 0.2: GitHub Actions APK build

**Files:**
- Create: `.github/workflows/build.yml`
- Create: `README.md` (download/sideload instructions).

**Interfaces:**
- Produces: CI that runs `./gradlew test assembleRelease` (debug-signed fallback) and uploads the APK artifact; tags publish a Release.

- [ ] Workflow: `actions/checkout`, `actions/setup-java@v4` (temurin 17), `android-actions/setup-android@v3`, gradle cache, `./gradlew test assembleRelease`, signing step (decode `KEYSTORE_BASE64` if present else use debug keystore), `actions/upload-artifact` for the APK, `softprops/action-gh-release` on `v*` tags.
- [ ] Commit `ci: build + upload APK on github actions`. (First push validates the toolchain.)

---

## Phase 1 — Core pure-Kotlin logic (TDD, JVM tests)

### Task 1.1: Band model

**Files:**
- Create: `app/src/main/java/com/atvriders/wsprtxrx/core/Band.kt`
- Test: `app/src/test/java/com/atvriders/wsprtxrx/core/BandTest.kt`

**Interfaces:**
- Produces: `enum class Band(val label:String, val rangeHz:LongRange, val defaultColor:Long)`, `fun bandForFreq(hz:Long): Band?`. Covers LF→VHF WSPR sub-bands (2200m, 630m, 160–10m, 6m, 4m, 2m).

- [ ] Step 1: Write `BandTest` asserting `bandForFreq(14_097_100) == Band.M20`, `bandForFreq(7_040_100) == Band.M40`, `bandForFreq(1) == null`.
- [ ] Step 2: Run, verify fails.
- [ ] Step 3: Implement `Band` with WSPR dial+offset ranges.
- [ ] Step 4: Run, verify passes.
- [ ] Step 5: Commit `feat(core): band model`.

### Task 1.2: Maidenhead grid ↔ lat/lon

**Files:**
- Create: `app/src/main/java/com/atvriders/wsprtxrx/core/Maidenhead.kt`
- Test: `app/src/test/java/com/atvriders/wsprtxrx/core/MaidenheadTest.kt`

**Interfaces:**
- Produces: `fun gridToLatLon(grid:String): LatLon` (center of square), `fun latLonToGrid(lat:Double, lon:Double, precision:Int=6): String`, `data class LatLon(val lat:Double, val lon:Double)`.

- [ ] Step 1: Test `gridToLatLon("FN42")` ≈ (42.5, -71.0) within 0.6°; `gridToLatLon("JO65")` ≈ (65.5, 30.0)... assert with tolerance; round-trip `latLonToGrid(gridToLatLon("FN42").… )` starts with "FN42".
- [ ] Step 2: Run, verify fails.
- [ ] Step 3: Implement field/square/subsquare decode + encode.
- [ ] Step 4: Run, verify passes.
- [ ] Step 5: Commit `feat(core): maidenhead conversion`.

### Task 1.3: Great-circle distance & azimuth

**Files:**
- Create: `app/src/main/java/com/atvriders/wsprtxrx/core/GreatCircle.kt`
- Test: `app/src/test/java/com/atvriders/wsprtxrx/core/GreatCircleTest.kt`

**Interfaces:**
- Produces: `fun distanceKm(a:LatLon, b:LatLon): Double`, `fun azimuthDeg(from:LatLon, to:LatLon): Double`.

- [ ] Step 1: Test known pair (e.g. London 51.5,-0.13 → New York 40.7,-74.0) distance ≈ 5570 km ±30, azimuth ≈ 288° ±2.
- [ ] Step 2: Run, verify fails.
- [ ] Step 3: Implement haversine + initial bearing.
- [ ] Step 4: Run, verify passes.
- [ ] Step 5: Commit `feat(core): great-circle math`.

### Task 1.4: Solar terminator (grey line)

**Files:**
- Create: `app/src/main/java/com/atvriders/wsprtxrx/core/SolarTerminator.kt`
- Test: `app/src/test/java/com/atvriders/wsprtxrx/core/SolarTerminatorTest.kt`

**Interfaces:**
- Produces: `fun subsolarPoint(epochSec:Long): LatLon`, `fun terminatorPolygon(epochSec:Long, steps:Int=180): List<LatLon>` (great-circle 90° from subsolar point).

- [ ] Step 1: Test subsolar latitude near 0 at an equinox timestamp (±2°) and within ±23.5° generally; polygon has `steps` points all ~90° from subsolar point.
- [ ] Step 2: Run, verify fails.
- [ ] Step 3: Implement solar declination + hour-angle approximation and terminator ring.
- [ ] Step 4: Run, verify passes.
- [ ] Step 5: Commit `feat(core): solar terminator`.

---

## Phase 2 — WSPR encoder + audio (TDD, correctness gate)

### Task 2.1: WSPR message bit-packing

**Files:**
- Create: `app/src/main/java/com/atvriders/wsprtxrx/core/wspr/WsprMessage.kt`
- Test: `app/src/test/java/com/atvriders/wsprtxrx/core/wspr/WsprMessageTest.kt`

**Interfaces:**
- Produces: `fun packCallsign(call:String): Int` (28-bit), `fun packGridPower(grid:String, dbm:Int): Int` (22-bit), `fun sourceBits(call:String, grid:String, dbm:Int): BooleanArray` (50-bit MSB-first).

- [ ] Step 1: Test callsign normalization (right/left justify rules, 3rd char digit), valid power set {0,3,7,10,13,17,20,23,27,30,33,37,40,43,47,50,53,57,60}; assert `packGridPower("FN42",37)` equals the value computed by the published formula `(179 - 10*lon_idx - dbm... )`-style packing (lock exact int during impl from WSJT-X reference).
- [ ] Step 2: Run, verify fails.
- [ ] Step 3: Implement packing per the open WSPR/WSJT-X type-1 message spec.
- [ ] Step 4: Run, verify passes.
- [ ] Step 5: Commit `feat(wspr): message bit packing`.

### Task 2.2: Convolutional FEC + interleave + sync → 162 symbols

**Files:**
- Create: `app/src/main/java/com/atvriders/wsprtxrx/core/wspr/WsprEncoder.kt`
- Create: `app/src/main/java/com/atvriders/wsprtxrx/core/wspr/WsprSync.kt` (the fixed 162-element sync vector constant).
- Test: `app/src/test/java/com/atvriders/wsprtxrx/core/wspr/WsprEncoderTest.kt`

**Interfaces:**
- Consumes: `WsprMessage.sourceBits`.
- Produces: `fun encode(call:String, grid:String, dbm:Int): IntArray` returning 162 channel symbols, each in 0..3, where `symbol[i] = sync[i] + 2*data[i]`.

- [ ] Step 1: Test `encode("K1ABC","FN42",37).size == 162`, all symbols in 0..3, and parity-of-sync: `encode(...)[i] % 2 == WsprSync.VECTOR[i]` for all i. Add a full-vector assertion against the canonical 162-symbol reference for `K1ABC/FN42/37` (generated/verified during impl with an independent reference encoder).
- [ ] Step 2: Run, verify fails.
- [ ] Step 3: Implement: 50 source bits + 31 zero tail → rate-1/2 K=32 conv encoder (polys 0xf2d05351, 0xe4613c47) → bit-reversal interleave of 162 → combine with `WsprSync.VECTOR`.
- [ ] Step 4: Run, verify passes.
- [ ] Step 5: Commit `feat(wspr): convolutional encode + interleave + symbols`.

### Task 2.3: Symbols → 4-FSK audio (AudioTrack)

**Files:**
- Create: `app/src/main/java/com/atvriders/wsprtxrx/audio/WsprAudio.kt`
- Test: `app/src/test/java/com/atvriders/wsprtxrx/audio/WsprAudioTest.kt` (pure sample-buffer generation, no AudioTrack).

**Interfaces:**
- Consumes: `WsprEncoder.encode`.
- Produces: `fun renderPcm(symbols:IntArray, centerHz:Double=1500.0, sampleRate:Int=12000): ShortArray` (phase-continuous 4-FSK, 8192 samples/symbol, tone spacing `sampleRate/8192.0`), and `class WsprPlayer` wrapping `AudioTrack` streaming (Android-only, not unit-tested).

- [ ] Step 1: Test `renderPcm(IntArray(162){0}).size == 162*8192`; tone-0 buffer's dominant frequency ≈ centerHz (simple zero-crossing estimate ±2 Hz); phase continuity (no sample jump > amplitude at symbol boundaries).
- [ ] Step 2: Run, verify fails.
- [ ] Step 3: Implement phase-accumulator FSK synthesis; `WsprPlayer.play(symbols, onProgress)` streaming to AudioTrack.
- [ ] Step 4: Run, verify passes.
- [ ] Step 5: Commit `feat(audio): wspr fsk synthesis + player`.

### Task 2.4: Even-minute scheduler

**Files:**
- Create: `app/src/main/java/com/atvriders/wsprtxrx/audio/TxScheduler.kt`
- Test: `app/src/test/java/com/atvriders/wsprtxrx/audio/TxSchedulerTest.kt`

**Interfaces:**
- Produces: `fun msUntilNextEvenMinute(nowMs:Long): Long` (start ~1s into the next even UTC minute).

- [ ] Step 1: Test boundaries: at `00:00:00.000` → ~1000ms; at `00:01:30.000` → ~30500ms; always lands at even minute + 1s.
- [ ] Step 2: Run, verify fails.
- [ ] Step 3: Implement.
- [ ] Step 4: Run, verify passes.
- [ ] Step 5: Commit `feat(audio): even-minute tx scheduler`.

---

## Phase 3 — Data layer

### Task 3.1: Spot model + SpotQuery + SourceId

**Files:**
- Create: `app/src/main/java/com/atvriders/wsprtxrx/data/model/Spot.kt`, `SpotQuery.kt`, `SourceId.kt`.
- Test: `app/src/test/java/com/atvriders/wsprtxrx/data/model/SpotTest.kt`

**Interfaces:**
- Produces: `data class Spot(...)` (all fields from spec §3.1), `data class SpotQuery(...)` (spec §3.2), `enum class SourceId { WSPR_LIVE, PSK_REPORTER, RBN }`, `Spot.withGeometry()` that fills lat/lon/distance/azimuth from grids using `core`.

- [ ] Step 1: Test `Spot(... txGrid="FN42", rxGrid="IO91").withGeometry()` populates distanceKm > 0 and azimuth in 0..360.
- [ ] Step 2–5: Implement, test, commit `feat(data): spot model + query`.

### Task 3.2: SpotSource interface + wspr.live SQL builder

**Files:**
- Create: `app/src/main/java/com/atvriders/wsprtxrx/data/source/SpotSource.kt`, `WsprLiveSource.kt`, `WsprLiveQueryBuilder.kt`.
- Test: `app/src/test/java/com/atvriders/wsprtxrx/data/source/WsprLiveQueryBuilderTest.kt`

**Interfaces:**
- Consumes: `SpotQuery`.
- Produces: `interface SpotSource { val id:SourceId; suspend fun query(q:SpotQuery): Result<List<Spot>> }`; `fun buildSql(q:SpotQuery): String`; `WsprLiveSource(client:OkHttpClient, json:Json)`.

- [ ] Step 1: Test `buildSql` includes band frequency filter, time window (`time > now() - INTERVAL n MINUTE`), callsign tx/rx clause, distance/power filters, `FORMAT JSONEachRow`, and is injection-safe (callsign sanitized to `[A-Z0-9/]`).
- [ ] Step 2–4: Implement builder + `WsprLiveSource.query` (OkHttp GET, parse JSONEachRow lines).
- [ ] Step 5: Commit `feat(data): wspr.live source`.

### Task 3.3: PSKReporter source (rate-limited)

**Files:**
- Create: `app/src/main/java/com/atvriders/wsprtxrx/data/source/PskReporterSource.kt`, `data/source/RateLimiter.kt`.
- Test: `.../PskReporterParseTest.kt`, `.../RateLimiterTest.kt`

**Interfaces:**
- Produces: `PskReporterSource(client, clock)`; parses the retrieve payload into `List<Spot>`; `RateLimiter(minIntervalMs)` returns cached result if called too soon.

- [ ] Step 1: Test parser against a captured fixture (`src/test/resources/pskreporter_sample.xml`) → expected spot count/fields; `RateLimiter` blocks a 2nd call within interval and returns cache.
- [ ] Step 2–4: Implement.
- [ ] Step 5: Commit `feat(data): pskreporter source + rate limiter`.

### Task 3.4: RBN telnet source

**Files:**
- Create: `app/src/main/java/com/atvriders/wsprtxrx/data/source/RbnSource.kt`, `data/source/RbnLineParser.kt`.
- Test: `.../RbnLineParserTest.kt`

**Interfaces:**
- Produces: `RbnSource(callsign, socketFactory)` collecting a time-boxed snapshot; `fun parseSpotLine(line:String): Spot?`.

- [ ] Step 1: Test `parseSpotLine` against sample RBN cluster lines (DX de … freq … call … SNR … grid) → `Spot`; malformed line → null.
- [ ] Step 2–4: Implement parser + bounded socket reader with timeout + graceful failure.
- [ ] Step 5: Commit `feat(data): rbn telnet source`.

### Task 3.5: Room cache + DataStore settings + QrzService + repository

**Files:**
- Create: `data/local/SpotEntity.kt`, `SpotDao.kt`, `AppDatabase.kt`; `data/prefs/SettingsStore.kt`; `data/qrz/QrzService.kt`; `data/SpotRepository.kt`.
- Test: `.../SpotRepositoryTest.kt` (fake sources), `.../SettingsStoreTest.kt` (in-memory), `.../QrzParseTest.kt` (fixture).

**Interfaces:**
- Consumes: all sources, Room, DataStore.
- Produces: `SpotRepository(sources:List<SpotSource>, dao, settings)` with `suspend fun search(q:SpotQuery): RepoResult` (merge enabled sources, dedup by `txCall+rxCall+freqHz+timeBucket`, geometry, cache, partial-failure reporting); `SettingsStore` flows (enabled sources, qrz creds, band colors, time range, units, theme, recent calls); `QrzService.lookup(call): Result<QrzInfo>`.

- [ ] Step 1: Test repo merges two fake sources, dedups duplicates, reports a failing source as partial; settings round-trip; QRZ parse from fixture.
- [ ] Step 2–4: Implement.
- [ ] Step 5: Commit `feat(data): repository + cache + settings + qrz`.

---

## Phase 4 — App shell

### Task 4.1: AppContainer + Application + theme + navigation

**Files:**
- Create: `di/AppContainer.kt`; modify `WsprApp.kt`; `ui/theme/Theme.kt`, `Color.kt`, `Type.kt`; `ui/WsprNavHost.kt`; `ui/Screen.kt` (route enum).
- Test: none (wiring); validated by CI build.

**Interfaces:**
- Consumes: repository, services.
- Produces: `AppContainer` exposing repository/services/settings; `WsprNavHost` with bottom-nav routes Spots/Map/Charts/TX/Settings; `viewModelFactory(container)` helper.

- [ ] Build green; commit `feat(ui): app shell + navigation + theme`.

---

## Phase 5 — Screens

### Task 5.1: Shared search + filter + ViewModel base

**Files:** `ui/common/SearchBar.kt`, `ui/common/FilterSheet.kt`, `ui/common/SpotsViewModel.kt` (shared query state), `ui/common/UiState.kt`.
**Interfaces:** Produces `SpotsViewModel(container)` holding `StateFlow<SpotQuery>` + `StateFlow<SpotsUiState>` consumed by Spots/Map/Charts; filter sheet edits bands/time/distance/power/unique/direction; search edits callsign-or-grid.
- [ ] Build green; commit `feat(ui): shared search + filter + viewmodel`.

### Task 5.2: Spots list + detail sheet (QRZ)

**Files:** `ui/spots/SpotsScreen.kt`, `ui/spots/SpotRow.kt`, `ui/spots/SpotDetailSheet.kt`.
**Interfaces:** Sortable table (time/SNR/distance/band); row tap → detail with QRZ lookup, "search this call", "open qrz.com".
- [ ] Build green; commit `feat(ui): spots list + detail`.

### Task 5.3: Map (MapLibre globe + markers + lines + terminator)

**Files:** `ui/map/MapScreen.kt`, `ui/map/MapLibreView.kt`, `ui/map/SpotLayers.kt`; add MapLibre dep + style URL.
**Interfaces:** Globe projection; markers green/red/purple by role; great-circle path lines; per-band colors from settings; terminator polygon overlay from `SolarTerminator`; marker tap → info.
- [ ] Build green; commit `feat(ui): map with globe + markers + grey line`.

### Task 5.4: Charts + Head2Head

**Files:** `ui/charts/ChartsScreen.kt`, `ui/charts/SpotCountChart.kt`, `ui/charts/SnrChart.kt`, `ui/charts/Head2HeadScreen.kt`; `ui/charts/ChartData.kt` (+ JVM test for bucketing).
**Test:** `ChartDataTest.kt` — time-bucketing + Head2Head pairing (spots heard by both stations) on sample data.
**Interfaces:** Canvas bar chart (band-colored counts over time, axis→now) + line chart (SNR over time); Head2Head pairs transmissions received by two chosen stations, no averaging.
- [ ] TDD `ChartData`; build green; commit `feat(ui): charts + head2head`.

### Task 5.5: TX screen

**Files:** `ui/tx/TxScreen.kt`, `ui/tx/TxViewModel.kt`; location permission handling; foreground playback.
**Interfaces:** Inputs call/grid/power; GPS auto-grid (fine-location permission, `latLonToGrid`); "Transmit" schedules via `TxScheduler`, plays `WsprPlayer`; countdown + progress; licensing/RF notice banner.
- [ ] Build green; commit `feat(ui): tx screen`.

### Task 5.6: Settings

**Files:** `ui/settings/SettingsScreen.kt`, `ui/settings/BandColorEditor.kt`, `ui/settings/RecentCallsEditor.kt`.
**Interfaces:** Toggle sources; QRZ login fields; band-color edit; default time range; units; theme; recent-calls swipe-delete/reorder — all persisted via `SettingsStore`.
- [ ] Build green; commit `feat(ui): settings`.

---

## Phase 6 — Polish + release

### Task 6.1: Manifest, permissions, icon, README

**Files:** `AndroidManifest.xml` (INTERNET, ACCESS_FINE_LOCATION, RECORD_AUDIO? no — playback only; FOREGROUND_SERVICE if needed), adaptive launcher icon, `README.md` finalize.
- [ ] Build green; commit `chore: permissions + icon + readme`.

### Task 6.2: Release signing path + tag

**Files:** `.github/workflows/build.yml` finalize release-signing branch; `docs` note on adding secrets.
- [ ] Push tag `v0.1.0`, confirm Release APK published; commit `ci: release signing + v0.1.0`.

---

## Self-Review

**Spec coverage:** §2 architecture → Phase 4; §3 sources → Phase 3; §4 screens → Phase 5; §5 TX encoder → Phase 2; §6 CI → Phase 0/6; §7 testing → tests in Phases 1–3,5.4. All spec sections mapped.

**Placeholder scan:** No "TBD/handle edge cases" left; exact WSPR vector + packed ints are locked during the encoder tasks (TDD gate) rather than guessed here, which is correct — the test asserts against the canonical reference.

**Type consistency:** `Spot`, `SpotQuery`, `SpotSource.query(): Result<List<Spot>>`, `SourceId`, `LatLon`, `encode(): IntArray(162)`, `renderPcm(): ShortArray`, `SpotRepository.search()` are used consistently across tasks.
