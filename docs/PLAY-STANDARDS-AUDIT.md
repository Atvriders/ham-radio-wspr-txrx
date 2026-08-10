# Google Play Standards Audit — Ham Radio WSPR TX/RX (`com.atvriders.wsprtxrx`)

**Audit date:** 2026-08-09  ·  **Repo:** `/home/kasm-user/ham-radio-wspr-txrx`  ·  **Target:** first production release, personal developer account

---

## 1. Verdict

**No — this cannot be submitted today, and the earliest realistic production date is ~3 weeks out even after the fixes land.** Two hard gates stop you immediately: the store listing has **zero screenshots** (Play will not publish a listing without two), and the Map screen has a **deterministic force-close** — tap any station marker, then press Home or rotate, and the app crashes on `rememberSaveable` with a non-Parcelable class. Beyond those, three mandatory *App content* declarations are entirely unprepared (foreground-service permissions **with a demo video you currently cannot film**, target audience, app access), the app ships **no in-app privacy-policy link** (an unconditional Play User Data requirement), the Data safety checklist gives two contradictory answer sets for the same questions, and the release pipeline silently falls back to **debug signing** — a fallback that has already fired, putting three debug-signed APKs under the production `applicationId` on public GitHub Releases. None of this is fatal to the concept: the app's actual data handling, transport security, permission *usage*, and transmit-licence gating are genuinely sound. It is paperwork, one crash, and a signing guard. Budget the closed-testing clock first (12 testers × 14 continuous days + up to 7 days production-access review) and fix the repo in parallel.

| Severity | Count | Meaning |
|---|---:|---|
| 🚫 **Blocker** | 2 | Hard gate: cannot publish / deterministic crash |
| 🔴 **High** | 6 | Blocks rollout or is a documented rejection trigger |
| 🟠 **Medium** | 10 | Real quality/compliance risk; fix before closed testing |
| 🟡 **Low** | 18 | Polish, doc accuracy, hygiene (incl. 2 informational) |
| **Total** | **36** | |

> §2 below lists everything that **gates submission or rollout**, regardless of severity label — that includes two Medium and one Low item whose failure mode is a Console form that will not save.

---

## 2. 🚫 Would block or fail review

Ordered by risk. Each item is tagged **CODE** (I can fix in the repo) or **CONSOLE/HUMAN** (only you can do it).

---

### B1 · No screenshots exist — Play will not publish the listing · **CONSOLE/HUMAN** (+ small CODE doc fix)

**What's wrong.** `docs/PLAY_STORE_LISTING.md:83-85` marks phone screenshots "REQUIRED, 2–8" and defers them. The repo contains exactly two images: `docs/store-assets/feature-graphic-1024x500.png` and `docs/store-assets/play-icon-512.png`. No `fastlane/`, no `screenshots/`.

**Why it blocks.** Play Console Help ("Add preview assets"): *"a minimum of two screenshots across different device types is required to publish."* Because this is a personal account, the **main store listing must be complete before a closed-testing rollout**, so this blocks the 14-day clock from even starting. This is the earliest hard gate on your whole timeline.

**Exact fix.**
1. Capture 5 phone screens on a real device or emulator: Spots list (populated), Globe map with a station tapped, Charts + Head2Head, **TX screen un-scrolled** (so the red `tx_disclaimer` banner at `TxScreen.kt:123-133` is in frame — that in-app text is stronger than any marketing caption), Settings.
2. **Two spec traps that will get your upload refused**, neither of which is in your doc:
   - Format must be **JPEG or 24-bit PNG, no alpha**. `adb exec-out screencap -p` emits 32-bit RGBA — flatten with `Image.open(f).convert('RGB')`.
   - **Max dimension ≤ 2× min dimension.** A stock Pixel 6/7/8 capture is 1080×2400 = 2.22:1 and is **rejected**. Either use a 1080×1920 AVD, or pad/centre-crop to ≤1.98:1.
3. Target 1080px short side and ≥4 shots to stay eligible for large-format recommendation surfaces (this is *eligibility*, not the publish minimum of 320px/2 shots).
4. **CODE:** rewrite `docs/PLAY_STORE_LISTING.md:90`. It currently says *"Crop to the device frame; Play accepts PNG/JPEG, 16:9 or 9:16, min 320 px, max 3840 px"* — that omits the alpha rule, omits the 2× rule, and pairs it with a Pixel 6 recommendation that produces non-compliant files. (Device frames are *permitted* on phone listings; the "no device frames" rule is Wear OS only.)

---

### B2 · Hard crash: `SelectedStation` in `rememberSaveable` — force-close on rotate/Home after tapping any map marker · **CODE**

**What's wrong.** `MapScreen.kt:138` — `var selectedStation by rememberSaveable { mutableStateOf<SelectedStation?>(null) }`, where `MapScreen.kt:82-90` declares a plain `data class SelectedStation(...)` with no `@Parcelize` and no `Serializable` (repo-wide grep for `parcelize|Parcelable|Serializable` → zero hits; `kotlin-parcelize` is not in the plugins block).

**Evidence of the throw.** Verified against decompiled `compose-runtime-saveable 1.7.5`: `SaveableStateRegistryImpl.performSave()` throws `IllegalStateException` for any value failing `canBeSavedToBundle`, whose `AcceptableClasses` set is `{Serializable, Parcelable, String, SparseArray, Binder, Size, SizeF}`. `MainActivity` is a `ComponentActivity` with **no `android:configChanges`** in the manifest, so rotation genuinely recreates it.

**Trigger.** Value is `null` (saveable) until a marker is tapped at `MapScreen.kt:245`. After that, the **next `onSaveInstanceState`** kills the process: rotate, press Home, open Recents, split-screen, or fold/unfold. Backgrounding is near-universal in any session, so this is effectively *"select a marker, leave the app, crash"* — 100% reproducible, on a headline feature.

**Exact fix (≈4 lines).**
```kotlin
// app/build.gradle.kts — plugins block (ships with KGP 2.0.21, no version needed)
id("kotlin-parcelize")

// MapScreen.kt:82-90
@Parcelize
data class SelectedStation(...) : Parcelable
```
Zero-build-change alternative: `) : java.io.Serializable`. No extra R8 rule needed — `proguard-android-optimize.txt` already keeps `Parcelable$Creator`.
Also **rewrite the comment at `MapScreen.kt:80-81`** — *"all fields are primitives/nullable"* states the wrong rule and is what caused the bug; it is the **container class** that must be Bundle-storable.
Add a JVM regression test asserting `SelectedStation` implements `Parcelable` or `Serializable` (there is no `androidTest` source set and CI runs only `testReleaseUnitTest`, so an instrumentation test would never run).
⚠️ Do **not** use the `listSaver`/`stateSaver` workaround — `rememberSaveable`'s stateSaver overload is bounded `T : Any` and will not compile for `SelectedStation?`.

---

### B3 · Foreground-service permissions declaration + demo video not prepared · **CONSOLE/HUMAN** (blocked by B4)

**What's wrong.** `app/build.gradle.kts:16` targets SDK 36; `AndroidManifest.xml:11` declares `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `:35-38` declares `foregroundServiceType="mediaPlayback"`. Nothing in `docs/` prepares the declaration — `SIGNING_AND_RELEASE.md:78-79` lists App content as "privacy policy URL, Data Safety, content rating, target audience, ads = No, government app = No" and omits it entirely. Only `docs/PRE-LAUNCH-REVIEW.md:181` flags the obligation.

**Why it blocks.** Play Console Help answer/13392821: apps targeting Android 14+ must declare **each** FGS type under **App content**, supplying (a) a functionality description, (b) the impact if the task is deferred or interrupted, (c) an approved use case, and (d) **"a link to a video demonstrating each foreground service feature."** App content must be complete to roll out to **any** track, including closed testing.

**Exact fix.**
1. **Fix B4 and H3 first** — the video must show the ongoing notification, and today it cannot exist.
2. Use case: *Media playback — continue audio or video playback from the background, including streaming.*
3. Paste-ready **functionality**: "The user enters their amateur callsign, grid locator and power, then taps Transmit. The app generates a 110.6-second audio tone sequence and plays it through the device speaker so it can be acoustically coupled into an amateur radio transceiver. The foreground service keeps this user-initiated audio playback running if the user leaves the app or the screen turns off. The app does not transmit radio-frequency energy; it produces sound only."
4. Paste-ready **deferral/interruption impact**: "WSPR is a strict 110.6-second, time-synchronised protocol that must begin exactly on an even UTC minute. If playback is deferred past that start, or interrupted mid-sequence, the tone is corrupted and no receiving station can decode it; the user must wait for the next even UTC minute to retry."
5. **Video** (60–90 s, unlisted YouTube — must open with no sign-in and stay live indefinitely; a Drive link that prompts for login is a common bounce): one take — TX tab → licence acknowledgement → tap Transmit → countdown → **pull down the shade showing the ongoing notification** → press Home, tone audibly continues → tap **Stop** in the notification → playback ends.
6. **CODE:** commit `docs/FOREGROUND_SERVICE_DECLARATION.md` with the exact submitted text + video URL, and add the item to the App content checklist at `SIGNING_AND_RELEASE.md:78`.

