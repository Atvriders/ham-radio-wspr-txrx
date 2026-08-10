#!/usr/bin/env bash
#
# Proves the Play bundle is signed by the real upload key (audit B6).
#
# The release build type falls back to the *debug* signing config when no keystore is
# present. That fallback already fired in practice: v0.1.0, v0.1.1 and v0.2.0 on GitHub
# Releases are debug-signed, each with a different ephemeral runner key, all carrying the
# production applicationId. Play's rejection ("You uploaded an APK or Android App Bundle
# that was signed in debug mode") only surfaces after a human has uploaded.
#
# Pinned to the fingerprint rather than the subject name, because a name check cannot
# catch rotation to the *wrong* key.
#
# Usage: scripts/check-release-signer.sh <path-to-aab>
#        EXPECTED_UPLOAD_CERT_SHA256=<hex> to override the pin after a deliberate rotation.

set -uo pipefail

AAB="${1:?usage: check-release-signer.sh <aab>}"

# Upload certificate of record: CN=Atvriders, O=Atvriders, C=US.
# Measured from the v1.0.0 / v1.0.1 release artifacts.
EXPECTED="${EXPECTED_UPLOAD_CERT_SHA256:-cec75e15e33dc7c7edcccf8d3878854bb276cd74440fa771108998fce4ea3bb4}"

if [ ! -f "$AAB" ]; then
  echo "::error::Bundle not found: $AAB"
  exit 1
fi

OUT="$(keytool -printcert -jarfile "$AAB" 2>&1)"
if [ $? -ne 0 ]; then
  echo "::error::keytool could not read a signature from $AAB"
  echo "$OUT"
  exit 1
fi

OWNER="$(printf '%s\n' "$OUT" | grep -m1 '^Owner:')"

# Plain case/if, never `grep -q '...' && exit 1`: under `bash -e` that construct inverts
# and fails the step on every correctly signed build.
case "$OWNER" in
  *"CN=Android Debug"*)
    echo "::error::$AAB is DEBUG-SIGNED ($OWNER). Play rejects debug-signed bundles."
    exit 1
    ;;
esac

ACTUAL="$(
  printf '%s\n' "$OUT" \
    | grep -m1 -i 'SHA256:' \
    | sed 's/.*[Ss][Hh][Aa]256:[[:space:]]*//' \
    | tr -d ': \r\t' \
    | tr 'A-F' 'a-f'
)"

if [ -z "$ACTUAL" ]; then
  echo "::error::Could not read the signer SHA-256 fingerprint from keytool output."
  echo "$OUT"
  exit 1
fi

if [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "::error::Signer certificate mismatch for $AAB"
  echo "  expected: $EXPECTED"
  echo "  actual:   $ACTUAL"
  echo "  $OWNER"
  echo "If the upload key was rotated deliberately, set EXPECTED_UPLOAD_CERT_SHA256 and update this script."
  exit 1
fi

echo "Signer OK — $OWNER"
echo "SHA-256 matches the pinned upload certificate ($EXPECTED)."
