# Signing & release — getting a Play-uploadable .aab

> ⏱️ **Read this first: the critical path to Production is about three weeks.** A personal
> developer account created after 13 November 2023 cannot publish to Production until it
> has run a **closed** test with **≥12 testers opted in continuously for 14 days** and
> then been granted production access (up to 7 more days of review). Internal testing does
> **not** count towards the 14 days. Plan the tester recruitment before you plan the code
> freeze.

> ✅ **Signing is already set up.** An upload keystore (`upload-keystore.p12`, PKCS12) was
> generated and the four signing secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
> `KEY_ALIAS=upload`, `KEY_PASSWORD`) are configured in the repo, so tagged builds are
> signed automatically. **Back up `upload-keystore.p12` and `SIGNING-CREDENTIALS.txt`**
> (in your repo folder, gitignored — they are NOT in git) to a password manager plus an
> offline copy. The manual steps in §1–§2 are only needed if you ever rotate the key.

---

## 0. What the build guarantees

Three independent guards make a debug-signed Play upload impossible:

1. **Gradle** refuses to run `bundleRelease` / `packageReleaseBundle` at all when
   `KEYSTORE_FILE` is unset. `assembleRelease` deliberately still works and falls back to
   debug signing, because a sideload APK must be installable without secrets.
2. **CI** fails fast if any of the four signing secrets is missing on a tag build (a
   present keystore with an empty `KEY_ALIAS` is a different, equally silent failure).
3. **CI** runs `scripts/check-release-signer.sh` against the built AAB: it rejects
   `CN=Android Debug` and pins the upload certificate's SHA-256 to
   `cec75e15…3bb4`, so rotation to the *wrong* key is caught too.

A fourth guard, `scripts/check-merged-manifest.sh`, fails the build if a denylisted
permission (precise location, Wi-Fi state, …) ever reappears in the merged manifest.

`versionCode` is derived from the tag (`v1.2.3` → `1002003`), not from the CI run number,
which was unusable: re-running a workflow reuses the run number, and renaming the workflow
file resets it to 1.

---

## 1. Create your upload keystore (one time, on your own machine)

Run this where you have a JDK (`keytool` ships with the JDK; **not** in this sandbox).

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.p12 \
  -storetype PKCS12 \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload \
  -dname "CN=Atvriders, O=Atvriders, C=US"
```

> Why an upload key + Play App Signing: you sign uploads with **your upload key**; Google
> re-signs with the **app signing key it manages**. If you ever lose the upload key,
> Google can reset it — you never lose your app.

## 2. Add four repository secrets

```bash
base64 -w0 upload-keystore.p12 > keystore.b64    # macOS: base64 -i … -o keystore.b64
```

In **GitHub → repo → Settings → Secrets and variables → Actions**, add:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | the entire contents of `keystore.b64` |
| `KEYSTORE_PASSWORD` | your keystore password |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | your key password |

Delete `keystore.b64` afterwards. If you rotate the key, also update the pinned
fingerprint in `scripts/check-release-signer.sh`.

## 3. Cut a build

```bash
git tag -a v1.0.2 -m "Ham Radio WSPR TX/RX v1.0.2"
git push origin v1.0.2
```

The **publish** job then verifies the secrets, derives `versionName`/`versionCode` from
the tag, builds and signs `app-release.aab`, asserts the signer, checks the merged
manifest, and uploads the AAB as the `ham-radio-wspr-txrx-play-aab` artifact (plus the
sideload APK on a GitHub Release).

Download the `.aab` from the run's **Artifacts**.

---

## 4. Release steps in Play Console

### Step 1 — Create the app, and decide the signing key *now*
**Create app** → "Ham Radio WSPR TX/RX", default language, App, Free, accept policies.
Then **Release → Setup → App signing**: **keep the Google-generated app signing key**
(the default). This choice is irreversible and is only cheap at app creation: a
self-provided key cannot be reset if it leaks, and yours lives in a GitHub Actions secret.

⚠️ **Consequence to accept and communicate:** under Play App Signing, Google re-signs with
a different key, so the GitHub sideload APK and the Play install can never update each
other (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Anyone moving from a sideload to Play must
uninstall first, losing their local settings, spot cache and QRZ credentials. Say so in
the Release body and the README. Do **not** "fix" this with
`applicationIdSuffix = ".sideload"` — that trades a one-time uninstall for a permanent
fork.

### Step 2 — Internal testing (a smoke test; it does **not** count towards the 14 days)
Release → Testing → **Internal testing** → create release → upload the `.aab` → add your
own account. Verify on a real device: TX audio and the ongoing notification, map tiles
after the permission strip, a hand-typed grid with location denied, and the QRZ-optional
path.

### Step 3 — Complete App content (every section, in Google's required order)

| # | Section | Answer / source |
|---|---|---|
| 1 | Ads | **No** |
| 2 | App access | **Restricted** — `docs/APP_ACCESS.md` |
| 3 | Privacy policy URL | `https://atvriders.github.io/ham-radio-wspr-txrx/privacy.html` |
| 4 | Content rating | questionnaire in `docs/CONTENT_RATING.md` → expect Everyone / PEGI 3 |
| 5 | Target audience and content | **18 and over only** — `docs/CONTENT_RATING.md` |
| 6 | Data safety | the binding answer set in `docs/DATA_SAFETY.md` |
| 7 | News app | **No** |
| 8 | Financial features | **None of these** |
| 9 | Health apps | **No** |
| 10 | Government apps | **No** |
| 11 | Advertising ID | **No** — the app declares no `AD_ID` permission |
| 12 | Foreground service permissions | `mediaPlayback` + demo video — `docs/FOREGROUND_SERVICE_DECLARATION.md` |

