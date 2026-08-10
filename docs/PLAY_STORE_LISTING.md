# Google Play — Store listing (ready to paste)

Fill these into **Play Console → Grow → Store presence → Main store listing**.
Character limits are Google's; the counts below were measured, not estimated.

---

## App name (max 30)
```
Ham Radio WSPR TX/RX
```

## Short description (max 80 — hard cap, the Console will not save a longer one)
```
See who hears you on WSPR: live spots, globe map, charts, audio WSPR encoder.
```
*(77 characters.)* "Audio WSPR encoder" rather than "a transmitter" is deliberate: the
app produces sound, not RF, and describing it as a transmitter invites a
misrepresented-functionality question. Do not substitute a variant that says
"transmit WSPR" without qualification.

## Full description (max 4000 — currently ~2,250)

**Do not re-wrap this block.** Play preserves newlines verbatim, so hard-wrapped bullets
render as detached orphan fragments. One physical line per paragraph or bullet. Keep the
en-GB spelling — it matches the in-app strings.

```
Ham Radio WSPR TX/RX is a propagation toolkit for licensed amateur radio operators who use WSPR (Weak Signal Propagation Reporter). See where your signal is being heard, study band conditions, compare receivers, and generate a WSPR transmission — all from your phone or tablet.

RECEIVE — see who's hearing you
• Live reception "spots" from wspr.live, PSKReporter, and the Reverse Beacon Network, merged into one view.
• A sortable spot table (time, SNR, distance, band) with a tap-through detail card and QRZ.com callsign lookup.
• An interactive globe map: transmitters, receivers, and great-circle paths, colour-coded by band, with a day/night grey-line overlay. Tap any station for its details.
• Filter by band, time window, distance, power, and direction; search by callsign or grid.

ANALYSE — read the bands
• Spots-over-time and SNR-over-time charts to spot openings and pick the best window.
• Head2Head: compare two receivers on the exact transmissions they both heard — a clean A/B for antenna and receiver testing, with no averaging.

TRANSMIT — a real WSPR encoder
• Encodes a proper WSPR message (callsign + grid + power) to audio and plays it, time-synced to the even UTC minute, for acoustic coupling / VOX into your SSB transceiver.
• Auto-fills your Maidenhead grid from your location (computed on your device only).
• An ongoing notification shows the transmission and lets you stop it early.

BUILT FOR EVERY SCREEN
• Adapts to phones, tablets, and folding phones — bottom bar, navigation rail, or drawer, with a two-pane list/detail view on larger screens.
• Light and dark themes; editable per-band colours; metric or imperial distances.

PRIVACY
• No ads. No analytics or tracking SDKs. No developer server.
• Your location is used only on your device to compute your grid; it is never uploaded.
• Settings, including optional QRZ credentials, are stored on your device (encrypted) and excluded from cloud backup.

IMPORTANT — licensing and transmitting
This app produces AUDIO only; it does not emit radio frequency energy. Transmitting on the air requires your own transceiver and a valid amateur radio licence. You are solely responsible for complying with the regulations of your licensing authority. Not affiliated with WSPRnet, PSKReporter, the Reverse Beacon Network, QRZ.com, or WSJT-X.

Map data © OpenStreetMap contributors, ODbL. Map tiles by OpenFreeMap, built with OpenMapTiles.

73!
```

---

## Categorisation
- **App category:** Tools *(alternative: Communication)*
- **Tags:** amateur radio, ham radio, WSPR, propagation, ham radio tools

## Contact details
- **Email:** klassenjames0@gmail.com
- **Website (optional):** `https://github.com/Atvriders/ham-radio-wspr-txrx`
- **Phone (optional):** leave blank

## Privacy policy URL
`https://atvriders.github.io/ham-radio-wspr-txrx/privacy.html`

This exact string must also appear in the app (`R.string.privacy_policy_url`) and in
`docs/DATA_SAFETY.md`. A mismatch between the three is itself a rejection trigger.

## Graphics (in docs/store-assets/)
- **App icon (512×512):** `play-icon-512.png` — 32-bit PNG **with** an alpha channel
  (colour type 6), fully opaque, ≤1024 KB. Regenerate with
  `python3 docs/store-assets/generate.py`, which asserts the spec.
- **Feature graphic (1024×500):** `feature-graphic-1024x500.png` — 24-bit PNG, **no**
  alpha. Deliberately a different spec from the icon; do not "fix" it to RGBA.
- **Phone screenshots (REQUIRED, 2–8):** not in the repo — they must be captured on a
  device or emulator. See below.
- **Tablet screenshots (7" and 10" tabs):** optional for publishing, but see the note.

### Screenshots — the spec traps that get uploads refused

1. **Format: JPEG or 24-bit PNG, no alpha.** `adb exec-out screencap -p` emits 32-bit
   RGBA and will be refused. Flatten every capture:
   `python3 -c "from PIL import Image;import sys;Image.open(sys.argv[1]).convert('RGB').save(sys.argv[1])" shot.png`
2. **Max dimension ≤ 2× min dimension.** A stock Pixel 6/7/8 capture is 1080×2400 =
   2.22:1 and **is rejected**. Use a 1080×1920 AVD, or pad/centre-crop to ≤1.98:1.
3. **Short side ≥ 1080 px and ≥ 4 shots** to stay eligible for large-format
   recommendation surfaces. (The publish *minimum* is 320 px and 2 shots; the 1080/4
   figures are for eligibility.) For the 7"/10" tablet tabs the accepted range is
   1,080–7,680 px, not the phone spec's 320 px.
4. **Device frames are allowed** on phone and tablet listings. The "no device frames"
   rule applies to Wear OS only.

**Suggested set (5 phone shots):** Spots list (populated), Globe map with a station
tapped, Charts + Head2Head, **TX screen un-scrolled** so the red in-app licensing
disclaimer is in frame — that banner is stronger evidence for a reviewer than any
marketing caption — and Settings showing the About & legal section.

**Tablets/foldables:** the full description advertises adaptive layouts, so without ≥4
shots at ≥1080 px under the 7" and 10" tabs the app is excluded from tablet and
Chromebook recommendation surfaces — exactly the audience that feature was built for.
There is no separate foldable tab; file unfolded shots under tablet.

## Attribution to keep in the listing

The map is OpenStreetMap data served by OpenFreeMap. The ODbL requires visible credit, so
the line at the end of the full description above is not optional, and the same credit
appears in-app on the map and in Settings → About & legal.
