# Ham Radio WSPR TX/RX

A native Android app for amateur-radio operators working with **WSPR** (Weak Signal
Propagation Reporter) data:

- **RX** — fetch and visualize reception "spots" from **wspr.live**, **PSKReporter**,
  and the **Reverse Beacon Network**: a sortable spot table, an interactive **globe/map**
  with great-circle paths and a grey-line (terminator) overlay, time/SNR charts, and a
  **Head2Head** receiver comparison.
- **TX** — encode a *real* WSPR message (callsign + grid + power) to **audio** and play it,
  time-synced to the even UTC minute, for **acoustic coupling / VOX** into an SSB
  transceiver. (A phone cannot emit RF; you supply the radio and the licence.)

The UI is **adaptive**: it reflows across phones, tablets, and folding phones —
bottom bar → navigation rail → drawer, with two-pane list/detail on wide screens.

The WSPR encoder is an independent implementation written from the published protocol
description — Andy Talbot G4JNT's *"The WSPR Coding Process"* (2009) — from which every
constant (both generator polynomials, the 162-bit sync vector, the bit-reversal
interleave, and the source-coding formulas) is taken; the convolutional polynomials
themselves are a 1971 JPL code. WSJT-X is used only as a **test oracle** for the
golden-vector unit test. No code or assets from any other app are included.

Map data © OpenStreetMap contributors, available under the ODbL. Tiles by
[OpenFreeMap](https://openfreemap.org/), built with
[OpenMapTiles](https://openmaptiles.org/).

## Download the APK

Every push to `master` builds an installable APK on GitHub Actions:

1. Open the **Actions** tab → latest **Build APK** run → **Artifacts** →
   `ham-radio-wspr-txrx-apk`.
2. Or download from the **Releases** page (published on `v*` tags).
3. Sideload it (enable "install unknown apps" on your device).

The release APK is signed with the project's **upload key** whenever the four signing
secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) are present
in the repo, which they are; a fork without them gets a debug-signed APK instead, and no
`.aab` at all (the build refuses to produce a debug-signed Play bundle).

> ⚠️ **Sideload APK ≠ Play install.** Under Play App Signing, Google re-signs the app with
> a different key, so a GitHub-downloaded APK and a Play install can never update each
> other (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Moving from one to the other requires an
> uninstall, which clears local settings, the spot cache and stored QRZ credentials.
>
> Historical note: the APKs attached to releases **v0.1.0, v0.1.1 and v0.2.0 are
> debug-signed**, each with a different ephemeral CI key. They cannot upgrade to each
> other or to anything newer — uninstall first.

## Build locally

```bash
./gradlew assembleRelease    # APK at app/build/outputs/apk/release/
./gradlew test               # JVM unit tests (core math + WSPR encoder)
```

Requires JDK 17 and the Android SDK (compileSdk 36 / targetSdk 36, minSdk 26).

## Tech

Kotlin · Jetpack Compose (Material 3, adaptive) · MapLibre · OkHttp ·
kotlinx.serialization · Room · DataStore.

## Licence

MIT — see [`LICENSE`](LICENSE). Third-party notices for the redistributed open-source
components (MapLibre, OkHttp, AndroidX/Compose, kotlinx, and the OSM/OpenFreeMap map-data
credits) ship inside the app under **Settings → About & legal**, and in
[`app/src/main/res/raw/third_party_licences.txt`](app/src/main/res/raw/third_party_licences.txt).

## Disclaimer

TX produces audio only. You are responsible for holding a valid amateur licence and
for all RF you transmit. Not affiliated with WSPRnet, PSKReporter, the Reverse Beacon
Network, or WSJT-X.
