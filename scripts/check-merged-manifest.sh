#!/usr/bin/env bash
#
# Fails the build if a denylisted permission survives manifest merging into the shipped
# release artifacts (audit H1).
#
# MapLibre's AAR manifest declares ACCESS_FINE_LOCATION and ACCESS_WIFI_STATE; the app
# manifest strips them with tools:node="remove". If that ever regresses — a MapLibre
# upgrade, a new dependency, a lost tools: namespace — the store listing silently starts
# advertising "precise location" and "view Wi-Fi connections" next to a privacy policy
# and Data Safety declaration that say coarse-only. This catches it at build time.
#
# Deliberately a DENYLIST, not an allowlist: androidx injects its own signature-level
# permission (DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION), so an "exactly these five"
# assertion would fail immediately and for the wrong reason.
#
# Usage: scripts/check-merged-manifest.sh [module-build-dir]   (default: app/build)

set -uo pipefail

BUILD_DIR="${1:-app/build}"

DENYLIST=(
  ACCESS_FINE_LOCATION
  ACCESS_BACKGROUND_LOCATION
  ACCESS_WIFI_STATE
  DUMP
  RECORD_AUDIO
  QUERY_ALL_PACKAGES
  READ_PHONE_STATE
  AD_ID
  REQUEST_INSTALL_PACKAGES
  SCHEDULE_EXACT_ALARM
  USE_EXACT_ALARM
)

mapfile -t MANIFESTS < <(
  find "$BUILD_DIR/intermediates" \
    \( -path '*merged_manifests/release*' \
    -o -path '*packaged_manifests/release*' \
    -o -path '*bundle_manifest/release*' \) \
    -name AndroidManifest.xml 2>/dev/null | sort
)

if [ "${#MANIFESTS[@]}" -eq 0 ]; then
  echo "::error::No merged release manifest found under $BUILD_DIR/intermediates — the permission guard cannot run."
  exit 1
fi

echo "Checking ${#MANIFESTS[@]} merged manifest(s):"
printf '  %s\n' "${MANIFESTS[@]}"

FAIL=0
for P in "${DENYLIST[@]}"; do
  for M in "${MANIFESTS[@]}"; do
    # Plain if/grep, never `grep -q ... && exit 1`: under `set -e` that construct
    # inverts and fails the step on every clean build.
    if grep -q "android.permission.$P" "$M"; then
      echo "::error::Denylisted permission $P leaked into $M"
      FAIL=1
    fi
  done
done

if [ "$FAIL" -ne 0 ]; then
  echo "Merged-manifest permission check FAILED."
  exit 1
fi

echo "Merged-manifest permission check passed — no denylisted permission present."
echo "Permissions actually shipped:"
grep -ho 'android\.permission\.[A-Z_]*' "${MANIFESTS[0]}" | sort -u | sed 's/^/  /'
