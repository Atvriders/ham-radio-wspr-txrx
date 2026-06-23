# Google Play — Store listing (ready to paste)

Fill these into **Play Console → Grow → Store presence → Main store listing**.
Character limits are Google's; everything below is within them.

---

## App name (max 30)
```
Ham Radio WSPR TX/RX
```

## Short description (max 80)
```
See who's hearing you on WSPR: live spots, a globe map, charts, and a transmitter.
```
*(80 chars. Alternate: "Track WSPR propagation worldwide and transmit WSPR — maps, charts, Head2Head.")*

## Full description (max 4000)
```
Ham Radio WSPR TX/RX is a propagation toolkit for licensed amateur radio operators who
use WSPR (Weak Signal Propagation Reporter). See where your signal is being heard, study
band conditions, compare receivers, and even transmit a WSPR message — all from your phone
or tablet.

RECEIVE — see who's hearing you
• Live reception "spots" from wspr.live, PSKReporter, and the Reverse Beacon Network,
  merged into one view.
• A sortable spot table (time, SNR, distance, band) with a tap-through detail card and
  QRZ.com callsign lookup.
• An interactive globe map: transmitters, receivers, and great-circle paths, colour-coded
  by band, with a day/night grey-line overlay. Tap any station for its details.
• Filter by band, time window, distance, power, and direction; search by callsign or grid.

ANALYSE — read the bands
• Spots-over-time and SNR-over-time charts to spot openings and pick the best window.
• Head2Head: compare two receivers on the exact transmissions they both heard — a clean
  A/B for antenna and receiver testing, with no averaging.

TRANSMIT — a real WSPR encoder
• Encodes a proper WSPR message (callsign + grid + power) to audio and plays it, time-synced
  to the even UTC minute, for acoustic coupling / VOX into your SSB transceiver.
• Auto-fills your Maidenhead grid from your location (computed on your device only).

BUILT FOR EVERY SCREEN
• Adapts to phones, tablets, and folding phones — bottom bar, navigation rail, or drawer,
  with a two-pane list/detail view on larger screens.
• Light and dark themes; editable per-band colours; metric or imperial distances.

PRIVACY
• No ads. No analytics or tracking SDKs.
• Your location is used only on your device to compute your grid; it is never uploaded.
• Settings, including optional QRZ credentials, are stored on your device (encrypted) and
  excluded from cloud backup.

IMPORTANT — licensing and transmitting
This app produces AUDIO only; it does not emit radio frequency energy. Transmitting on
the air requires your own transceiver and a valid amateur radio licence. You are solely
responsible for complying with the regulations of your licensing authority. Not affiliated
with WSPRnet, PSKReporter, the Reverse Beacon Network, QRZ.com, or WSJT-X.

73!
```

---

## Categorisation
- **App category:** Tools *(alternative: Communication)*
- **Tags:** amateur radio, ham radio, WSPR, propagation, ham radio tools

## Contact details
- **Email:** klassenjames0@gmail.com  *(change if you want a public support address)*
- **Website (optional):** your GitHub Pages URL or repo
- **Phone (optional):** leave blank

## Privacy policy URL
- Paste the GitHub Pages URL once it is live (see docs/SIGNING_AND_RELEASE.md and the
  Pages setup): `https://atvriders.github.io/ham-radio-wspr-txrx/privacy.html`

## Graphics (in docs/store-assets/)
- **App icon (512×512):** `play-icon-512.png`
- **Feature graphic (1024×500):** `feature-graphic-1024x500.png`
- **Phone screenshots (REQUIRED, 2–8):** you must capture these on a device/emulator —
  see the "Screenshots" note below. Suggested set: Spots list, Globe map with a tapped
  station, Charts + Head2Head, TX screen, Settings.

### Screenshots — how to get them (the one thing CI can't make)
1. Install the APK on a phone (or an Android Studio emulator, Pixel 6 / API 35).
2. Open each screen; use the device screenshot (Power+VolDown) or the emulator camera.
3. Crop to the device frame; Play accepts PNG/JPEG, 16:9 or 9:16, min 320 px, max 3840 px.
4. For tablet listings (optional), repeat on a tablet/foldable emulator.
