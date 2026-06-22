# Ham Radio WSPR TX/RX — Final Pre-Launch Deep Review

## 1. Verdict

**Not launch-ready.** The app is functionally rich and the WSPR signal core (encoder, sync vector, audio synthesis, geo math) is verified correct, but it cannot ship today for two independent classes of reasons: (a) **the release pipeline produces a debug-signed APK, not a Play-mandated signed `.aab`, with no privacy policy, no location disclosure, and an unprepared Data Safety form** — any one of which is an automatic Play rejection; and (b) **the primary data source is broken** — the wspr.live query selects `FROM rx` instead of `FROM wspr.rx`, so the headline feature returns zero spots for every user. On top of that, QRZ credentials are stored in plaintext inside an auto-backed-up DataStore. Fix the blockers below, address the high-priority bugs, and this is a strong release.

| Severity | Count |
|---|---|
| 🚑 Blocker | 6 |
| High | 9 |
| Medium | 13 |
| Low | 18 |

---

## 2. 🚑 Launch Blockers (must fix before Play Store)

### B1. wspr.live SQL queries the wrong table — primary data source returns zero spots for everyone
- **`WsprLiveQueryBuilder.kt:51`** — emits `SELECT ... FROM rx WHERE ...`. The wspr.live ClickHouse HTTP user has no default database, so the table must be fully qualified as `wspr.rx`. ClickHouse returns `Table rx doesn't exist` (HTTP non-2xx), and `WsprLiveSource.query` (`WsprLiveSource.kt:38`) maps any non-success to `Result.failure`, so **every** query on the primary source fails. List/map/charts/Head2Head are empty for all users unless RBN/PSK are enabled.
- **Fix:** change to `FROM wspr.rx`. Update `WsprLiveQueryBuilderTest` to assert `FROM wspr.rx` (the current test asserts `contains("FROM rx")`, which masks the bug). Add a real-endpoint smoke test.

### B2. Release build is debug-signed and ships an APK, not an `.aab`
- **`app/build.gradle.kts:45-49`** (falls back to `signingConfigs.getByName("debug")` when `KEYSTORE_FILE` is unset) and **`.github/workflows/build.yml:49-67`** (only `assembleRelease`, uploads `app-release.apk`). The Android debug key is a well-known shared key; a debug-signed artifact can never be uploaded to Play, and since Aug 2021 all **new** apps must publish an Android App Bundle.
- **Fix:** generate a dedicated upload keystore, enroll in Play App Signing, store `KEYSTORE_BASE64/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD` as secrets. Add `./gradlew bundleRelease` and upload the `.aab`. Remove the debug-signing fallback for release builds (fail the CI release/tag job if secrets are absent) so a debug-signed release can never be produced.

### B3. No privacy policy
- Grep for "privacy" across the repo returns nothing. A privacy policy URL is **mandatory** for any app requesting sensitive permissions (location is sensitive) and is one of the most common automatic rejections.
- **Fix:** write and host a public privacy policy covering location use, QRZ credentials, and the callsign/grid/query data sent to wspr.live / PSKReporter / QRZ / reversebeacon.net; enter the URL in Play Console → App content → Privacy policy.

### B4. Location permission has no prominent in-context disclosure
- **`AndroidManifest.xml:6-7`**, **`TxScreen.kt:88-96`** — `ACCESS_FINE/COARSE_LOCATION` is requested the moment the user taps "GPS" to auto-fill their grid, with no rationale shown before the system dialog. Play's Location policy requires a prominent in-app disclosure.
- **Fix:** show a one-time rationale before the runtime request ("Location is used only to compute your Maidenhead grid locally; it is never uploaded"). Use is foreground-only — do **not** add `ACCESS_BACKGROUND_LOCATION`.

### B5. Data Safety form not prepared; app collects/transmits identity + credentials
- **`WsprLiveSource.kt`, `QrzService.kt:25-67`, `SettingsStore.kt:99-100`** — app sends searched callsigns to wspr.live/PSKReporter, sends QRZ username+password to xmldata.qrz.com, stores QRZ credentials + own callsign/grid on device, and reads device location. None is declared. Inaccurate/missing Data Safety declarations are a frequent enforcement/rejection cause.
- **Fix:** complete the Data Safety form — Location (approximate), callsign as an identifier, QRZ account credentials (collected + stored + sent to third party), third parties named (QRZ.com, wspr.live, PSKReporter). Keep consistent with the privacy policy.

