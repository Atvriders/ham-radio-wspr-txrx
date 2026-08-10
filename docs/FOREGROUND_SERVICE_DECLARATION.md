# Play Console — Foreground service permissions declaration

Play Console → **App content → Foreground service permissions**.

Apps targeting Android 14+ must declare **each** foreground-service type they use. This
app targets API 36 and declares exactly one:

- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `<service android:foregroundServiceType="mediaPlayback">` on `TxForegroundService`

App content cannot be marked complete — and therefore **no track can be rolled out,
including closed testing** — until this section is filled in, and it requires a video.

---

## 1. Approved use case to select

**Media playback — continue audio or video playback from the background, including
streaming.**

Type fidelity is defensible and deliberate: `mediaPlayback` has "Runtime prerequisites:
None", and its approved use case is literally continuing audio playback from the
background. `WsprPlayer` streams real PCM through `AudioTrack` with `USAGE_MEDIA`.

**Do not switch the type**, and **do not add a MediaSession** — a session would expose a
timing-exact, unresumable 110.6 s frame to headset/AVRCP/lockscreen pause events, which
would corrupt transmissions rather than improve them.

---

## 2. Functionality description (paste as-is)

> The user enters their amateur callsign, grid locator and power, then taps Transmit. The
> app generates a 110.6-second audio tone sequence and plays it through the device speaker
> so it can be acoustically coupled into an amateur radio transceiver. The foreground
> service keeps this user-initiated audio playback running if the user leaves the app or
> the screen turns off. The app does not transmit radio-frequency energy; it produces
> sound only.

---

## 3. Impact if the task is deferred or interrupted (paste as-is)

> WSPR is a strict 110.6-second, time-synchronised protocol that must begin exactly on an
> even UTC minute. If playback is deferred past that start, or interrupted mid-sequence,
> the tone is corrupted and no receiving station can decode it; the user must wait for the
> next even UTC minute to retry.

---

## 4. Demo video

**Requirement:** "a link to a video demonstrating each foreground service feature."

- **Host:** unlisted YouTube. It must open with **no sign-in** and stay live
  indefinitely. A Google Drive link that prompts for login is a common bounce.
- **Length:** 60–90 s, one continuous take.

**Shot list — record in this order:**

1. Open the app, go to the **TX** tab.
2. Enter callsign and grid; tap **Transmit**.
3. Accept the one-time **amateur licence acknowledgement** dialog.
4. Allow the notification permission prompt when it appears.
5. Show the countdown to the even UTC minute, then the transmit progress.
6. **Pull down the notification shade** so the ongoing notification is clearly visible,
   with its title, count-down chronometer, and **Stop** action.
7. Press **Home**. The tone audibly continues — this is the whole point of the service.
8. Pull the shade down again and tap **Stop**. Playback ends immediately.

Step 6 is the shot Play is actually looking for. It only exists because the
`POST_NOTIFICATIONS` permission is now declared and requested (audit B4) and the
notification has a Stop action and an immediate-display behaviour (audit H3) — the video
could not have been filmed before those landed.

---

## 5. Record of submission

Fill in when submitted, so a future re-declaration matches:

| Field | Value |
|---|---|
| Date submitted | _(fill in)_ |
| Video URL | _(fill in)_ |
| Declared types | `mediaPlayback` |
| Review outcome | _(fill in)_ |
