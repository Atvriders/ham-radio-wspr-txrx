# Privacy Policy — Ham Radio WSPR TX/RX

_Last updated: 2026-08-09_

This privacy policy describes how the **Ham Radio WSPR TX/RX** Android app
("the app", "we") handles your information. The app is a tool for amateur radio
operators to view WSPR (Weak Signal Propagation Reporter) reception reports and to
generate WSPR audio for transmission.

## Summary

- The app contains **no advertising and no analytics/tracking SDKs**, and there is **no
  developer server**: nothing you enter is ever sent to us.
- Your device **location** is used **only on your device** to compute your Maidenhead
  grid locator. It is **never uploaded** by the app.
- Some features send data you enter (callsign, grid, search terms; QRZ credentials)
  to third-party amateur-radio services so they can return results. These are listed
  below.
- Settings (including your QRZ credentials) are stored **only on your device** and are
  excluded from cloud backup and device-to-device transfer.

## Information the app uses

### Location (approximate / coarse)
When you tap the **GPS** button on the Transmit screen, the app requests the
`ACCESS_COARSE_LOCATION` permission and reads your last known approximate location to
fill in your Maidenhead grid square. This computation happens entirely on your device.
The app does **not** upload, store off-device, or share your raw coordinates. You can
decline the permission and type your grid manually. The app does not use background
location and does not request precise location.

### Amateur callsign and grid locator
When you transmit, or when you search for spots, the callsign and/or grid you enter are
used to build the on-air WSPR message and/or the queries sent to the data services
below. Your most recent searched callsigns are stored on your device to populate the
search box.

### QRZ.com credentials (optional)
If you choose to enter QRZ.com credentials in Settings to look up station details, your
QRZ **username and password** are stored on your device and are sent to QRZ.com
(`xmldata.qrz.com`) over HTTPS to authenticate and perform callsign lookups. If you do
not enter QRZ credentials, **the app makes no QRZ.com API requests**. (Separately, the
spot detail view offers a **QRZ.com** button that opens `qrz.com` in your browser for the
callsign you are viewing — see the table below. That works without credentials, and once
your browser opens the page, QRZ.com's own privacy policy applies.)

> Note: the QRZ password is encrypted at rest on your device using the Android Keystore
> and is excluded from backups. It is sent to QRZ.com over HTTPS only to authenticate
> your lookups.

## Third parties the app sends data to

The app contacts these services only to provide the feature you are using. Each has its
own privacy policy:

| Service | What is sent | When |
|---|---|---|
| **wspr.live** (`db1.wspr.live`) | Search parameters (callsign, grid, band, time range, distance/power limits) | When you search/refresh spots (default source) |
| **PSKReporter** (`retrieve.pskreporter.info`) | A time window, a "reception reports only" flag, the developer's fixed contact address (required by PSKReporter's operator policy — it is **not** your address), and the callsign you searched for | When PSKReporter is enabled as a source |
| **Reverse Beacon Network** (`telnet.reversebeacon.net`) | The fixed placeholder login callsign `N0CALL`, over a plain (cleartext) telnet connection | When RBN is enabled as a source |
| **QRZ.com** (`xmldata.qrz.com`) | Your QRZ username + password; the callsign you look up | When you have entered QRZ credentials and view station details |
| **QRZ.com** (`www.qrz.com`, in your browser) | The callsign you are viewing, as part of the URL | Only when you tap the **QRZ.com** button on a spot detail |
| **OpenFreeMap** (`tiles.openfreemap.org`) | Standard map tile/style requests (includes your IP, as with any web request) | When you open the Map screen |

The Reverse Beacon Network connection is **cleartext (unencrypted) telnet** — that is how
the protocol works. The app sends the constant `N0CALL` as the login and **never** your
own callsign, credentials or location over it. There is no setting that changes this.

## Data stored on your device

- App settings: enabled data sources, QRZ username/password (password encrypted),
  default time range, units, theme, recent searched callsigns, band colour overrides
  (in a private DataStore).
- A local cache of recently fetched spots (a private Room database), pruned
  automatically after 7 days.

This on-device data is excluded from Android cloud Auto Backup and device-to-device
transfer. Uninstalling the app removes all of it. You can also clear it via the system
Settings → Apps → Storage → Clear storage.

## Security

- **In transit** — all web requests use HTTPS/TLS, and cleartext HTTP is disabled for
  every domain at the operating-system level, so the app cannot silently fall back to an
  unencrypted connection. The single exception is the optional Reverse Beacon Network
  telnet feed, which is unencrypted by protocol design and carries only the fixed
  placeholder callsign `N0CALL` — never your callsign, credentials or location.
- **At rest** — your QRZ password is encrypted with AES-256-GCM using a non-exportable
  key held in the Android Keystore, with a fresh initialisation vector per encryption. If
  encryption is unavailable for any reason, the password is **discarded rather than
  written in plaintext**.
- **Backups and transfers** — the settings store and the spot cache are excluded from
  Android Auto Backup and from device-to-device transfer, so neither leaves your device.
- **No servers, no third-party SDKs** — the app has no backend of its own, and ships no
  advertising, analytics, crash-reporting or tracking SDKs. The developer never receives
  your data.
- **Location** — read only when you tap GPS, converted to a Maidenhead grid on-device,
  and never transmitted or stored off-device.
- **Least privilege** — the app requests only internet, network state, coarse location,
  notification, foreground-service and wake-lock permissions. Precise location and Wi-Fi
  state are explicitly removed from the shipped app, and a build-time check fails the
  release if either ever reappears.

## Data retention

- **Cached spots** are pruned automatically after **7 days**.
- **Settings, recent callsigns (the 20 most recent) and QRZ credentials** are retained
  for as long as the app is installed — there is no automatic expiry. Clear them at any
  time as described under "Data deletion".
- **The developer holds no copies of anything**, because there is no developer server.
- **Third parties** (wspr.live, PSKReporter, the Reverse Beacon Network, QRZ.com,
  OpenFreeMap) retain data under **their own** policies; see their websites.

## Data deletion

- **On-device data:** clear all stored data at any time by uninstalling the app, or via
  Android Settings → Apps → Ham Radio WSPR TX/RX → Storage → Clear storage. To remove
  only QRZ credentials, clear the username/password fields in the app's Settings.
- **Data held by third parties:** to delete data associated with your QRZ.com account,
  contact QRZ.com. Spot/reception data on wspr.live, PSKReporter, and the Reverse
  Beacon Network is operated by those projects under their own policies.

To request help with data deletion, contact us at the email below.

## Children

The app is not directed to children and does not knowingly collect data from children.

## Changes

We may update this policy; the "last updated" date will change accordingly.

## Contact

Questions about this policy: **klassenjames0@gmail.com**