### B6. QRZ password stored in plaintext DataStore with backups enabled
- **`SettingsStore.kt:43-46,99-100`** writes `qrz_pass` as cleartext; **`AndroidManifest.xml:11`** has `allowBackup="true"` with **no** `dataExtractionRules`/`fullBackupContent` (no `res/xml` rules exist). The credentials DataStore is therefore eligible for Google cloud Auto Backup and `adb backup` — a real off-device credential leak of a reusable, PII-linked QRZ.com account, and a Data Safety problem.
- **Fix:** stop persisting the raw password — store it in EncryptedSharedPreferences / a Keystore-backed cipher, or persist only the short-lived QRZ `sessionKey` (already cached at runtime in `QrzService`). Add `res/xml/data_extraction_rules.xml` + `res/xml/backup_rules.xml` that **exclude** the `wspr_settings` DataStore from cloud backup and device transfer, wired via `android:dataExtractionRules`/`android:fullBackupContent`. At minimum set `allowBackup="false"`.

> Note: this same `allowBackup`-with-no-rules default also sweeps the Room spot cache and `recentCalls` (operator callsign + searched stations) into backups — fixing B6's backup rules resolves both.

---

## 3. High Priority

**H1. MapView never started when opened while Activity is already RESUMED — blank/stalled map.** `MapScreen.kt:119-137`. The `MapView` is created with `onCreate(null)` and driven only by a `LifecycleEventObserver`, which delivers **future** events only and never replays the current state. The Map tab is entered mid-RESUMED via the `when(current)` switch, so `ON_START`/`ON_RESUME` never fire and the GL surface starts inconsistently (blank map, or only renders after a background/foreground cycle). Fix: on attach, fast-forward to the owner's current state — `if (lifecycle.currentState.isAtLeast(STARTED)) mapView.onStart(); if (...RESUMED) mapView.onResume()` (or use `LifecycleStartEffect`); make teardown symmetric.

**H2. Full-search error is captured but never displayed — silent failure on every screen.** `SpotsViewModel.kt:84` sets `ui.error`, but no composable reads it. On total failure (no network, DNS, all sources down) the list collapses to the "No spots" empty state, identical to a legitimately empty result, with no retry. `FailuresBanner` only covers partial per-source failures. Fix: render `ui.error` on Spots/Map/Charts with a Retry → `vm.search()`, and distinguish loading / error / empty explicitly. (Combined with B1, today every user hits this silent path.)

**H3. `versionCode` hardcoded to `1` with no bump strategy.** `app/build.gradle.kts:17-18`. Play rejects any upload whose `versionCode` is not strictly greater than the highest in the track, so the first update/crash-fix cannot be uploaded and parallel CI builds collide. Fix: drive `versionCode` from CI (`github.run_number` or a parsed tag) via a gradle property; set `versionName` from the git tag; decide on launching as `1.0.0` (current `0.1.0` reads pre-release).

**H4. MapLibre native libs + universal APK → 16 KB page-size risk and ~60 MB bloat.** `libs.versions.toml:16` (`maplibre 11.13.5`), no `splits{}`/`bundle{}`. As of Nov 1 2025, apps targeting Android 15+ must support 16 KB memory pages — every bundled `.so` must be 16 KB-aligned. A universal APK also forces all four ABIs onto every device. Fix: publish as an `.aab` (Play splits ABIs automatically); verify MapLibre 11.13.5's `.so` files are 16 KB-aligned (`check_elf_alignment.sh`) and upgrade if not; optionally restrict `abiFilters` to `arm64-v8a`/`armeabi-v7a`(/`x86_64`).

**H5. R8 disabled for release — no shrinking/obfuscation, ~60 MB APK, keep-rules never exercised.** `app/build.gradle.kts:37-42` (`isMinifyEnabled = false`, no `isShrinkResources`). `material-icons-extended` + MapLibre + full Compose stack ship unused code/resources; the existing ProGuard keep rules are inert, so a future flip to `true` could break at runtime undetected. Fix: set `isMinifyEnabled = true` + `isShrinkResources = true`, then smoke-test list/map/charts/TX/DataStore + fresh-install Room on the minified build and confirm `mapping.txt` is produced. Do this together with the `.aab` switch so size is measured on the per-device split.

