# Closed testing — the plan that gets production access

Google rejected the first applications with:

> *Testers were not engaged with your app during your closed test* /
> *You didn't follow testing best practices, which may include gathering and acting on
> user feedback through updates to your app*

That is a **process** verdict, not a code verdict. Google does not review your build
tooling, your optimization score, or your AGP version. It looks at three signals:

| Signal | What Google checks | What kills an application |
|---|---|---|
| **Opt-in count** | ≥12 testers opted in, **continuously**, for 14 days | Invited but never accepted; someone opts out mid-way and resets continuity |
| **Engagement** | Testers actually **installed and used** the app across the period | Installs with no sessions; one open on day 1 and nothing after |
| **Iteration** | Feedback gathered, and **updates shipped in response** | Zero releases during the test; generic questionnaire answers |

The fix is to run a test that genuinely looks like a beta, and to keep the evidence.

---

## 1. Recruit 15–18 testers (not 12)

Recruit a buffer: if one person opts out, continuity resets for that slot. Aim for
15–18 so you can lose a few and still clear 12.

**Where the real testers are** — this app's audience is specific and reachable:
- Your local **amateur radio club** mailing list / net — the single best source.
- **groups.io**: `WSJTX`, `WSPRnet`, `QRPLabs`, `ham-radio-software` type groups.
- **Reddit**: r/amateurradio, r/RTLSDR, r/QRP (read each sub's self-promo rules first).
- **QRZ.com forums** → Software & Apps.
- Regional WSPR/QRP Facebook or Discord groups.

**Do not** use tester-exchange / "review swap" services. Google detects reciprocal
testing rings and it makes rejection *more* likely, not less.

### Recruitment post (paste and edit)

> **Beta testers wanted — free Android WSPR app (no ads, no tracking)**
>
> I've written **Ham Radio WSPR TX/RX**, a free Android app for WSPR operators, and I need
> a handful of testers before it can go live on Google Play.
>
> What it does:
> • Live WSPR spots from wspr.live, PSKReporter and the Reverse Beacon Network
> • Interactive globe map with great-circle paths and the grey line
> • Spots/SNR charts, plus **Head2Head** — compare two receivers on the exact
>   transmissions they both heard (a clean A/B for antenna testing)
> • A real WSPR **transmit** encoder: builds a proper WSPR frame and plays it as audio,
>   time-synced to the even UTC minute, for acoustic coupling / VOX into your rig
>
> No ads, no analytics, no account required. Free and open source.
>
> **What I need:** install it from the Play test link and actually use it over the next two
> weeks — check spots, poke the map, try a transmit if you're licensed — and tell me what's
> broken or missing. I'll be shipping updates through the test based on what you report.
>
> Reply with the **Google account email** you use on your Android device and I'll add you.
> 73!

---

## 2. Make engagement happen (this is what failed last time)

Being on the tester list is not engagement. Plan for **actual sessions across the 14 days**.

- **Day 0:** send the opt-in link plus a one-line "what to try first". Confirm each person
  has actually *installed* — ask them to reply "installed". Chase the silent ones.
- **Ask for specific tasks**, not "have a play". People do concrete things:
  1. Search your own callsign and check the spots look right
  2. Open the map, tap a station, rotate the phone
  3. Set a band filter and confirm the list matches
  4. Run one full transmit cycle (licensed testers) and watch the notification
  5. Try it with mobile data off, to see the offline behaviour
- **Mid-test nudge (~day 5 and ~day 10):** post "what's changed" and ask one specific
  question. This is what keeps sessions happening in the second week.
- Tell testers the test runs the **full 14 days** and to please not uninstall or leave the
  programme — continuity is what's being measured.

## 3. Ship at least 2 updates during the test

This is the "acting on feedback" evidence. Even small ones count, and each new build
also gives testers a reason to open the app again.

- Cut a tagged release (`git tag -a v1.0.x`) → CI builds the signed `.aab` → upload to the
  **closed** track with release notes that name the feedback:
  > *"v1.0.4 — thanks to G0XYZ for spotting that the band filter didn't apply to
  > PSKReporter results; fixed. Also larger tap targets on the map after feedback from
  > VK2ABC."*
- Release notes that quote testers are exactly the paper trail the reviewer wants.

## 4. Keep a feedback log

Fill this in as you go; you will paste from it into the application questionnaire.

| Date | Tester (call / initials) | Feedback | Action taken | Shipped in |
|---|---|---|---|---|
| | | | | |
| | | | | |

## 5. Answer the application questionnaire properly

Google asks how you recruited testers, what feedback you got, and how you acted on it.
**Generic answers fail.** Write specifics:

- *How did you recruit?* — "Posted to my local club's reflector and the WSJTX groups.io
  list; 16 licensed amateurs opted in, all active WSPR operators."
- *What feedback did you get?* — quote 3–4 concrete items from your log, with callsigns.
- *How did you act on it?* — name the versions: "v1.0.4 fixed the band filter reported by
  …; v1.0.5 added … after three testers asked for it."
- *What changed as a result?* — link the release notes.

---

## 6. Timeline

| Day | Action |
|---|---|
| −7 to 0 | Recruit 15–18 testers; collect Google account emails |
| 0 | Add testers to the closed track, roll out, confirm installs |
| 1–13 | Nudge on ~day 5 and ~day 10; log feedback; **ship 2+ updates** |
| 14 | Confirm ≥12 still opted in and engaged |
| 14+ | Apply for production access with the questionnaire filled from the log |
| +≤7 | Google reviews (up to 7 days) |

**The clock only counts continuous days with ≥12 opted-in testers.** Everything else —
screenshots, listing copy, App content, the build itself — is already done and does not
gate this. Recruiting is the critical path; start today.
