# Google Play — Content rating questionnaire (recommended answers)

Fill in **Play Console → App content → Content rating**. Start the questionnaire, enter a
contact email, and choose category **Utility, Productivity, Communication, or Other**.
Recommended answers for this app (a ham-radio utility with no objectionable content):

| Question | Answer |
|---|---|
| Category | **Utility / Productivity / Communication / Other** |
| Violence (realistic or cartoon) | **No** |
| Blood / gore | **No** |
| Sexual or suggestive content | **No** |
| Nudity | **No** |
| Profanity / crude humour | **No** |
| Controlled substances (drugs/alcohol/tobacco) | **No** |
| Gambling (simulated or real) | **No** |
| Frightening / horror content | **No** |
| Does the app share the user's current physical location with other users? | **No** — location is used only on-device to compute the operator's Maidenhead grid; it is never uploaded or shared. |
| Does the app allow users to interact or communicate with each other? | **No** — there is no in-app user-to-user messaging or social feature. (It displays public WSPR/propagation reports, not user communications.) |
| Does the app allow users to purchase digital goods? | **No** |
| Is the app a web browser or search engine? | **No** |
| Does the app share user-provided content? | **No** |

**Expected result:** rated **Everyone / PEGI 3 / 3+** across regions.

> Note on the transmit feature: the app only generates *audio*; it does not transmit RF and
> contains no objectionable content, so it does not affect the rating. The licensing
> responsibility is covered by the in-app disclaimer and the store description — it is not a
> content-rating factor.

---

# Target audience and content

**This is a separate mandatory declaration** (Play Console → App content → Target audience
and content), not part of the content rating, and **App content cannot be marked complete
without it**.

| Question | Answer |
|---|---|
| Target age group(s) | **Ages 18 and over only** — do **not** tick 13–15 |
| Does your store listing unintentionally appeal to children? | **No** |
| Teacher Approved / Designed for Families | **Do not opt in** |
| Restrict minor access | Leave off (that control is for gambling/dating-class apps) |

**Rationale, to keep on file.** The only interactive output is an RF transmit aid behind a
one-time amateur-licence acknowledgement dialog; the listing addresses licensed operators
and carries a licensing disclaimer; the icon and feature graphic are a dark technical
wireframe globe with no cartoon or child-directed styling. Ticking any group **12 and
under** (and, in some locales, 13–15) pulls the app under the Families Policy
Requirements and adds child-directed scrutiny of `ACCESS_COARSE_LOCATION` for zero
benefit.

Target audience declares who the app is *designed and marketed for*, not who may legally
use it — minors who hold amateur licences can still install it. A content rating of
**Everyone** alongside a target audience of **18+** is a normal combination for a
technical utility.

**Google requires these to be completed first**, in this order: Ads = **No** →
**App access** (see `docs/APP_ACCESS.md`) → **Privacy policy URL** → Content rating →
Target audience.
