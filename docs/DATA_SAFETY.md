# Google Play Data Safety form — binding declaration

_For Ham Radio WSPR TX/RX. Fill in Play Console → App content → Data safety exactly as
written here. These answers are the single source of truth and must stay consistent with
`docs/PRIVACY_POLICY.md` / `docs/privacy.html`._

> **The answer key in §2 is the binding submission.** Earlier drafts of this file offered
> two defensible alternatives for the "Shared" questions; the form takes one deterministic
> answer, and misrepresentation here draws removal or suspension rather than a warning.
> Every declared type is **Shared = Yes**: the app queries independent third-party
> services, not contracted service providers acting on the developer's behalf.

---

## 1. Overview answers

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** |
| Do you provide a way for users to request that their data be deleted? | **Yes** |

**Encrypted in transit — the honest detail.** Every HTTP endpoint (wspr.live,
PSKReporter, QRZ.com, OpenFreeMap tiles) is HTTPS, and cleartext HTTP is disabled at OS
level for all domains (`res/xml/network_security_config.xml`, `cleartextTrafficPermitted="false"`
in base-config). The one exception is the optional Reverse Beacon Network telnet feed,
which is unencrypted by protocol design and carries **only the fixed placeholder callsign
`N0CALL`** — never the user's callsign, credentials or location. No user data therefore
travels unencrypted, and the "Yes" answer is accurate. If a real callsign is ever wired
into RBN, this answer must be revisited (a unit test pins the outbound bytes to prevent
that happening silently).

**Data deletion.** Uninstall, or Android Settings → Apps → Ham Radio WSPR TX/RX →
Storage → Clear storage; QRZ credentials can be cleared individually in the app's
Settings. There is no server-side account to delete — the developer operates no server.

---

## 2. Answer key — the four rows to declare

| Data type | Collected | Shared | Optional | Purpose | Recipients |
|---|---|---|---|---|---|
| Personal info → **User IDs** (amateur callsign, QRZ username) | **Yes** | **Yes** | **Yes** | App functionality | wspr.live, PSKReporter, QRZ.com |
| Personal info → **Other info** (QRZ.com password) | **Yes** | **Yes** | **Yes** | App functionality | QRZ.com |
| App activity → **In-app search history** | **Yes** | **Yes** | **Yes** | App functionality | wspr.live, PSKReporter (RBN when enabled) |
| **Location** (Approximate **and** Precise) | **No** | No | — | — | — |

### Notes per row

**User IDs.** The amateur callsign is an identifier issued by a licensing authority, so it
maps to *User IDs*, not *Other info*. It goes into the WSPR message the user transmits and
into spot queries. The QRZ username belongs in the same row and is only present if the
user configures QRZ.

**Other info — free-text description to paste into the form:**

> QRZ.com account password, entered by the user, encrypted at rest with the Android
> Keystore and excluded from backup, sent to QRZ.com over HTTPS solely to authenticate
> callsign lookups. Never sent to the developer; the developer operates no server.

There is no "User account credentials" type in Play's taxonomy. The Personal info list is
exactly: Name, Email address, User IDs, Address, Phone number, Race and ethnicity,
Political or religious beliefs, Sexual orientation, Other info. **Other info** is the
correct home for the password, and it must be declared — under-declaring a third-party
password that the privacy policy openly says is sent to `xmldata.qrz.com` is the
highest-signal inconsistency an automated check can find.

**In-app search history** is the form's exact type name (not "Search history"). The
callsign / grid / band / time-range the user searches are sent to the enabled sources; the
20 most recent callsigns are also kept on the device.

**Location = Not collected, and that is correct.** Play defines *collection* as
transmission off the device. Coarse location is read only when the user taps GPS,
converted to a Maidenhead locator on-device, and never uploaded or stored off-device.
Requesting `ACCESS_COARSE_LOCATION` is a *permission*, not a Data Safety collection. Do
not declare Approximate or Precise location. (`ACCESS_FINE_LOCATION` is actively stripped
from the merged manifest — see the CI guard in `scripts/check-merged-manifest.sh` — so the
shipped permission list matches this declaration.)

**OpenFreeMap is deliberately not declared.** Tile requests carry only the IP address
inherent to any HTTPS request, and IP is not a type in Play's taxonomy. The row stays in
the privacy policy — over-disclosure there is safe, the reverse is not.

---

## 3. Declare "No" for everything else

Financial info · Health and fitness · Messages · Photos and videos · Audio files, music
files, voice or sound recordings · Files and docs · Calendar · Contacts · App activity
other than in-app search history · Web browsing history · App info and performance
(no crash logs, no diagnostics) · Device or other IDs.

No advertising ID, no analytics SDK, no crash-reporting SDK, no tracking of any kind.

---

## 4. Security practices section

| Question | Answer |
|---|---|
| Data is encrypted in transit | **Yes** (see §1) |
| Users can request that data be deleted | **Yes** |
| Committed to follow the Play Families Policy | **No** — the app is not directed at children (see `docs/CONTENT_RATING.md` → Target audience) |
| Independent security review | Leave unticked |

---

## 5. Importing via CSV (optional, faster than the form)

The CSV cannot be written from scratch — Google generates it with internal Question IDs
specific to the current form version.

1. On the **Data safety** page, click **Export to CSV**.
2. Fill the **`Response value`** column with `TRUE` / `FALSE` from §2 and §3.
3. **Import** the file back — this **overwrites** the current answers.

---

## 6. Keep consistent

- The privacy policy URL in the Console must be **byte-identical** to the one in the app
  (`R.string.privacy_policy_url`) and in `docs/PLAY_STORE_LISTING.md`:
  `https://atvriders.github.io/ham-radio-wspr-txrx/privacy.html`
- Third parties named in both this form and the privacy policy: **QRZ.com, wspr.live,
  PSKReporter, Reverse Beacon Network**.
- If crash reporting or analytics is ever added, reopen this form to declare
  *App info and performance*.