**H6. RBN telnet login is fragile and uses a likely-rejected placeholder call.** `RbnSource.kt:23-54`. It writes `"$loginCall\r\n"` immediately on connect without waiting for the `Please enter your call:` prompt (early bytes can be discarded), `loginCall` defaults to `N0CALL` (a reserved placeholder several cluster front-ends reject), and a fixed 6 s snapshot means a slow handshake yields zero lines, surfaced as a silent empty list. Fix: read until the prompt before sending; use the user's real (already-collected) callsign; make `snapshotMs` adaptive; surface an explicit "RBN login failed" state. (Privacy caveat below — see L-block.)

**H7. `FORMAT JSONEachRow` concatenated into the query with no structural guarantee.** `WsprLiveQueryBuilder.kt:51-52`. Valid only because of an incidental leading space; any future edit that appends a clause after `FORMAT` or adds a trailing `;` silently breaks all parsing. Fix: build the `FORMAT` suffix so it is guaranteed to be the final token with no trailing `;`, or pass `default_format=JSONEachRow` as a separate URL param; add a test asserting exactly one trailing `FORMAT JSONEachRow` and no `;`.

**H8. QRZ password persisted at rest (cross-ref B6) — also sent as a URL query parameter.** `QrzService.kt:52-58` sends `?username=...&password=...`; over HTTPS so not on the wire, but credentials in URLs land in server/proxy access logs and any crash-reporting interceptor that captures full request URLs. Fix: minimize the raw password's lifetime (exchange for a session key once per session) and ensure no logging/crash-reporting OkHttp interceptor is ever attached to `httpClient`. (The at-rest fix is B6; this is the in-transit surface to keep in mind.)

**H9. Almost all user-facing strings are hardcoded — no i18n.** `strings.xml` has only 7 entries (and even the `nav_*` ones are dead code); `Destinations.kt:13-17`, `QueryControls.kt`, `SettingsScreen.kt`, `TxScreen.kt`, `SpotDetail.kt`, `ChartsScreen.kt` all hardcode English. Blocks localization for a global ham audience and is flagged by Play's pre-launch/listing localization checks. Fix: move literals into `strings.xml`, wire `Destination` labels to a `@StringRes`, and standardize on `stringResource()` (drop the ad-hoc `stringResourceCompat` in `TxScreen.kt:147`).

---

## 4. Medium / Polish

**Domain correctness & sources**
- **Maidenhead `gridToLatLon` validates length only, not characters.** `Maidenhead.kt:12-30`. Garbage grids (e.g. `ZZ99`, `FNXY`, `42AB`) decode to out-of-range coordinates without throwing; both callers (`Spot.gridLatLon` `runCatching`, `TxViewModel.validGrid`) treat "didn't throw" as valid. Result: PSKReporter spots with malformed locators plotted off-globe and feeding wrong distances/Head2Head; the TX Transmit button can enable for a bogus grid. No crash (MapLibre/trig tolerate out-of-range). Fix: per-position validation (`[A-R][A-R][0-9][0-9]`, subsquare `[A-X][A-X]`) in `gridToLatLon`, expose a `gridToLatLonOrNull`, and route both callers through it.
- **`packGridPower` accepts out-of-range grids → corrupt (negative) message field.** `WsprMessage.kt:60-66`. With only `length>=4` checked, a grid whose first letter is S–Z makes `m` negative; the transmitted frame decodes to a coherent-but-wrong grid. Reachable from the TX screen because `validGrid` (via `Maidenhead.gridToLatLon`) doesn't reject it. Same root cause as above — fix both the encoder boundary (`require` field-class) and the UI gate together.
- **PSKReporter query never filters `mode=WSPR` and omits `appcontact=`.** `PskReporterSource.kt:30-48`. FT8/FT4/CW reports are mixed into a "WSPR" app's list/charts/Head2Head; and without `appcontact=`, a mass-deployed app risks throttling/blocking per PSKReporter operator policy. Fix: decide/document WSPR-only vs. multimode; add `appcontact=<contact>`.
- **PSKReporter 5-min rate limit is in-memory only.** `RateLimiter.kt:13-27`. Every cold start resets it, so repeated app opens can exceed the policy. Fix: persist last-fetch timestamp per key in DataStore; apply UI band/grid filters client-side to the cached superset instead of refetching.
- **RBN line parser is over-strict (rigid column order, requires `dB` + 4-digit `Z`).** `RbnLineParser.kt:19-21`. Many valid RBN formats are silently dropped, materially reducing coverage. Fix: parse positionally (locate freq / `dB` / `Z` tokens); add fixtures for real line variants; tag RBN spots as skimmer (CW/FT), not WSPR.
- **Solar terminator crosses the antimeridian without splitting → map-wide streak.** `SolarTerminator.kt:38-53` (per-point `normalizeLon`), rendered as one `LineString` in `MapScreen.kt:328-333`. For essentially every non-equinox time the grey line draws a horizontal streak across the whole map. Cosmetic on an optional overlay, but visibly "broken." Fix: split into a MultiLineString at crossings (and close the ring, fixing the secondary ~2° gap from the `0 until steps` loop).

