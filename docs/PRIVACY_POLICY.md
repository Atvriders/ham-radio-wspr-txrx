# Privacy Policy — Ham Radio WSPR TX/RX

_Last updated: 2026-06-22 (DRAFT — review and host at a public URL before Play submission)_

This privacy policy describes how the **Ham Radio WSPR TX/RX** Android app
("the app", "we") handles your information. The app is a tool for amateur radio
operators to view WSPR (Weak Signal Propagation Reporter) reception reports and to
transmit WSPR signals.

## Summary

- The app contains **no advertising and no analytics/tracking SDKs**.
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
location.

### Amateur callsign and grid locator
When you transmit, or when you search for spots, the callsign and/or grid you enter are
used to build the on-air WSPR message and/or the queries sent to the data services
below. Your most recent searched callsigns are stored on your device to populate the
search box.

### QRZ.com credentials (optional)
If you choose to enter QRZ.com credentials in Settings to look up station details, your
QRZ **username and password** are stored on your device and are sent to QRZ.com
(`xmldata.qrz.com`) over HTTPS to authenticate and perform callsign lookups. If you do
not enter QRZ credentials, no data is sent to QRZ.com.

> Note: in the current version the QRZ password is stored in the app's private
> on-device settings in plaintext (excluded from backups). A future version will move
> it to the Android Keystore. If this concerns you, do not enter QRZ credentials.

## Third parties the app sends data to

The app contacts these services only to provide the feature you are using. Each has its
own privacy policy:

| Service | What is sent | When |
|---|---|---|
| **wspr.live** (`db1.wspr.live`) | Search parameters (callsign, grid, band, time range) | When you search/refresh spots (default source) |
| **PSKReporter** (`pskreporter.info`) | Search parameters (callsign, grid, band) | When PSKReporter is enabled as a source |
| **Reverse Beacon Network** (`telnet.reversebeacon.net`) | A login callsign over a plain (cleartext) telnet connection | When RBN is enabled as a source |
| **QRZ.com** (`xmldata.qrz.com`) | Your QRZ username + password; the callsign you look up | When you have entered QRZ credentials and view station details |
| **OpenFreeMap** (`tiles.openfreemap.org`) | Standard map tile/style requests (includes your IP, as with any web request) | When you open the Map screen |

The Reverse Beacon Network connection is **cleartext (unencrypted) telnet**. By default
the app uses a placeholder login callsign and does not send your personal callsign over
this connection.

## Data stored on your device

- App settings: enabled data sources, QRZ username/password, default time range,
  units, theme, recent searched callsigns, band color overrides (in a private
  DataStore).
- A local cache of recently fetched spots (a private Room database), pruned
  automatically after 7 days.

This on-device data is excluded from Android cloud Auto Backup and device-to-device
transfer. Uninstalling the app removes all of it. You can also clear it via the system
Settings → Apps → Storage → Clear storage.

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