> Type fidelity is defensible: `mediaPlayback` has *"Runtime prerequisites: None"* and its approved use case is literally "continue audio playback from the background." `WsprPlayer` streams real PCM through `AudioTrack` with `USAGE_MEDIA`. **Do not switch type** and **do not add a MediaSession** — a session would expose a timing-exact, unresumable 110.6 s frame to headset/AVRCP/lockscreen pause events.

---

### B4 · `POST_NOTIFICATIONS` never declared — the transmit notification is invisible on Android 13+ · **CODE**

**What's wrong.** `AndroidManifest.xml:4-11` declares five permissions; `POST_NOTIFICATIONS` is not among them, and a repo-wide grep returns zero hits. Confirmed against the built merged manifest — no dependency injects it either. `TxForegroundService.kt:88-96` builds a notification and `:32-39` posts it via `startForeground()`.

**Why it blocks.** A runtime permission absent from the manifest **can never be granted** — the system dialog cannot even be shown. Per Android docs: with the permission denied, users *"still see notices related to foreground services in the Task Manager but don't see them in the notification drawer."* So on effectively the entire 2026 install base, the app drives a 110.6 s transmission into a radio with **no shade-level indicator** — and **the B3 demo video cannot be filmed**, because the shot Play expects does not exist.

**Exact fix.**
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
Request it once, chained onto the existing one-time licence-acknowledgement dialog (`TxScreen.kt:96-115`) rather than as a second gate in front of the time-critical Transmit tap, guarded by `Build.VERSION.SDK_INT >= 33`, reusing the `rememberLauncherForActivityResult` pattern already at `TxScreen.kt:68`. **Never block or delay transmit on the result** — `startForeground()` does not need it. On denial, ask once and stop; drive the in-app state off `NotificationManagerCompat.areNotificationsEnabled()` (catches disabled channels too) and show a small inline note on the TX screen with a button firing `Settings.ACTION_APP_NOTIFICATION_SETTINGS`. No Play declaration form applies — this is not a sensitive permission.

---

### B5 · No privacy-policy link or text inside the app · **CODE**

**What's wrong.** `grep -rni "privacy" app/src/` returns **zero** lines. `SettingsScreen.kt:70-129` ends at "Recent callsigns" — there is no About/legal section, and `SettingsScreen` is the only `Destination.SETTINGS` target (`WsprAppRoot.kt:68`). `res/values/strings.xml` (151 lines) has no privacy string. The policy exists and is live (`https://atvriders.github.io/ham-radio-wspr-txrx/privacy.html`, HTTP 200) but is reachable **only** from the store listing.

**Why it blocks.** Play User Data policy, verbatim: *"All apps must post a privacy policy link in the designated field within Play Console, **and** a privacy policy link or text within the app itself."* Unconditional — and this app stores third-party account credentials and requests location, i.e. the high-scrutiny class. A reviewer verifies it by opening Settings in seconds.

**Exact fix (~15 lines).** Append an "About & legal" `Section` after the recent-calls block (`SettingsScreen.kt:128`) containing:
- a short plain-language summary line,
- the URL as **selectable `Text`** (independently satisfies "link **or text**" if the browser hand-off fails),
- a `TextButton` calling `runCatching { uriHandler.openUri(privacyUrl) }` — **the `runCatching` is required**: `AndroidUriHandler.openUri` throws `IllegalArgumentException` when no browser handles the intent, which would turn a paperwork finding into a crash on a browserless test device,
- `BuildConfig.VERSION_NAME` (`buildConfig = true` is already set at `app/build.gradle.kts:69`).

The URL string resource must be **byte-identical** to the URL entered in the Console privacy-policy field and in the Data safety form — a mismatch is itself a rejection trigger. Do not introduce a second URL constant.

---

### B6 · Release pipeline silently falls back to debug signing — and it has already shipped · **CODE**

**What's wrong.** `app/build.gradle.kts:49-53`:
```kotlin
signingConfig = if (!System.getenv("KEYSTORE_FILE").isNullOrEmpty())
    signingConfigs.getByName("release") else signingConfigs.getByName("debug")
```
This governs `bundleRelease` as well as `assembleRelease`. `.github/workflows/build.yml:84` prints a *warning* and continues; `:119` copies the result to `dist/…-play.aab` **unconditionally**; no signer assertion exists anywhere downstream.

**Evidence it is not theoretical.** Reproduced empirically: `bundleRelease` with no keystore env yields `Owner: C=US, O=Android, CN=Android Debug` and a `META-INF/ANDROIDD.RSA` signature block. Worse, `apksigner` against the five published GitHub Releases shows **v0.1.0, v0.1.1 and v0.2.0 are debug-signed, each with a *different* ephemeral runner key**, all carrying the production `applicationId com.atvriders.wsprtxrx` (the `.debug` suffix is on the *debug* build type only). Those artifacts are publicly downloadable right now and cannot upgrade to each other or to the Play build.

**Why it blocks.** Play's hard upload error: *"You uploaded an APK or Android App Bundle that was signed in debug mode."* The failure is silent at build time and only surfaces after a human has already uploaded.

**Exact fix (three layers).**
1. **Gradle guard (load-bearing — protects local builds too).** Resolve the keystore path once into a `val`, reuse it in `signingConfigs` and the buildType selector, then after the `android { }` block:
```kotlin
if (keystorePath == null) {
    tasks.matching { it.name == "bundleRelease" || it.name == "packageReleaseBundle" }
        .configureEach { doFirst { throw GradleException("Refusing to build a debug-signed Play bundle: KEYSTORE_FILE is unset.") } }
}
```
Use `tasks.matching { }.configureEach` (no eager realisation) and `doFirst` (so `assembleRelease`, `testReleaseUnitTest` and IDE sync are unaffected).
2. **CI fail-fast.** Check **all four** secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and `exit 1` with `::error::`; a present keystore with an empty alias is a different, equally silent failure. Then simplify `build.yml:100` and `:109` to a plain `KEYSTORE_FILE`.
3. **Post-build signer assertion**, pinned to the real upload cert (`SHA-256 cec75e15e33dc7c7edcccf8d3878854bb276cd74440fa771108998fce4ea3bb4`, measured from v1.0.0/v1.0.1) — pinning also catches rotation to the *wrong* key, which a name check cannot.
```bash
case "$(keytool -printcert -jarfile "$aab" | grep -m1 '^Owner:')" in
  *"CN=Android Debug"*) echo "::error::debug-signed"; exit 1 ;;
esac
```
⚠️ Do **not** use `grep -q 'CN=Android Debug' && exit 1` — under `bash -e` that inverts and fails the step on every *correctly* signed build.
4. **Cleanup:** delete or annotate the APK assets on releases v0.1.0/v0.1.1/v0.2.0; anyone holding one must uninstall (losing Room DB, DataStore prefs, encrypted QRZ credentials and the licence acknowledgement) before installing anything newer.

---

### B7 · Data safety checklist gives two contradictory answer sets, and one data type does not exist · **CONSOLE/HUMAN** (+ CODE doc fix)

**What's wrong.** `docs/DATA_SAFETY.md` is the instruction sheet for a mandatory declaration, and it is internally inconsistent:
- `:25` maps the callsign to **Personal info → User IDs**; `:66` maps the *same* callsign to **Other personal info**. Two mutually exclusive types in Play's fixed taxonomy.
- `:34-38` explicitly blesses either Shared answer (*"you may answer No … or answer Yes … either is defensible"*). The form takes **one** deterministic answer.
- `:72-78` declares **"User account credentials"** — a type that does not exist. Play's Personal info list is exactly: Name, Email address, User IDs, Address, Phone number, Race and ethnicity, Political or religious beliefs, Sexual orientation, Other info. The doc's own answer key (`:25-29`) therefore **omits the QRZ password entirely**.
- `:103-104` leaves OpenFreeMap as "declare per your judgment"; `:28`/`:81` say "Search history" where the form's type is "In-app search history".