**Concurrency & lifecycle**
- **`search()` is not single-flight → races `RateLimiter`'s plain `HashMap`.** `SpotsViewModel.kt:72-87`, `RateLimiter.kt:14-27`. Repeated/overlapping searches run concurrent `repository.search()` coroutines that read/write a non-synchronized `HashMap` from IO threads (data race / possible `ConcurrentModificationException`), and a stale search can win the `StateFlow` write. Fix: cancel the prior `searchJob` before launching (mirror `TxViewModel.transmit`); make `RateLimiter` thread-safe (Mutex / `ConcurrentHashMap`).

**TX audio / lifecycle**
- **No audio-focus request for the ~110.6 s transmission.** `WsprPlayer.kt` (sole audio path; grep confirms no `requestAudioFocus` anywhere). Concurrent music/notification/call audio mixes into `STREAM_MUSIC` and corrupts the acoustically-coupled signal, with no abort path on focus loss. Fix: request `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` (minSdk 26 → `AudioFocusRequest`) around `play()`, abandon in `finally`, and abort the transmit (set IDLE + "interrupted") on `AUDIOFOCUS_LOSS`.
- **No foreground service / wake handling for the time-critical transmit.** `TxViewModel.kt:57-87`. The 2-minute transmit runs on `viewModelScope`; backgrounding or screen timeout can stall audio or clear the ViewModel (`onCleared` cancels `txJob`), corrupting the on-air slot. Fix: run encode+wait+play in a foreground service (mediaPlayback) and/or hold a partial wake lock + keep-screen-on while `TRANSMITTING`; at minimum warn the user not to background during transmit.
- **`AudioTrack` not checked for `STATE_INITIALIZED` before `play()`.** `WsprPlayer.kt:32-41`. The hardcoded 12 kHz `STREAM_MUSIC` mono PCM16 path isn't guaranteed on every device; an uninitialized track makes `play()` throw `IllegalStateException`. Caught by `TxViewModel`'s try/catch (no crash), but surfaces an opaque message and leaves transmit unusable on affected devices. Also `.coerceAtLeast(4096)` masks a `getMinBufferSize` error code. Fix: check `getMinBufferSize > 0`, verify `track.state == STATE_INITIALIZED` (release + throw a clear message otherwise).

**Storage**
- **Spot cache grows unbounded — `deleteOlderThan`/`clear` are declared but never called.** `SpotDao.kt:16-20`, `SpotRepository.kt:60`. The primary key embeds a 120 s time bucket (`Spot.dedupKey`), so `REPLACE` rarely collapses rows across time and the `spots` table grows for the install's lifetime (no Worker/callback prunes it; no index on `timeUtc`). Internal private storage only — gradual disk growth, recoverable via clear-storage, no crash. Fix: wire `deleteOlderThan` (or a row cap) into `search()` after `upsertAll`, ideally in one `@Transaction`; add an index on `timeUtc`.

