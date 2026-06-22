# Google Play Data Safety form — declaration checklist

_DRAFT for Ham Radio WSPR TX/RX. Use this to fill in Play Console → App content →
Data safety. Answers must stay consistent with `docs/PRIVACY_POLICY.md`._

## Overview answers

- **Does your app collect or share any of the required user data types?** **Yes.**
- **Is all of the user data collected by your app encrypted in transit?** **Yes** — all
  HTTP endpoints (wspr.live, PSKReporter, QRZ.com, map tiles) use HTTPS.
  - Caveat: the optional **Reverse Beacon Network** feature uses a **cleartext telnet**
    connection. By default the app sends only a placeholder login callsign over it (no
    personal data). Disclose RBN's cleartext nature in the privacy policy; if you ever
    send the user's real callsign over RBN, you must revisit the "encrypted in transit"
    answer.
- **Do you provide a way for users to request that their data be deleted?** **Yes** —
  on-device clear-storage / uninstall, and clearing credential fields in-app; document
  the data-deletion route (see privacy policy "Data deletion" section).

## Data types to declare

### Location
- **Approximate location** — **Collected: Yes. Shared: No.**
  - Purpose: **App functionality** (compute the user's Maidenhead grid on-device).
  - Processed **ephemerally** / on-device; not sent off device by the app.
  - Optional (user taps GPS; can be declined).
  - Do **NOT** declare Precise location — the app requests only `ACCESS_COARSE_LOCATION`.

### Personal info
- **Other personal info → Amateur radio callsign (identifier)** — **Collected: Yes.
  Shared: Yes.**
  - Purpose: **App functionality** (used in WSPR transmissions and in spot queries).
  - Shared with: wspr.live, PSKReporter (and RBN if enabled) to return results.
  - Stored on device (recent searched callsigns).

### Account credentials (QRZ.com) — optional feature
- **Other personal info / "User account credentials"** — **Collected: Yes (optional).
  Shared: Yes (with QRZ.com).**
  - QRZ **username and password** are collected when the user enters them.
  - Purpose: **App functionality** (authenticate to QRZ.com for callsign lookups).
  - Sent to a **third party (QRZ.com)** over HTTPS.
  - Stored on the device (excluded from backup/transfer). A future version will
    Keystore-encrypt them.

### App activity (search terms)
- **App activity → Search history** — **Collected: Yes. Shared: Yes.**
  - The callsign/grid/band/time-range the user searches are sent to wspr.live /
    PSKReporter to return spots; recent searches are stored on device.
  - Purpose: **App functionality.**

## Data the app does NOT collect (declare "No")

- No precise location.
- No financial info, health, messages, photos/videos, audio recordings, contacts,
  calendar.
- No advertising or analytics identifiers; **no third-party ads, no analytics SDKs.**
- No device or other identifiers collected for tracking.

## Security practices to declare

- **Data is encrypted in transit:** Yes (with the RBN cleartext caveat above).
- **Users can request data deletion:** Yes.
- **Committed to Play Families policy:** app is not directed at children.

## Reviewer notes (not part of the form)

- Third parties named in both the form and the privacy policy: **QRZ.com, wspr.live,
  PSKReporter, Reverse Beacon Network** (and OpenFreeMap for map tiles — standard
  network requests; declare per your judgment, typically not a "data type" collection).
- Keep the **privacy policy URL** entered in Play Console consistent with this form.
- If you later add Crashlytics / Play Vitals-style crash reporting, re-open this form to
  declare diagnostic data.