**Why it blocks.** If the "No" branch is taken, the public label reads *"No data shared with third parties"* directly beside a linked privacy policy whose section heading is literally **"Third parties the app sends data to"** listing five named recipients (`PRIVACY_POLICY.md:47-58`). Under-declaring the QRZ password — which the policy openly says is sent to `xmldata.qrz.com` (`QrzService.kt:52-56` puts it in a URL query parameter) — is the highest-signal inconsistency an automated check or reviewer can find, and misrepresentation here draws removal/suspension, not a warning.

**Exact fix — collapse to one binding answer set; delete the duplicated detail blocks rather than syncing two.**

| Data type | Collected | Shared | Optional | Purpose | Recipients |
|---|---|---|---|---|---|
| Personal info → **User IDs** (amateur callsign, QRZ username) | Yes | **Yes** | Yes | App functionality | wspr.live, PSKReporter, QRZ.com |
| Personal info → **Other info** (QRZ password) | Yes | **Yes** | Yes | App functionality | QRZ.com |
| App activity → **In-app search history** | Yes | **Yes** | Yes | App functionality | wspr.live, PSKReporter (RBN when enabled) |
| **Location** (Approximate **and** Precise) | **No** | No | — | — | — |

- Delete the hedge at `:34-38`; replace with: *"The answer key below is the binding submission. Every declared type is Shared = Yes; the app queries independent third-party services, not contracted service providers."*
- For **Other info**, use the free-text field: *"QRZ.com account password, entered by the user, encrypted at rest with the Android Keystore and excluded from backup, sent to QRZ.com over HTTPS solely to authenticate callsign lookups. Never sent to the developer; the developer operates no server."*
- Resolve `:103-104`: **do not declare** OpenFreeMap — tile requests carry only the IP address inherent to any HTTPS request, and IP is not a type in Play's taxonomy. Keep the row in the privacy policy (over-disclosure is safe; the reverse is not).
- Delete the misfiled Families line at `:98` (that belongs in Target audience, see B9).
- **Location = Not collected is correct and stays correct** even after B-side manifest changes — Play defines collection as transmission off-device, and the grid is computed on-device and never uploaded.

---

### B8 · Privacy policy is missing the required "secure data handling procedures" element · **CONSOLE/HUMAN** (CODE-editable docs, must republish)

**What's wrong.** `docs/PRIVACY_POLICY.md` headings are: Summary (`:10`), Information the app uses (`:21`), Third parties (`:47`), Data stored on your device (`:64`), Data deletion (`:76`), Children (`:87`), Changes (`:91`), Contact (`:95`). There is **no security section**; the only security statements are the incidental QRZ blockquote at `:43-45`. `privacy.html` mirrors the gap.

**Why it blocks.** Play's User Data policy enumerates five things a privacy policy *must* disclose; the third is verbatim **"Secure data handling procedures for personal and sensitive user data."** *"Your privacy policy does not include all required elements"* is a standard rejection string, and this app handles a reusable third-party password. Aggravating factor: the policy already advertises a **cleartext telnet** channel (`:60-62`) with a hedged mitigation and no security section anywhere — that invites element-checking.

**Exact fix.** Add a `## Security` H2 between "Data stored on your device" and "Data deletion", in **both** `PRIVACY_POLICY.md` and `privacy.html`, then bump the "Last updated" date (currently 22 June 2026) and **republish GitHub Pages so the hosted copy matches**. Every claim below was verified in code, so the section is truthful as written:
- **In transit** — all web requests use HTTPS/TLS; cleartext HTTP is disabled for every domain at OS level (`res/xml/network_security_config.xml:9`, `cleartextTrafficPermitted="false"` in base-config). The one exception is the optional Reverse Beacon Network telnet feed, unencrypted by protocol design, which carries only the fixed placeholder callsign `N0CALL` and never your callsign, credentials or location.
- **At rest** — the QRZ password is encrypted with AES-256-GCM using a non-exportable Android Keystore key (`SecretCrypto.kt:69-79`, `:45-54`); if encryption is unavailable the password is discarded rather than written in plaintext (`SettingsStore.kt:56-67`).
- **Backups/transfers** — settings store and spot cache excluded from Auto Backup and device-to-device transfer (`backup_rules.xml`, `data_extraction_rules.xml`).
- **Retention** — cached spots pruned after 7 days; settings persist until changed or cleared.
- **No servers, no third-party SDKs** — no backend, no ads/analytics/crash-reporting/tracking SDKs.
- **Location** — read only on tap, converted to a Maidenhead grid on-device, never transmitted or stored off-device.

---

### B9 · Target audience and content declaration unaddressed · **CONSOLE/HUMAN** (+ CODE doc fix)

**What's wrong.** `docs/CONTENT_RATING.md` is 29 lines of IARC questionnaire only and never mentions age groups; `SIGNING_AND_RELEASE.md:79` names "target audience" with no answer. This is a **separate mandatory declaration** from the content rating, and App content cannot be completed without it.

**Recommended answers.**

| Question | Answer |
|---|---|
| Target age group(s) | **Ages 18 and over only** — do **not** tick 13-15 |
| Store listing unintentionally appeals to children? | **No** |
| Teacher Approved / Designed for Families | Do not opt in |
| Restrict minor access | Leave off (for gambling/dating-class apps) |

**Rationale to keep on file:** the only interactive output is an RF transmit aid behind a one-time amateur-licence acknowledgement (`TxScreen.kt:95-115`); the listing addresses licensed operators and carries a licensing disclaimer (`PLAY_STORE_LISTING.md:21-24`, `:56-60`); the icon and feature graphic are a dark technical wireframe globe with no cartoon or child-directed styling. Ticking any group **12 and under** (and, in some locales, 13-15) pulls the app under Families Policy Requirements and adds child-directed scrutiny of `ACCESS_COARSE_LOCATION` for zero benefit. Target audience declares who the app is *designed and marketed for*, not who may legally use it — minors holding licences can still install it. Content rating "Everyone" alongside target audience 18+ is a normal combination for a technical utility.

**Prerequisites Google requires first:** Ads = No, **App access**, privacy policy URL. App access is undocumented anywhere — see M-Decl in §4.

---

### B10 · Release runbook skips the mandatory 12-tester / 14-day closed test and lists 6 of ~12 App content items · **CONSOLE/HUMAN** (+ CODE doc fix)

**What's wrong.** `docs/SIGNING_AND_RELEASE.md:76-81` says *"Test internally first … Internal testing"* then *"Promote the internal release to Production when you're happy."* For a personal developer account created after 13 Nov 2023, **Production is locked**: you must run a **closed** test with **≥12 testers opted in continuously for 14 days**, then apply for production access (~7 days review). Internal testing does not count. The step described does not exist for this developer, and the doc hides a ~21-day critical path.

Separately, the App content list omits: **App access**, **News apps**, **Financial features**, **Health apps**, **Advertising ID**, and **Foreground service permissions** (B3). App content cannot be marked complete with any required section outstanding.

**Exact fix (CODE, doc):** rewrite steps 3-6 to (3) internal smoke test — *does not count*; (4) closed testing with the 12/14 mechanics spelled out, warning that opting out and rejoining resets continuity and that invited-but-not-installed does not count; (5) the full App content table; (6) FGS declaration; (7) listing + assets; (8) production. Add a one-line banner at the top of the file so the 3-week lead time is visible before anyone reads to the bottom. Also fix the stale `:86` *"Target API level (35)"* → 36.

---

### B11 · App icon is a 24-bit PNG; Play's icon spec is 32-bit with alpha · **CODE**

**What's wrong.** Raw IHDR parse of `docs/store-assets/play-icon-512.png`: 512×512, bit depth 8, **colour type 2** (truecolour, no alpha), 141,577 bytes. Cause: `docs/store-assets/generate.py:194` does `img.convert('RGB')…save(out)`, discarding the RGBA channel built up on lines 131-193. Play's spec is **"32-bit PNG (with alpha), 512px by 512px, max 1024KB."**

**Exact fix (one word).** `generate.py:194` → `img.convert('RGBA')`. **Do not touch line 305** — the feature graphic must stay 24-bit no-alpha, and it is currently correct. Regenerate and **commit the PNG** (fixing the script alone is not enough — the committed binary is what gets uploaded).