**UX / a11y / resources**
- **Hardcoded `Color.Gray`/hex text colors break dark-mode contrast and theming.** `Format.kt:31-36`, `ChartsScreen.kt` (~12 `Color.Gray` texts + `Color.LightGray` gridlines), `SpotsScreen.kt`, `SpotDetail.kt`, `TxScreen.kt:62-67`. Mid-gray secondary text and small (11 sp) axis labels on the `DeepNavy` dark scheme likely fall below WCAG 4.5:1; the safety-critical TX disclaimer (`0xFF8D6E00` on translucent amber over navy) is the hardest text to read. Fix: use `MaterialTheme.colorScheme` roles (`onSurfaceVariant`, `outlineVariant`, `errorContainer`/`onErrorContainer`); verify contrast in both schemes.
- **Color is the sole carrier of meaning for SNR / band / map TX-RX role.** `Format.snrColor`, band dots, `MapScreen.kt:266` red/green/purple markers. ~8% of men (large in the ham demographic) can't distinguish strong/weak SNR or TX vs RX dots. Fix: add non-color cues (glyph/shape/icon), surface map role in the always-visible legend.
- **TX disclaimer is the only RF-licensing gate and is easy to miss; no confirmation before transmit.** `TxScreen.kt:62-68,120-125`. A passive (low-contrast) banner + an immediate Transmit button means a curious non-ham can transmit on first tap. Fix: gate first-ever transmit behind a one-time persisted "I hold a valid amateur licence" acknowledgement; keep the disclaimer visible next to the button at higher contrast. Also mirror the disclaimer in the Play listing and answer the content-rating questionnaire honestly.

---

## 5. Low / Nits

- **`ACCESS_FINE_LOCATION` is over-requested** — coarse is ample for a 4/6-char grid and de-risks review. `AndroidManifest.xml:6`, `TxScreen.kt:89-94`. Drop FINE, request only `ACCESS_COARSE_LOCATION`.
- **RBN uses cleartext telnet** (`RbnSource.kt:25`); acceptable today because `loginCall` defaults to `N0CALL` (no PII sent), but if a real callsign is ever wired in it leaks in cleartext — add a comment/test forbidding it, and note RBN plaintext in the privacy policy.
- **ClickHouse SQL is string-interpolated** (`WsprLiveQueryBuilder.kt:30-47`); injection is blocked only by `cleanCallsign`/`cleanGrid` sanitization — make `buildSql` consume only sanitized values and add a quote-injection test.
- **CI injects signing secrets into PR-triggered builds** (`build.yml:7-8,50-55`) and grants job-level `contents: write`; split into an unsigned build/test job (PRs, no secrets) and a gated signed publish job.
- **No dependency lockfiles / wrapper checksum** (`gradle-wrapper.properties` lacks `distributionSha256Sum`, no `gradle.lockfile`, no `wrapper-validation` CI step) — supply-chain/reproducibility gap.
- **`AppDatabase` uses `exportSchema=false` + `fallbackToDestructiveMigration()`** (`AppDatabase.kt:6`, `AppContainer.kt:43`); harmless for a disposable cache, but set `exportSchema=true` and commit the v1 schema JSON **before** first release (the v1 baseline is unrecoverable afterward).
- **Map camera + selected station reset on rotation/tab switch** (`MapScreen.kt:105-117`); persist via `rememberSaveable` or hoist into the ViewModel.
- **Globe style JSON refetched on every Map entry** with no cache and no loading placeholder (`MapScreen.kt:116-117,349-360`); cache process-wide, show a spinner while `styleSpec == null`.
- **Chart bucketing / SNR sort / Head2Head computed on the composition thread** (`ChartsScreen.kt:48-55`); move into the ViewModel / `produceState` off-main.
- **Settings color-palette swatches are 28 dp tap targets** (`SettingsScreen.kt:153`), below the 48 dp minimum; wrap in `minimumInteractiveComponentSize()`.
- **Color swatches lack TalkBack semantics; `SpotRow` selection not exposed** (`SettingsScreen.kt`, `SpotsScreen.kt:161`); add `contentDescription` + `semantics { selected = … }`.
- **Direction/SpotSort/ThemeMode rendered as raw enum names** (`QueryControls.kt:178`, e.g. "BOTH"); give proper `@StringRes` labels.
- **Charts ignore `ui.loading`** and the two charts can disagree on empty/insufficient-data messaging (`ChartsScreen.kt`).
- **`WsprMessage.normalize` emits a misleading error for empty callsigns and accepts interior spaces** (e.g. "K1 BC") (`WsprMessage.kt:35-45`); add a canonical WSPR callsign regex guard.
- **`dbm`/`grid` power not range-checked in the encoder** (`WsprEncoder.kt`, `WsprMessage.kt:64`); unreachable via the constrained PowerPicker dropdown, but add `require(dbm in VALID_POWERS)` as defense-in-depth on the public API.
- **`GreatCircle.distanceKm` returns NaN for (near-)antipodal grid-center pairs** (`GreatCircle.kt:20`); reachable from real grid data, renders a wrong "0 km" and sorts to the top. One-line fix: clamp the radicand with `.coerceIn(0.0,1.0)` (or use the `asin` form).
- **`Band.ordered` re-sorts an already-ascending 15-element enum on every access** (`Band.kt:32`); make it a stored `val`. (Negligible; UI-tap paths only.)
- **`Band` 2190m `rangeHz` (135–138 kHz) excludes real ~139 kHz WSPR signals** (`Band.kt:14`), so 2190m spots show "?"/grey and drop from charts; niche LF band. Widen the range (and prefer the server `band` code for classification).