App content cannot be marked complete with any required section outstanding, and no
track — including closed testing — can be rolled out until it is.

### Step 4 — Record and host the foreground-service demo video
Follow `docs/FOREGROUND_SERVICE_DECLARATION.md` §4. Unlisted YouTube, opens with no
sign-in, kept live indefinitely.

### Step 5 — Fill the main store listing
`docs/PLAY_STORE_LISTING.md` — the 77-character short description, the unwrapped full
description, the icon and feature graphic from `docs/store-assets/`, and the screenshots.
On a personal account the **main store listing must be complete before a closed-testing
rollout**, so this gates the start of the 14-day clock.

### Step 6 — Closed testing: ≥12 testers, 14 continuous days
Release → Testing → **Closed testing** → create a track and a tester list of **at least
12 Google accounts**.

- Testers must **accept the invitation and install** — invited-but-not-installed does not
  count.
- The 14 days must be **continuous**: if a tester opts out and rejoins, their continuity
  resets and the clock effectively restarts for that slot. Recruit a couple of spares.
- Ham and QRP clubs are the natural pool. Ask each tester to run at least one full
  transmit cycle so the foreground service is genuinely exercised.

### Step 7 — Apply for production access
Available from day 14. Review takes up to 7 days. Expect questions about tester
engagement, so keep notes on what testers reported.

### Step 8 — Production
Create the production release with a **staged rollout**, then watch Android vitals
(user-perceived crash rate thresholds: 1.09% overall, 8% per device model) and the Device
catalogue count after the `uses-feature required="false"` change.

---

## Notes

- The sideload APK on GitHub Releases **is signed with the upload key** whenever the
  secrets are present — it is not debug-signed. (Historically v0.1.0, v0.1.1 and v0.2.0
  *were* debug-signed, each with a different ephemeral runner key; those assets should be
  deleted or annotated, and anyone holding one must uninstall before installing anything
  newer.)
- Target API level is **36** (Android 16), ahead of Play's 31 August 2026 deadline. Keep
  `compileSdk`/`targetSdk` current as Google raises the bar each year.
- 16 KB page-size compliance is satisfied (all shipped `.so` files use `0x4000` LOAD
  alignment). That one *is* a hard upload gate — re-check it after any AGP or MapLibre
  bump.
- The Room schema JSON (`app/schemas/…`) is committed, so future DB migrations have a
  baseline.