**Verified by regenerating:** IHDR colour type 6, alpha min == max == 255 (fully opaque, so it also satisfies Google's "no transparency" *design* guidance), 153.9 KB, and **max per-channel RGB delta vs the current icon is 0** — no visual change, no size risk.
*(Confidence note: the icon spec is published and historically enforced, but I could not reproduce a current-console rejection message for icon bit depth. Worst case is an inline form error; the fix is free either way.)*

---

## 3. ⚠️ High priority

Real quality/compliance risks. None blocks upload, but each either degrades the review outcome, undermines a declaration you are about to make, or breaks the app's flagship feature.

---

### H1 · MapLibre injects `ACCESS_FINE_LOCATION` + `ACCESS_WIFI_STATE` into the shipped bundle · **CODE** · 2-line fix, empirically verified

`app/src/main/AndroidManifest.xml:6-7` comments *"FINE is not requested to de-risk Play review"* — but the **actually-shipped** manifest contradicts it. Verified in the built artifact, not inferred: `app/build/outputs/bundle/release/app-release.aab` → `base/manifest/AndroidManifest.xml` contains `ACCESS_FINE_LOCATION` and `ACCESS_WIFI_STATE`, injected by `org.maplibre.gl:android-sdk:11.13.5` (extracted the AAR; its manifest declares both). The app uses neither: zero `LocationComponent`/`PermissionsManager` references in `app/src`, and MapLibre's own `classes.jar` and all four `libmaplibre.so` contain **zero** `WifiManager`/`getConnectionInfo` references — the WiFi permission is vestigial Mapbox-telemetry heritage.

**Consequence.** The store listing's auto-generated permission list will read **"precise location"** and **"view Wi-Fi connections"** next to a privacy policy that says approximate-only. That is a visible trust discrepancy and a literal breach of the minimum-scope principle in *Permissions and APIs that Access Sensitive Information*. It is **not** an upload block (only `ACCESS_BACKGROUND_LOCATION` triggers the declaration form) and it does **not** falsify the Data safety answers. Forward risk: when you eventually target 37, the *Minimum Scope: Foreground Location Access* policy (enforcement ~late Oct 2026) will require a Console declaration justifying any declared FINE — stripping it now permanently removes that obligation.

**Fix.** Add `xmlns:tools` to `<manifest>`, then:
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" tools:node="remove" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"    tools:node="remove" />
<uses-feature    android:name="android.hardware.wifi"                    tools:node="remove" />
<!-- and, separately, stop Play inferring a hard location requirement: -->
<uses-feature android:name="android.hardware.location"         android:required="false" />
<uses-feature android:name="android.hardware.location.gps"     android:required="false" />
<uses-feature android:name="android.hardware.location.network" android:required="false" />
```
The last three matter because location permissions **imply `android.hardware.location` as required=true**, excluding Android TV / some Chromebook configurations from the device catalogue for no benefit (the grid is typeable by hand — `TxViewModel.grid` is a plain user field). Verify with `./gradlew :app:processReleaseManifest` and grep the merged output; expect exactly the five app permissions plus androidx's signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

⚠️ **A CI guard here must be a *denylist*, not an allowlist** — an "exactly these five" assertion fails immediately on the androidx-injected permission. Use:
```bash
for P in ACCESS_FINE_LOCATION ACCESS_BACKGROUND_LOCATION ACCESS_WIFI_STATE RECORD_AUDIO QUERY_ALL_PACKAGES; do
  grep -q "android.permission.$P" "$M" && { echo "FAIL: $P leaked"; exit 1; }
done
```
Also update the now-stale comment at `:6-8`, and the stale references in `docs/PRE-LAUNCH-REVIEW.md:98` and `docs/superpowers/plans/2026-06-19-*.md:318`.

---

### H2 · The foreground service is started **after** the up-to-2-minute wait — the one scenario it exists for is the one it cannot cover · **CODE**

`TxViewModel.kt:88-97` busy-waits for the next even UTC minute (0–120 s); `keepAlive.start()` is only reached at `:102`, after the phase flips to TRANSMITTING. Three compounding defects:

1. **FGS start is refused.** If the user taps Transmit and leaves (or the screen locks — `TxScreen.kt:62-66` scopes `keepScreenOn` to the composable), the process is background by `:102`. `Context.startForegroundService()` throws `ForegroundServiceStartNotAllowedException`; `TxForegroundService.kt:60-68` swallows it via `runCatching{}.getOrDefault(false)` and `TxViewModel.kt:102` **discards the boolean**. AOSP has no grace period for a recently-foregrounded app, and "activity in the back stack" is explicitly carved *out* of the exemption list.
2. **Audio focus is then denied.** Verbatim from Android 15 behaviour changes: *"Apps that target Android 15 (API level 35) must be the top app or running a foreground service in order to request audio focus … the call returns `AUDIOFOCUS_REQUEST_FAILED`."* `WsprPlayer.kt:86-92` logs and **transmits anyway**. `GAIN_TRANSIENT_EXCLUSIVE` is refused during a call — precisely when you must not emit. There is also **no `OnAudioFocusChangeListener`** (`WsprPlayer.kt:83-85`), so an incoming call at t+30 s corrupts the frame silently.
3. **No drift guard.** On thaw, `remaining <= 0` breaks the loop immediately and the app transmits at an arbitrary phase of the slot — an out-of-slot, undecodable emission on shared spectrum. No `WAKE_LOCK` is declared, so an FGS alone does not keep the CPU awake.

**Fix.** Start the keep-alive at the **tap**, while the app is demonstrably foreground, and hold it across WAITING + TRANSMITTING with a single `finally { keepAlive.stop() }`. Capture and surface the boolean as a non-fatal `warning` in `TxUiState` (*"Background transmit protection unavailable — keep this screen open"*) rather than aborting. Add `WAKE_LOCK` (normal protection, zero Play impact) with a bounded `wl.acquire(150_000L)` in the service. Measure the wait **monotonically** (`elapsedRealtime` deltas against a wall-clock slot target, injected as a lambda so existing `TxSchedulerTest` still runs on the JVM) and, if `now - slotWall > 1_500 ms`, **re-arm for the next slot instead of transmitting late**. In `WsprPlayer`, abort on denied focus with a typed failure surfaced to the UI, register a focus-change listener that cancels in-flight transmission, and build the track with `AudioTrack.Builder` + matching `AudioAttributes` instead of the deprecated `STREAM_MUSIC` constructor at `:44-51`.

🚫 **Do not add `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`.** `USE_EXACT_ALARM` is Play-restricted to alarm-clock/calendar apps; a WSPR app does not qualify and requesting it invites the exact rejection this audit is trying to avoid.

*Also note (larger, decide deliberately):* playback lives in `viewModelScope`, so a task swipe cancels `txJob` and cuts the carrier mid-slot regardless of the service. For an RF transmitter that is arguably the *safer* default — if you move playback into the service, keep `onTaskRemoved` → stop the transmit, not just `stopSelf`.

---

### H3 · The transmit notification is inert: no tap target, no Stop action, deferred display · **CODE**

`TxForegroundService.kt:88-96` sets only ContentTitle/ContentText/SmallIcon/Ongoing/`CATEGORY_SERVICE`/PRIORITY_LOW. There is **no `setContentIntent`**, **no `addAction`**, and no `FOREGROUND_SERVICE_IMMEDIATE` — so on Android 12+ display may be deferred ~10 s, roughly **9% of the entire 110.6 s transmission**. The only stop control is the in-app button at `TxScreen.kt:186-189`. `strings.xml:40` reads *"Keeping the transmission alive — do not close the app"* — truthful about the architecture, but it advertises fragility in the very demo video a reviewer will watch.

**Fix.** Add `setContentIntent` → `MainActivity` (`FLAG_IMMUTABLE`, `FLAG_ACTIVITY_SINGLE_TOP`), a **Stop** action, `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)`, `setUsesChronometer(true)` + `setChronometerCountDown(true)` for a live remaining-time readout (highest-value single line for the B3 video), `setOnlyAlertOnce(true)`, and `CATEGORY_TRANSPORT`. Replace `android.R.drawable.ic_media_play` with an app-owned white-on-transparent vector — framework drawables are not a stable status-bar-icon API across OEMs. Reword `strings.xml:40` to *"Transmitting for 110.6 s — tap Stop to end early."*

⚠️ **Wiring caveat, do not skip:** audio is owned by `WsprPlayer` inside `TxViewModel` (`WsprViewModelFactory.kt:18` only bridges service start/stop). A Stop action that merely calls `stopSelf()` **kills the notification while the tone keeps playing** — strictly worse than today. Route `ACTION_STOP` through a process-scoped signal on `AppContainer` (e.g. `@Volatile var onTxStopRequested: (() -> Unit)?` registered by the factory, or a `MutableSharedFlow` the ViewModel collects) that drives the same path as `TxViewModel.stop()`. `WsprPlayer.play()` is cancellation-responsive (`ensureActive()` every ~46 ms with a `finally` that stops/releases the track and abandons focus), so this works cleanly. Add a unit test that `ACTION_STOP` cancels the transmit **job**, not just the service.

---

### H4 · `runCatching` swallows `CancellationException` → permanent bogus "Couldn't load spots" banner over correct data · **CODE**

`SpotsViewModel.kt:78-92` wraps `repository.search(...)` in `runCatching`, which catches `Throwable` — including the `JobCancellationException` from the single-flight `searchJob?.cancel()`. The `onFailure` body is non-suspending, so it runs anyway. `onSuccess` (`:82-88`) **never resets `error`**, and `ErrorBanner.kt:25-26` renders unconditionally whenever `error != null`.

**The ordering is deterministic, not racy:** every source does blocking OkHttp inside `withContext(Dispatchers.IO)` (`WsprLiveSource.kt:31-42`, `PskReporterSource.kt:36`, `RbnSource.kt:35` telnet). `cancel()` cannot interrupt `execute()`, so request #1 unwinds *after* search #2 has already cleared `error`, writing `error = "StandaloneCoroutine was cancelled"` **and** `loading = false` over an in-flight search. The banner then survives the successful result until some later search resets it.

**Reachability is high:** the ViewModel starts a search in `init` (`:63`), and no control is disabled while `loading` — tapping a recent-call chip, the filter-sheet Apply, IME Search, or the banner's own Retry during cold start all reproduce it. The robo crawler taps rapidly and will screenshot it. It also leaks an internal Kotlin class name into the UI.

**Fix.** Replace with `try/catch`, `catch (e: CancellationException) { throw e }` **first**, `ensureActive()` before the success write, and `error = null` on success. Apply the same pattern to `lookupQrz` (`:100-107`) with a cancellable `qrzJob`. Map `e.message` to a string resource so raw exception text (`Unable to resolve host "db1.wspr.live"…`) never reaches the banner.

⚠️ **The obvious test false-passes.** Under `StandardTestDispatcher`, cancelling job #1 queues its resumption *ahead* of job #2, so the bogus write lands before the reset and gets cleared. You must model the uninterruptible blocking IO (`withContext(NonCancellable) { delay(1_000) }` in the fake repo) — and confirm the test **fails against the current code** before landing the fix.

---

### H5 · Content drawn under the navigation bar and display cutout on every non-compact layout · **CODE** · one-line primary fix

`WsprAppRoot.kt:58` — `Box(Modifier.fillMaxSize().statusBarsPadding())` is the **only** inset handling for all five screens (repo-wide grep finds just this and `navigationBarsPadding()` in a bottom sheet at `QueryControls.kt:151`).

Verified by decompiling the resolved artifact (BOM 2024.10.01 resolves **material3-adaptive-navigation-suite 1.3.1**): `NavigationSuiteScaffold` calls `consumeWindowInsets` with `NavigationBarDefaults.windowInsets.only(Bottom)` in bar mode but only `.only(Start)` in **rail/drawer** mode — it never accounts for the bottom inset in rail mode and never handles `displayCutout` in any mode. Rail is selected when width is MEDIUM/EXPANDED and height is not COMPACT: **tablets (both orientations), unfolded foldables, large freeform windows**.

**Concrete breakage:** on a tablet with 3-button nav, the Transmit/Stop button (`TxScreen.kt:186-199`) is the last child of a scrolled Column with 16dp padding — at max scroll roughly 32dp of its ~40dp height sits under an opaque, tap-swallowing bar. The last row of `SpotsScreen.kt:162` (`LazyColumn`, no contentPadding) cannot be scrolled clear. `MapScreen.kt:281-293` station card and `:279` legend sit under system UI, and MapLibre's OSM attribution/logo (bottom-left) are obscured. In phone **landscape** the app stays in bar mode but takes the *horizontal* inset and cutout hit (legend, text fields).

**Correction to a common misreading:** this is **not** an Android 16 regression. `MainActivity.kt:15` already calls `enableEdgeToEdge()` and `themes.xml` has no opt-out attribute, so the app is edge-to-edge today at any targetSdk. It is already broken — that is the reason to fix it, not the 36 bump.

**Fix (one line):** `WsprAppRoot.kt:58` → `Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)`. Safe against double-padding because `windowInsetsPadding` honours `consumeWindowInsets`.
🚫 **Do not also add** `contentPadding = WindowInsets.safeDrawing.only(Bottom).asPaddingValues()` to `SpotsScreen` — `asPaddingValues()` is a raw conversion that ignores consumption and will double-pad by the full nav-bar height.
🚫 **Map:** `MapLibreMap.setPadding()` is camera padding and does **not** move the logo/attribution ornaments. Either let the root padding inset the MapView (simplest, ship this) or go full-bleed and set `uiSettings.setLogoMargins/setAttributionMargins/setCompassMargins` **in pixels**.
**Verify on:** tablet at sw800dp in *both* orientations with 3-button nav (the true worst case), an unfolded-foldable profile, a 6.7" phone in landscape with a cutout, and phone portrait as a regression check.

---

### H6 · No IME inset handling — keyboard covers the QRZ password / Transmit controls · **CODE**

Repo-wide grep for `imePadding`, `WindowInsets.ime`, `windowSoftInputMode`: **zero hits**. `enableEdgeToEdge()` calls `setDecorFitsSystemWindows(window, false)`, so from API 30 the window is not resized for the IME — the keyboard becomes an inset the app must consume. The scroll viewports therefore keep full window height and Compose's bring-into-view has nothing to scroll toward. Affected: `TxScreen.kt:117-118` (callsign `:135-142`, grid `:145-152`, Transmit button) and `SettingsScreen.kt:66-67` (QRZ username `:78`, password `:79-82`, Save button `:83`).

**Scope correction:** `QueryControls.kt:73` (the app's most-used field) is **not** affected — it is pinned in the fixed header. `ChartsScreen` has no text field at all. On a portrait phone the symptom is usually unreachable *buttons*, not hidden fields; the genuinely bad case is landscape / small window / large font scale, where the masked password field is typed into blind.

**Fix (3 parts).**
1. `AndroidManifest.xml` MainActivity: `android:windowSoftInputMode="adjustResize"` — **required** for the minSdk-26..29 floor, where AndroidX derives `Type.ime()` from `systemWindowInsets.bottom - stableInsets.bottom` and reports 0 in an unresized window.
2. Root (`WsprAppRoot.kt:58`) — if you prefer to keep the bottom to the nav suite, use `WindowInsets.safeDrawing.only(Horizontal + Top)`; otherwise the H5 `safeDrawing` fix covers it.
3. `.imePadding()` on exactly the two scroll containers — **before `.padding(16.dp)` and outside `verticalScroll`**. Ordering is load-bearing: `.verticalScroll(...).imePadding()` pads the *content* instead of shrinking the viewport and does not fix bring-into-view.

---

### H7 · `startForeground()` unguarded in `onStartCommand` — an uncatchable crash path · **CODE**

`TxForegroundService.kt:28-41` calls `startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)` with no try/catch. The caller side (`:60-68`) is guarded, but that protection does not reach into the service callback. AOSP `Service.java` documents `@throws ForegroundServiceStartNotAllowedException` for targetSdk ≥ 31, plus `SecurityException` / `Invalid`/`MissingForegroundServiceTypeException` for targetSdk ≥ 34 (you are on 36). Reachable when the FGS allowance is revoked between `startForegroundService()` and `onStartCommand` (user hits Home/power in that window), on a restart onto a live `ServiceRecord`, in the "Restricted" battery bucket, or on OEM frameworks that only enforce at promotion.

**Fix.**
```kotlin
val promoted = runCatching {
    ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(this),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
}.isSuccess
if (!promoted) { stopSelf(); return START_NOT_STICKY }
```
⚠️ **`stopSelf()` on failure is mandatory, not optional** — a service started via `startForegroundService()` that never reaches foreground state is killed with `ForegroundServiceDidNotStartInTimeException` after ~5 s. You would trade one crash for another.
⚠️ **Correction:** the `Build.VERSION_CODES.Q` branch at `:37-39` is **not** dead code — minSdk is 26 and the 3-arg overload only exists from API 29. `ServiceCompat` is a simplification, not a removal. Also add `override fun onTaskRemoved(rootIntent: Intent?) { stopSelf() }`.

---

### H8 · AGP 8.7.2 is below Google's documented minimum for compileSdk 36, and the warning is suppressed · **CODE**

`gradle/libs.versions.toml:2` pins `agp = "8.7.2"` against `compileSdk = 36` / `targetSdk = 36` (`app/build.gradle.kts:11,16`), with `gradle.properties:6-8` carrying `android.suppressUnsupportedCompileSdk=36` and the comment *"AGP 8.7.2 is tested to 35 … to keep the build warning-free."* Google's compatibility table: **API 36 → minimum AGP 8.9.1** (API 36.1 → 8.13.0). AGP 8.7 release notes state verbatim: *"The maximum API level that Android Gradle plugin 8.7 supports is API level 35."*

**Why it matters (and what it is not).** No Play policy reads the AGP version and the AAB uploads fine — this is **not** a compliance issue. It is a correctness risk in a shipped, minified, resource-shrunk artifact: AGP 8.7.2's default build-tools is 35.0.0, so aapt2 35 compiles resources against the API 36 `android.jar`; R8 8.7.x's API database tops out at 35, affecting API-conditional outlining/desugaring; the manifest merger predates Android 16. The only signal was silenced.

**Fix.**
1. `libs.versions.toml:2` → `agp = "8.13.0"` (last stable 8.x, supports API 36.1, JDK 17 unchanged — CI already provisions temurin 17). **Do not jump to AGP 9.x** before launch (requires Gradle 9.x + DSL removals).
2. Regenerate the wrapper **twice** with the official checksum — `gradle/actions/wrapper-validation@v4` runs in both CI jobs and a stale/omitted `distributionSha256Sum` fails it:
   `./gradlew wrapper --gradle-version 8.13 --distribution-type bin --gradle-distribution-sha256-sum 20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78` (run twice).
3. **Delete all three lines** at `gradle.properties:6-8`. The upgrade is only proven if the build is clean *without* the flag.
4. **Add lint to CI — this is the half with teeth.** `.github/workflows/build.yml` runs **no lint task at any AGP version**: only `testReleaseUnitTest`, `assembleRelease`, `bundleRelease`. Add `./gradlew :app:lintRelease --stacktrace` to both jobs and upload the HTML report. Expect a backlog on first run; triage, then consider `lint { abortOnError = true }`. Two hits to expect: **predictive back** (nothing declares `android:enableOnBackInvokedCallback` and there is no `BackHandler` anywhere — decide deliberately) and edge-to-edge (H5/H6).
5. Re-verify after: `./gradlew clean :app:testReleaseUnitTest :app:lintRelease :app:bundleRelease`.

✅ **Already fine, keep it that way:** 16 KB page-size compliance is satisfied — all three shipped `.so` files (`libmaplibre.so`, `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`) report `LOAD` alignment `0x4000`. That one *is* a Play upload gate, so re-check it after the AGP bump.

---

## 4. Medium / polish

Grouped and terse. All CODE unless marked.

**Build & release pipeline**
- **`versionCode` from `github.run_number`** (`build.yml:99`, `:108`; `app/build.gradle.kts:19`). Re-running a workflow reuses the number (`run_attempt` is what increments), and renaming the workflow file resets it to 1 — permanently unuploadable. Derive it from the tag in a `run:` block writing `$GITHUB_ENV`: `v1.2.3 → 1002003`. ⚠️ **You must delete the `VERSION_CODE:` line from *both* step-level `env:` blocks** — a step `env:` overrides `$GITHUB_ENV` and silently defeats the fix. 🚫 Never put `$(( … ))` in a YAML `env:` value: it is passed literally, `toIntOrNull()` returns null, and you ship a signed AAB with **versionCode 1**. Interim workaround needs no code: use *Run workflow* on the tag ref rather than *Re-run jobs*.
- **Sideload APK ≠ Play install — decide before app creation.** `app/build.gradle.kts:49-53` signs `assembleRelease` with the upload key too, and `build.yml:136-141` attaches it to public Releases. Under Play App Signing, Google re-signs with a different key, so GitHub↔Play can never update each other (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, data loss on uninstall). **Recommendation: keep the Google-generated app signing key** (a self-provided one cannot be reset if it leaks, and yours lives in a GitHub Actions secret) and warn users in the Release body and README. 🚫 Do **not** add `applicationIdSuffix = ".sideload"` — it trades a one-time uninstall for a permanent fork. **This choice is only cheap right now**, at app creation.
- **Stale docs:** `SIGNING_AND_RELEASE.md:84` ("sideload APK stays debug-signed") is false; `:86` says target API 35; `README.md:29-31` repeats the debug-signed claim and `:38` says compileSdk 35.

**Store listing copy & assets** *(CONSOLE-facing, CODE-editable)*
- **Short description is 82 chars** (`PLAY_STORE_LISTING.md:15`), against a hard 80-char cap the Console will not save — and line 17 self-certifies "(80 chars)", which is why it slipped through. Replace with: `See who hears you on WSPR: live spots, globe map, charts, audio WSPR encoder.` (77). This also fixes the unqualified *"a transmitter"* framing → **"audio WSPR encoder"**, pre-empting any misrepresented-functionality question. Delete or requalify the line-17 alternate, which repeats the bare "transmit WSPR" wording. Fix line 4's false "everything below is within them".
- **Full description is hard-wrapped** at ~90 columns (`:21-62`). Play preserves newlines verbatim; the two-space bullet continuations (`:28,:30,:32,:38,:42,:47,:54`) render as detached orphan fragments. Unwrap to one physical line per paragraph/bullet (currently 2,239 / 4,000 chars, ample room) and add a note above the fence so nobody re-wraps it. **Keep en-GB spelling** — it matches `strings.xml` and the Kotlin API (`licenceAcknowledged`, `settings_band_colours`).
- **No tablet/foldable screenshots** while `:46` advertises adaptive layouts. Not a publish gate, but without ≥4 shots at ≥1080px under the 7"/10" tabs you are excluded from tablet/Chromebook recommendation surfaces — the exact audience this feature was built for. There is no separate foldable tab; file unfolded shots under tablet. Note `:90`'s "min 320px" is the phone spec and is wrong for large-screen tabs (1,080–7,680px).

**Privacy & doc accuracy** *(edit `.md` **and** `.html`, bump both dates, republish Pages)*
- **QRZ absolute claim.** `PRIVACY_POLICY.md:41` / `privacy.html:68`: *"If you do not enter QRZ credentials, no data is sent to QRZ.com."* Falsified by `SpotDetail.kt:79-81`, an ungated **QRZ.com** button on every spot detail opening `https://www.qrz.com/db/<callsign>`. Reword to scope the claim to API lookups and add a table row for the browser hand-off. (No Data safety change — the user-initiated-transfer basis is already cited.)
- **RBN "by default"** (`:60-62`, `:56`) implies a mode where your real callsign goes out in cleartext. There is none — `RbnSource.kt:24` defaults to `N0CALL` and `AppContainer.kt:70` constructs it with no args. State it flatly, fix the table row too, and **pin it with a unit test** asserting the outbound bytes are `"N0CALL\r\n"`. Then strike the conflicting advice at `PRE-LAUNCH-REVIEW.md:58` (H6), which recommends sending the user's real callsign over that plaintext socket — executing it would make the policy false *and* break the "encrypted in transit" answer.
- **Retention is incomplete** (`:64-74` covers only the 7-day spot cache). `SettingsStore.kt:82-86` keeps QRZ credentials and the 20 most-recent callsigns indefinitely. Add a "Data retention" block: *"retained for as long as the app is installed — no automatic expiry"* (avoid promising periods you do not enforce), plus "developer holds no copies" and a pointer to third parties' own policies.
- **PSKReporter row overstates** what is sent (`:55` claims callsign, grid, band). `PskReporterSource.kt:40-51` sends only `flowStartSeconds`, `rronly`, `appcontact`, and one of `senderCallsign`/`receiverCallsign`. Also disclose that `appcontact` is a fixed **developer** address, not the user's. Fix the same shorthand at `DATA_SAFETY.md:82-84`. 🚫 Do **not** add "band and distance filters are applied on your device" — `SpotRepository.search` does not post-filter PSK results, so that would swap one inaccuracy for another.