---

## 6. ✅ Play Store Launch Checklist

**Signing & artifact**
- [ ] **BLOCKER** Generate a dedicated upload keystore; enroll in Play App Signing; store keystore secrets in CI.
- [ ] **BLOCKER** Add `./gradlew bundleRelease`; upload the `.aab` (keep APK only for sideload).
- [ ] **BLOCKER** Remove the debug-signing fallback for release; fail the release/tag CI job if signing secrets are absent.
- [ ] **HIGH** Drive `versionCode` from CI (`github.run_number`/tag) and `versionName` from the git tag; decide on `1.0.0`.

**Permissions, privacy & data**
- [ ] **BLOCKER** Write + host a privacy policy (location, QRZ credentials, callsign/grid/query sent to wspr.live/PSKReporter/QRZ/RBN); enter URL in Play Console.
- [ ] **BLOCKER** Add a prominent in-app location rationale before the runtime permission request (`TxScreen.kt:88-96`); do **not** add background location.
- [ ] **BLOCKER** Complete the Data Safety form (approximate location, callsign identifier, QRZ account credentials, third-party transmission to QRZ/wspr.live/PSKReporter); keep consistent with the policy.
- [ ] **BLOCKER** Encrypt QRZ password at rest (Keystore/EncryptedSharedPreferences or store only the session key) **and** add `data_extraction_rules.xml` + `backup_rules.xml` excluding the credentials DataStore (or `allowBackup="false"`).
- [ ] **MEDIUM** Downgrade to `ACCESS_COARSE_LOCATION` only.
- [ ] **DEFENSE-IN-DEPTH** Add a `networkSecurityConfig` disallowing cleartext for the HTTPS hosts; confirm all endpoints stay HTTPS.

