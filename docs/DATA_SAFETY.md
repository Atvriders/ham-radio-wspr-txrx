# Google Play Data Safety form — declaration checklist

_For Ham Radio WSPR TX/RX. Use this to fill in Play Console → App content →
Data safety. Answers must stay consistent with `docs/PRIVACY_POLICY.md`._

## Importing via CSV (faster than clicking through the form)

The CSV cannot be written from scratch — Google generates it with internal Question IDs
specific to the current form version. Do this:

1. On the **Data safety** page, click **Export to CSV** (downloads the template: one row per
   answer choice, with a `Response value` column and an `Answer requirement` column —
   `REQUIRED` / `OPTIONAL` / `MULTIPLE_CHOICE`).
2. Fill the **`Response value`** column with `TRUE` / `FALSE` (leave blank where allowed),
   using the answer key below.
3. **Import** the file back (this **overwrites** the current form answers).

Paste the exported CSV here and I'll return it fully filled.

### Answer key (what each question resolves to)
- **Does your app collect or share any of the required user data types?** → **Yes**
- **All collected data encrypted in transit?** → **Yes**
- **Provide a way to request data deletion?** → **Yes**
- **Location (Approximate / Precise)** → **No** (used on-device only; never transmitted)
- **Personal info → User IDs** (amateur callsign; QRZ username): **Collected = Yes**,
  purpose **App functionality**, **Optional**, stored on device. (QRZ username only if the
  user configures QRZ.)
- **App activity → Search history** (callsign/grid/band searched, sent to wspr.live/PSK):
  **Collected = Yes**, purpose **App functionality**.
- **Financial, Health, Messages, Photos/Videos, Audio, Files, Calendar, Contacts, Web
  history, Device/other IDs** → **No** to all.
- **Ads / analytics identifiers** → **No** (no ads, no analytics SDKs).

> On "Shared": transfers to wspr.live/PSKReporter/QRZ happen only as a **user-initiated**
> query for the feature the user requested. If the form asks whether each type is *shared*,
> you may answer **No** under the user-initiated-transfer basis, **or** answer **Yes** and
> name wspr.live/PSKReporter/QRZ to be conservative — either is defensible; just keep it
> consistent with the privacy policy.

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

### Location — declare NOT collected
- **Approximate location** — **Collected: No. Shared: No.**
  - Google defines "collection" as data **transmitted off the device**. The app reads
    coarse location **only on-device** to compute the Maidenhead grid and **never
    transmits it**, so under the Data Safety rules location is **not collected**.
  - Do **NOT** declare Precise or Approximate location collection. (You still request the
    `ACCESS_COARSE_LOCATION` runtime permission — that is a permission, not a Data Safety
    collection.)

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
  - Stored on the device, **Keystore-encrypted at rest** and excluded from backup/transfer.

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
