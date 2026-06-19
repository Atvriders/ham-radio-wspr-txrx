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

This is an original, clean-room implementation. The WSPR protocol is an open,
published specification (K1JT / WSJT-X). No code or assets from any other app are used.

## Download the APK

Every push to `master` builds an installable APK on GitHub Actions:

1. Open the **Actions** tab → latest **Build APK** run → **Artifacts** →
   `ham-radio-wspr-txrx-apk`.
2. Or download from the **Releases** page (published on `v*` tags).
3. Sideload it (enable "install unknown apps" on your device).

By default the APK is **debug-signed** so it installs without any secrets. To get a
stable upgrade signature, add repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`; CI will then sign a release build.

## Build locally

```bash
./gradlew assembleRelease    # APK at app/build/outputs/apk/release/
./gradlew test               # JVM unit tests (core math + WSPR encoder)
```

Requires JDK 17 and the Android SDK (compileSdk 35).

## Tech

Kotlin · Jetpack Compose (Material 3, adaptive) · MapLibre · OkHttp ·
kotlinx.serialization · Room · DataStore.

## Disclaimer

TX produces audio only. You are responsible for holding a valid amateur licence and
for all RF you transmit. Not affiliated with WSPRnet, PSKReporter, the Reverse Beacon
Network, or WSJT-X.