**Build size & runtime**
- [ ] **HIGH** Enable `isMinifyEnabled = true` + `isShrinkResources = true`; smoke-test all screens + TX + fresh-install Room on the minified build; confirm `mapping.txt` is uploaded.
- [ ] **HIGH** Verify MapLibre 11.13.5 `.so` files are 16 KB page-aligned (upgrade if not); rely on the `.aab` for per-ABI splitting.
- [ ] **MEDIUM** After R8, add OkHttp `-dontwarn` rules (conscrypt/bouncycastle/openjsse); verify Room `*_Impl` and the wspr.live manual-JSON parse survive (note: existing kotlinx.serialization keep rules guard a `@Serializable` pattern the code doesn't actually use).

**Functional gating (don't ship broken)**
- [ ] **BLOCKER** Fix `FROM rx` → `FROM wspr.rx` (B1) and add a real-endpoint smoke test — the primary source must actually return spots.
- [ ] **HIGH** Surface `ui.error` with retry so a broken network/source isn't indistinguishable from "no spots."

**Listing & compliance**
- [ ] **MEDIUM** Mirror the amateur-licence/RF disclaimer in the store description; gate first transmit behind a persisted licence acknowledgement; answer the content-rating questionnaire honestly.
- [ ] **PRE-SUBMIT** Produce store assets (512×512 hi-res icon, feature graphic, screenshots) — repo has only launcher mipmaps.

**Pipeline hygiene**
- [ ] **MEDIUM** Split CI: unsigned build+test on PRs (no secrets, `contents: read`); signed publish on push/tag with scoped `contents: write`.
- [ ] **MEDIUM** Add dependency lockfiles/verification, pin `distributionSha256Sum`, add a Gradle `wrapper-validation` step.

---

## 7. What's Solid

- **WSPR signal core is verified correct.** The encoder/message/sync vector are bit-exact against WSJT-X golden vectors; `WsprSync.VECTOR`, the convolutional + interleave path, and the 4-FSK synthesis (`WsprAudio` — 162×8192/12000 = 110.59 s, tone spacing 12000/8192 Hz, sample-rate-independent via the `dPhase` normalization) are all confirmed accurate.
- **Geo & solar math is correct.** Haversine distance (R=6371), initial-bearing azimuth with proper 0–360 normalization, Cooper's-equation solar declination, and the subsolar-longitude mapping are all sound; all `Band` frequency ranges and wspr.live band codes match the published table exactly.
- **Timing alignment is correct.** `TxScheduler.msUntilNextEvenMinute` properly aligns to even-UTC-minute + 1 s against epoch.
- **AudioTrack lifecycle is leak-free.** `WsprPlayer.play()` reliably `stop()`s/`release()`s in `finally`, honors coroutine cancellation per chunk via `ensureActive()`, and `txJob` is cancelled before each new transmit — no double-track, no native leak.
- **Threading/IO fundamentals are right.** Single process-wide DataStore instance (top-level `preferredDataStore` delegate); all network sources and the map style fetch use `Dispatchers.IO`; all `SpotDao` methods are `suspend` (no main-thread queries) — no ANR risk found in those paths.
- **Cross-source dedup and the Room cache model are reasonable.** `dedupKey` collapses the same report across sources, the cache is correctly treated as disposable (settings live in DataStore, not Room), and `WsprLiveSource` correctly parses the timezone-naive wspr.live timestamps as UTC.
- **Input throws are safely contained today.** The TX path validates and wraps `encode()` in try/catch, so malformed callsign/grid/power surface as an inline error rather than a crash — the validation findings above are about wrong-but-transmitted data and future callers, not present-day crashes.
- **Dependency version matrix is internally compatible** (AGP 8.7.2 / Kotlin 2.0.21 / KSP 2.0.21-1.0.28 / Gradle 8.9 / Compose BOM 2024.10.01 / Room 2.6.1 / MapLibre 11.13.5).

---

## Completeness critic

I have enough context from this thorough report to assess gaps. Let me identify what's genuinely missing or under-covered for a public Play Store launch.

The review is exceptionally thorough. Here are the genuinely important gaps, not nitpicks:

1. **Target API level / `targetSdkVersion` is never stated or audited.** Play has a hard rule: new apps and updates must target an API level within one year of the latest Android release (currently API 35 / Android 15 as of the Aug 31 2025 deadline; API 36 once enforced). The report cites "Android 15+ 16 KB pages" but never confirms `compileSdk`/`targetSdk` actually meet Play's minimum-target gate — this is itself an automatic rejection if too low, and it's a one-line `build.gradle.kts` fact that should have been verified alongside `versionCode`.

2. **No crash/ANR observability before launch, and no pre-launch-report stability gate.** The report praises "no ANR risk found" and "no crash" in specific paths, but there's no Crashlytics/Play Vitals-equivalent in the build (and the report's H8/B6 explicitly assume "no crash-reporting interceptor"). Shipping a first release with zero field crash visibility means the broken `FROM rx` class of bug would never surface post-launch except via reviews. At minimum, decide consciously (Play's pre-launch report on internal track is free) — this is unaddressed.

3. **Account-deletion / data-deletion path is undeclared.** Because the app collects QRZ account credentials and a callsign identifier, Play's User Data policy requires an in-app and/or web route to delete that collected data (the "Data deletion" section of the Data Safety form, distinct from the privacy policy). B5 covers *declaring* collection but never the *deletion* requirement — for credential-collecting apps this is a frequent rejection and is missing from the checklist.

4. **Test-coverage gap on the network/source layer and Compose UI.** The report verifies the signal-core math well, but every blocker/high bug in the data layer (B1 wrong table, H2 silent error, H7 FORMAT fragility, race in `search()`) points to there being no integration tests around `WsprLiveSource`/`PskReporterSource`/`SpotsViewModel` and no Compose UI tests — the very class of regression most likely to recur. A note that the test suite is effectively unit-only (golden vectors) and lacks source/VM/UI coverage belongs in the report.

5. **Foreground-service policy paperwork, not just the code.** The Medium item correctly recommends a `mediaPlayback` foreground service for TX, but if adopted that triggers its own Play obligations: the `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission, a declared `foregroundServiceType`, and a Play Console foreground-service-use declaration with a video. The fix is proposed without flagging that it adds a new review surface — adopting it late could itself delay launch.

If none of the above are adopted, coverage is otherwise complete — the signing/privacy/data-safety/data-source blockers are the correct top priorities and are well-characterized.
