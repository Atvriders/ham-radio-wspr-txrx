# Play Console — App access declaration

Play Console → **App content → App access**.

This is a **mandatory** section, and it is a prerequisite Google requires before the
Target audience declaration can be completed.

---

## Answer

Select **"All or some functionality is restricted"**.

The default ("All functionality is available without special access") would be wrong: the
listing advertises QRZ.com callsign lookup, and that feature is gated on a third-party
account the reviewer does not have.

### Instruction entry

| Field | Value |
|---|---|
| Name | QRZ.com callsign lookup (optional feature) |
| Username | **leave blank** (the field is optional) |
| Password | **leave blank** (the field is optional) |
| Any other instructions | see below |

### Instructions text (paste as-is)

> Everything in this app works with no account: live WSPR spots, the globe map, the
> charts, Head2Head, and the WSPR audio encoder all function on a clean install with no
> sign-in.
>
> One optional feature is gated: the QRZ.com callsign lookup on a spot's detail card. It
> requires the reviewer's own QRZ.com XML API subscription, which is a paid product sold
> by QRZ.com, so we cannot supply working test credentials. Without credentials the
> detail card simply shows "No QRZ data (set login in Settings for details)" and the rest
> of the app is unaffected. A "QRZ.com" button on the same card opens the public qrz.com
> web page for that callsign in a browser and needs no account.
>
> Please note: the first time you tap Transmit, the app shows a one-time dialog asking
> you to confirm you hold a valid amateur radio licence. Transmit does not proceed until
> that dialog is accepted. The app produces audio only — it does not emit radio-frequency
> energy.

---

## Why no test account is supplied

**Do not create or share a QRZ.com account for the reviewer.** A free QRZ account
authenticates successfully but returns "subscription required" on XML API lookups, so the
reviewer would follow the instructions, appear to sign in correctly, and still see
"No QRZ data" — a worse outcome than being told up front that the feature is
third-party-gated.

## Related listing wording

Keep `docs/PLAY_STORE_LISTING.md` honest about this: the full description says
"QRZ.com callsign lookup" as an optional detail-card feature, not a headline capability.