**Attribution & licensing** *(no Play enforcement here — this is licence obligation and professionalism)*
- **No OSS notices anywhere.** The APK redistributes BSD-2-Clause (MapLibre `android-sdk` 11.13.5, `maplibre-android-gestures` 0.0.4) and Apache-2.0 (OkHttp 4.12.0, all AndroidX/Compose/Room/DataStore, kotlinx, `android-sdk-geojson`/`turf` 6.0.1) code; both condition binary redistribution on reproducing notices. Ship `res/raw/third_party_licences.txt` (full BSD-2 text + the three MapLibre copyright lines, full Apache-2.0 text with per-library attribution, OkHttp's embedded Public Suffix List MPL-2.0 note, and the OSM/OpenMapTiles/OpenFreeMap data credits) behind the same **About** screen you add for B5. Add a test that reads `libs.versions.toml` and asserts every `implementation(...)` coordinate appears in the notice file. 🚫 Do **not** use `oss-licenses-plugin` — it pulls in Play Services, and MapLibre's POM says only `<name>BSD</name>`, so the actual licence text and copyright lines would **not** be emitted for your highest-obligation dependency.
- **`app/build.gradle.kts:73`** excludes `META-INF/{AL2.0,LGPL2.1}` — verified **inert** (those files exist only in JNA, not in the app graph; the built AAB has no such entry). Leave it; it is AGP template boilerplate, not a live strip.
- **OSM credit is behind an unlabelled (i) button that the station card covers.** `MapScreen.kt:130-133` uses the one-arg `MapView(Context)` ctor, so MapLibre's defaults put logo + (i) at `Gravity.BOTTOM|START` (verified from bytecode: `attributionGravity=8388691`) — directly under the full-width `StationInfoCard` (`:281-293`, `:312 fillMaxWidth()`). Add a persistent `© OpenStreetMap contributors` chip at `BottomEnd` linking to `openstreetmap.org/copyright`, give the card `bottom = 44.dp` clearance, and inject an `attribution` key onto the `openmaptiles` source in `globeStyleJson()` (`:476-487`) so the credit survives an upstream TileJSON regression. Add the same credit to the listing full description and README.
- **No `LICENSE` file**, and `WsprSync.kt:7-8` / `WsprMessage.kt:9-10` / `WsprEncoder.kt:9-10` cite **GPLv3 WSJT-X source files** as the origin of the tables while `README.md:17-18` claims a *"clean-room implementation."* The substance is fine — every constant (both generator polynomials, the 162-bit sync vector, the bit-reversal interleave, the source-coding formulas) is published verbatim in G4JNT's *"The WSPR Coding Process"* (2009), and the polynomials are a 1971 JPL code. But the repo hands a complainant a ready-made narrative. Recite G4JNT as the derivation source, keep the WSJT-X mention only in `WsprEncoderTest` reworded as a **test oracle**, drop the term-of-art "clean-room", and add `LICENSE` (Apache-2.0 matches your dependency ecosystem; an unlicensed public repo defaults to all-rights-reserved).

**Robustness**
- **DataStore has no `.catch` and no corruption handler** (`SettingsStore.kt:21`, `:44`), collected unprotected in three places (`SpotsViewModel.kt:57`, `SettingsViewModel.kt:17`, `AppContainer.kt:86`). A corrupt prefs file is then a **crash loop on every launch** with no recovery but clearing app data. Add `ReplaceFileCorruptionHandler { emptyPreferences() }` **and** `.catch { if (it is IOException) emit(emptyPreferences()) else throw it }` (both — the handler only covers `CorruptionException`, the catch alone leaves the bad file in place forever). Route `settingsSnapshot()` (`:129-130`) through the guarded flow, and guard the single private `edit()` helper (`:132-134`) — that closes the higher-probability disk-full path for all nine setters at once. Consider making `setLicenceAcknowledged` return success so the TX gate never appears accepted when it was not persisted.
- **~2,400 GeoJSON features built on the main thread** on every map refresh (`MapScreen.kt:296-302`). Wrap `buildPoints`/`buildLines` in `withContext(Dispatchers.Default)` and call `setGeoJson` back on Main. 🚫 Do **not** switch to `toJson()` + `setGeoJson(String)` — MapLibre 11.13.5's `setGeoJson(FeatureCollection)` already hands off to a background `FeatureConverter` with no Gson pass; the String overload *adds* serialisation. ⚠️ `Source.checkThread()` throws only in debuggable builds, so an off-thread mutation would pass release and crash debug.
- **PSKReporter `appcontact` is undeliverable** (`PskReporterSource.kt:66-67`): `ham-radio-wspr-txrx@users.noreply.github.com` — that domain accepts no inbound mail, and it is not even a valid GitHub noreply handle. The whole point of the parameter is reachability *before* the operator throttles or blocks every install. Point it at a real monitored mailbox (the one already published at `PLAY_STORE_LISTING.md:72`). 🚫 Not a `+` alias — OkHttp leaves `+` unencoded in query components. Add a test asserting the value is deliverable-shaped. Mark `PRE-LAUNCH-REVIEW.md:73` resolved.
- **Functional bug found while auditing:** band/grid/distance/power filters are honoured for RBN (`RbnSource.kt:79`) and wspr.live (server-side SQL) but **never applied to PSKReporter results** — `SpotRepository.search` merges without post-filtering. A band-filtered search silently shows unfiltered PSK spots.

**Declarations**
- **App access should be "restricted", not the default.** `QrzService.kt:48-59` returns null on blank credentials and `SpotDetail.kt:60-73` renders "No QRZ data", so an advertised feature (`PLAY_STORE_LISTING.md:30`) is gated on a third-party account. Select *"All or some functionality is restricted"*, **leave username/password blank** (both fields are optional), and explain in the free-text box that QRZ's XML API needs *their* paid subscription so no working test credentials can be supplied, that everything else works with no account, and — most valuable line — that **the first transmit shows a one-time licence dialog that must be accepted**. 🚫 Do not create or share a QRZ account for the reviewer: a free account authenticates but returns subscription-required on lookups, so they would follow your instructions and still see "No QRZ data". Commit the wording as `docs/APP_ACCESS.md` and add it to the `SIGNING_AND_RELEASE.md` checklist. Also soften `PLAY_STORE_LISTING.md:30` to *"optional QRZ.com callsign lookup using your own QRZ.com account."*

---

## 5. Low / informational

- Implicit `android.hardware.location` requirement narrows the device catalogue (Android TV / some Chromebooks) — folded into the H1 manifest edit.
- MapLibre's injected `<uses-feature android:name="android.hardware.wifi" required="false">` filters nothing; removal is cosmetic tidiness only.
- `PRIVACY_POLICY.md:54` (wspr.live row) under-lists distance/power filters — harmless over-generalisation, widen if editing anyway.
- `DATA_SAFETY.md:28`/`:81` say "Search history"; the form's type is "**In-app** search history".
- ALL-CAPS section headers in the full description are fine — the ALL CAPS restriction applies to titles, icons and developer names, not descriptions.
- `TxScreen.kt:222` `lastLocation()` queries `GPS_PROVIDER` first; with coarse-only grant that `SecurityException` is already swallowed by `runCatching` and falls through to `NETWORK_PROVIDER`. No behaviour change from H1, but dropping `GPS_PROVIDER` makes the code match the policy.
- `TxViewModel.kt:109` catches `Exception` including `CancellationException`, so pressing **Stop** sets `error = "StandaloneCoroutine was cancelled"`, rendered verbatim at `TxScreen.kt:183` — same class as H4, fix in the same pass.
- `WsprAudio.renderPcm` (162 × 8192 `sin()` calls) runs on `Dispatchers.Main.immediate` — move to `Dispatchers.Default`.
- Tab-switching discards map camera and selection: `WsprAppRoot.kt:63` uses a bare `when` with no `SaveableStateHolder` (so the `MapScreen.kt:80-81` "tab-switch" claim is doubly wrong). ⚠️ **Add the holder only *after* B2** — `SaveableStateHolderImpl.saveTo()` calls `performSave()` on every tab switch and would add a new crash path.
- `SpotsViewModel.sortSpots()` returns a new list, so changing sort order on the List tab re-triggers a full map rebuild (`spots` is a `LaunchedEffect` key).
- The signing material in the working tree (`upload-keystore.p12`, `SIGNING-CREDENTIALS.txt`) is correctly `.gitignore`d and confirmed **untracked** — no leak.

**🚫 Explicitly do NOT do (traps in circulating advice):**
`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` · a `MediaSession` for the transmit tone · `applicationIdSuffix = ".sideload"` · `oss-licenses-plugin` · `grep -q 'CN=Android Debug' && exit 1` (inverts) · `$(( … ))` inside a YAML `env:` · an "exactly these permissions" CI allowlist · `MapLibreMap.setPadding()` for attribution · `contentPadding` + root `safeDrawing` together · `.verticalScroll(...).imePadding()` ordering · `context.startService(ACTION_STOP)` (throws on API 31+ in the background) · catching `startForeground` failure without `stopSelf()`.

---

## 6. ✅ Pre-submission checklist

### A · I can fix in the repo (CODE)

1. **`@Parcelize SelectedStation`** + `kotlin-parcelize` plugin + rewrite the wrong comment + JVM saveability test. *(B2 — the crash)*
2. **Declare + request `POST_NOTIFICATIONS`**, chained onto the licence dialog, never blocking transmit. *(B4 — unblocks the FGS video)*
3. **Rebuild the transmit notification:** `setContentIntent`, **Stop** action routed through an `AppContainer` stop signal (not `stopSelf`), `FOREGROUND_SERVICE_IMMEDIATE`, chronometer, app-owned mono icon, reworded `strings.xml:40`. *(H3)*
4. **Move `keepAlive.start()` to the Transmit tap**; add bounded `WAKE_LOCK`; monotonic wait + ±1.5 s drift guard with re-arm; abort on denied audio focus + register a focus-change listener. *(H2)*
5. **Guard `startForeground` with `ServiceCompat` + `runCatching` + `stopSelf()`**, add `onTaskRemoved`. *(H7)*
6. **Add the Settings → About section**: privacy-policy link (`runCatching` around `openUri`) + selectable URL text + version. *(B5)*
7. **Signing guards:** Gradle refusal to `bundleRelease` without a key, CI four-secret fail-fast, `keytool` signer assertion pinned to `cec75e15…`. *(B6)*
8. **Strip `ACCESS_FINE_LOCATION` / `ACCESS_WIFI_STATE`** with `tools:node="remove"`, add the three `uses-feature required="false"` lines, add a **denylist** CI guard, verify against the merged manifest. *(H1)*
9. **Fix the cancellation banner** in `SpotsViewModel` (+ `lookupQrz`), with a test that fails first. *(H4)*
10. **Insets:** `safeDrawing` at `WsprAppRoot.kt:58`, `windowSoftInputMode="adjustResize"`, `.imePadding()` on the two input columns, map ornament margins. *(H5, H6)*
11. **Toolchain:** AGP → 8.13.0, wrapper → 8.13 (regenerate twice with sha256), delete the suppression flag, **add `lintRelease` to CI**, re-verify 16 KB alignment. *(H8)*
12. **Robustness:** DataStore corruption handler + `.catch` + guarded `edit()`; GeoJSON off the main thread; PSKReporter contact address; PSKReporter band-filter bug. *(§4)*
13. **`versionCode` from the git tag**, and delete the `VERSION_CODE:` line from *both* `env:` blocks. *(§4)*
14. **Icon:** `generate.py:194` → `convert('RGBA')`, regenerate, commit the PNG. *(B11)*
15. **Docs pass, one commit:**
    - `DATA_SAFETY.md` → single binding answer set (B7)
    - `PRIVACY_POLICY.md` + `privacy.html` → add **Security**; fix QRZ, RBN, retention, PSKReporter rows; add retention block; bump dates (B8, §4)
    - `PLAY_STORE_LISTING.md` → 77-char short description, unwrapped full description, corrected screenshot spec, tablet guidance (B1, §4)
    - `CONTENT_RATING.md` → add Target audience section (B9)
    - `SIGNING_AND_RELEASE.md` → rewrite steps 3-8, full App content table, fix `:84`/`:86` (B10)
    - New: `docs/FOREGROUND_SERVICE_DECLARATION.md`, `docs/APP_ACCESS.md`, `LICENSE`, `res/raw/third_party_licences.txt`
    - `README.md` → fix `:17-18` clean-room wording, `:29-31` signing claim, `:38` compileSdk, add map-data credit
16. **Full verification before commit:** `./gradlew clean :app:testReleaseUnitTest :app:lintRelease :app:bundleRelease`, then grep the merged manifest and confirm the AAB signer.

### B · You must do in Play Console / externally (CONSOLE/HUMAN)

1. **Decide the app signing key at app creation** — recommendation: accept Google's generated key. *Irreversible; cheap only right now.* *(§4)*
2. **Capture screenshots** on hardware: 5 phone shots (TX un-scrolled so the red disclaimer is in frame), flattened to 24-bit, padded to ≤1.98:1, ≥1080px short side. Plus ≥4 each for the 7" and 10" tablet tabs. *(B1)*
3. **Re-publish GitHub Pages** so `privacy.html` matches the repo, and confirm the URL is live and non-geofenced. *(B8)*
4. **Record and host the FGS demo video** (unlisted YouTube, opens with no sign-in, keep live indefinitely) — *only after fixes A2 and A3 land*, otherwise the notification shot does not exist. *(B3)*
5. **Complete every App content section**, in Google's required order: Ads = No → **App access (restricted, QRZ)** → Privacy policy URL → Content rating → **Target audience (18+ only)** → Data safety (per the corrected `DATA_SAFETY.md`) → News = No → Financial = None → Health = No → Government = No → Advertising ID = No → **Foreground service permissions (mediaPlayback + video)**. *(B3, B7, B9, §4)*
6. **Fill the main store listing** — 77-char short description, unwrapped full description, icon (regenerated), feature graphic, all screenshots. *(B1, B11, §4)*
7. **Delete or annotate the debug-signed APK assets** on GitHub Releases v0.1.0 / v0.1.1 / v0.2.0, and add the sideload-vs-Play signature warning to future release bodies and the README. *(B6, §4)*
8. **Upload the AAB to internal testing**, verify on a device (TX audio, map tiles after the permission strip, hand-typed grid with location denied, QRZ optional path).
9. **Start closed testing immediately** — ≥12 real Google accounts that **accept and install**, held ≥14 *continuous* days. Ham/QRP clubs are the natural pool; ask testers to run a full transmit cycle.
10. **Apply for production access** after day 14 (≤7 days review), then create the production release with a staged rollout.
11. **Post-launch:** watch Android vitals (user-perceived crash rate 1.09% overall / 8% per device model) and check Device catalogue for the supported-device count after the `uses-feature` change.

**Critical path:** items B1–B6 gate the *start* of the 14-day clock. Everything in A can proceed in parallel.

---

## 7. ✅ What already complies

Worth stating plainly, because the substance of this app is in good shape and several of the scariest-sounding findings above resolved *in your favour* on inspection:

- **Transport security is genuinely strong.** `network_security_config.xml:9` sets `cleartextTrafficPermitted="false"` in base-config, so the app **cannot** fall back to plaintext HTTP for any domain. The one cleartext channel (RBN telnet) is protocol-mandated, carries only the constant `N0CALL`, and is honestly disclosed.
- **Secrets at rest are done correctly.** AES-256-GCM with a non-exportable Android Keystore key (`SecretCrypto.kt:69-79`), IV prefixed to ciphertext, the legacy plaintext key actively removed, and the password **discarded rather than persisted** if encryption fails (`SettingsStore.kt:56-67`). Both the DataStore and `wspr.db` are excluded from Auto Backup *and* device-to-device transfer.
- **Location handling is exemplary.** Coarse-only at runtime (`TxScreen.kt:84`, `:154`), behind a rationale dialog, converted to a Maidenhead grid on-device, never transmitted or stored off-device, and the app is fully usable with the permission denied. **Data safety "Location: not collected" is correct** and stays correct.
- **No ads, no analytics, no crash-reporting, no tracking SDKs, no backend.** No `AD_ID`, no `QUERY_ALL_PACKAGES`, no `REQUEST_INSTALL_PACKAGES`, no background location, no exact alarms, no restricted permissions of any kind — so no Permissions Declaration Form is triggered.
- **Manifest surface is minimal and correct:** one exported launcher Activity, the service correctly `android:exported="false"` with a matching type permission and `startForeground` type flag, no orientation/resizability/aspect-ratio locks.
- **`targetSdk 36` is ahead of Play's 31 Aug 2026 deadline**, and **16 KB page-size compliance is already satisfied** (all shipped `.so` files at `0x4000` LOAD alignment) — the one real upload gate in that area.
- **`enableEdgeToEdge()` is already called** (`MainActivity.kt:15`), so the Android 16 edge-to-edge enforcement is a non-event; the inset work in H5/H6 is finishing a job already started.
- **The transmit feature is responsibly gated:** a one-time persisted amateur-licence acknowledgement (`TxScreen.kt:95-115`) plus a permanent high-contrast in-app disclaimer (`TxScreen.kt:123-133`) stating audio-only output and operator responsibility. That banner is stronger evidence for a reviewer than any marketing caption.
- **The WSPR encoder is substantively defensible IP** — every constant is independently published in G4JNT's 2009 protocol description, the polynomials are a 1971 JPL code, and the Kotlin is independently written with a golden-vector test. Only the *comments* need rewording.
- **OSM/OpenFreeMap attribution technically exists today** via MapLibre's default control (uiSettings is never disabled, and the upstream TileJSON carries the credit) — the fix is about making it visible, not creating it.
- **Rate limiting is in place** (`RateLimiter(5 * 60_000L)`), matching PSKReporter's expected polling cadence — no Device-and-Network-Abuse exposure.
- **Signing material is untracked and correctly `.gitignore`d.** The feature graphic already meets its spec exactly (24-bit, no alpha, 1024×500). The hosted privacy policy is live and returns HTTP 200. The content-rating questionnaire answers are prepared and correct.