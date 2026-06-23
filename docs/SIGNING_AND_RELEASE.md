# Signing & release — getting a Play-uploadable .aab

> ✅ **Already set up.** An upload keystore (`upload-keystore.p12`, PKCS12) was generated and
> the four signing secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS=upload`,
> `KEY_PASSWORD`) are configured in the repo, so tagged builds are signed automatically.
> **Back up `upload-keystore.p12` and `SIGNING-CREDENTIALS.txt`** (in your repo folder,
> gitignored — they are NOT in git) to a password manager + offline copy. With Play App
> Signing enabled, a lost *upload* key is recoverable via Google, but keep the backup anyway.
> The manual steps below are only needed if you ever rotate the key.

The CI already builds a **signed `.aab`** (`bundleRelease`) whenever the four signing
secrets are present, and falls back to a debug-signed build when they are not. To produce
a release you can upload to Google Play, do the one-time keystore setup below, then cut a
version tag.

> Why an upload key + Play App Signing: you sign uploads with **your upload key**; Google
> re-signs with the **app signing key it manages**. If you ever lose the upload key, Google
> can reset it — you never lose your app.

## 1. Create your upload keystore (one time, on your own machine)

Run this where you have a JDK (`keytool` ships with the JDK; **not** in this sandbox). Keep
the file and passwords safe — back them up offline.

```bash
keytool -genkeypair -v \
  -keystore upload-keystore.jks \
  -storetype JKS \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload \
  -dname "CN=Atvriders, O=Atvriders, C=US"
# It will prompt for a keystore password (and key password — use the same to keep it simple).
```

## 2. Add four repository secrets

Base64-encode the keystore:
```bash
base64 -w0 upload-keystore.jks > keystore.b64    # macOS: base64 -i upload-keystore.jks -o keystore.b64
```

In **GitHub → repo → Settings → Secrets and variables → Actions → New repository secret**,
add:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | the entire contents of `keystore.b64` |
| `KEYSTORE_PASSWORD` | your keystore password |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | your key password (same as keystore password if you reused it) |

The CI workflow decodes `KEYSTORE_BASE64` to a file and passes the rest through env; the
Gradle `release` signing config picks them up automatically. (Delete `keystore.b64`
afterwards; never commit the keystore — it is already in `.gitignore`.)

## 3. Cut a release

```bash
git tag -a v1.0.0 -m "Ham Radio WSPR TX/RX v1.0.0"
git push origin v1.0.0
```

This triggers the **publish** job, which:
- sets `versionName` from the tag (`v1.0.0` → `1.0.0`) and `versionCode` from the CI run
  number (always strictly increasing — Play requires this);
- builds and **signs** `app-release.aab` with your upload key;
- uploads it as the **`ham-radio-wspr-txrx-play-aab`** artifact and attaches the sideload
  APK to a GitHub Release.

Download the `.aab` from the run's Artifacts and upload it in Play Console.

## 4. In Play Console (after your developer account is approved)

1. **Create app** → name "Ham Radio WSPR TX/RX", default language, app/free, accept policies.
2. **Release → Setup → App signing**: keep **Play App Signing** enabled (default).
3. **Test internally first:** Release → Testing → Internal testing → create release →
   upload the `.aab` → add your own Google account as a tester. Verify on a real device.
4. Complete **App content**: privacy policy URL, Data Safety (`docs/DATA_SAFETY.md`),
   content rating (`docs/CONTENT_RATING.md`), target audience, ads = No, government app = No.
5. Fill **Main store listing** (`docs/PLAY_STORE_LISTING.md`) + graphics + screenshots.
6. Promote the internal release to **Production** when you're happy.

## Notes
- The sideload APK stays debug-signed for convenience; only the `.aab` needs your upload key
  for Play. If you prefer, you can also sign the APK by the same secrets (already wired).
- Target API level (35) currently meets Play's requirement; keep `compileSdk`/`targetSdk`
  current as Google raises the bar each year.
- Before the very first production release, also commit the generated Room schema JSON
  (`app/schemas/…`) so future DB migrations have a baseline (it's exported by CI now).
