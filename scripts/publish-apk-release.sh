#!/usr/bin/env bash
#
# Builds the full release APKs (then a debug APK) of the current commit, checks that it is signed with the shared key
# (docs/shared-signing.md) and publishes it as the latest GitHub release of this fork, so it can
# always be downloaded from
#   https://github.com/<repo>/releases/latest/download/Nuvio.apk
#
# Usage: scripts/publish-apk-release.sh [--repo owner/name]
# Needs: a clean, pushed working tree, the shared keystore in local.properties, and `gh` logged in.

set -euo pipefail

REPO="MarlonH1/NuvioMobile"
while [ $# -gt 0 ]; do
  case "$1" in
    --repo) REPO="$2"; shift 2 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

cd "$(dirname "$0")/.."
SHARED_KEY_SHA256="324d485c07dd86019fb438b0903c6e6849f2d4b651c56dc346e95fbbbb5f6b36"
OUT=$(mktemp -d)

if [ -n "$(git status --porcelain -- . ':!local.properties')" ]; then
  echo "Uncommitted changes: commit them first, so the release matches a commit on GitHub." >&2
  exit 1
fi
COMMIT=$(git rev-parse HEAD)
BRANCH=$(git branch --show-current)
git fetch -q origin "$BRANCH"
if ! git merge-base --is-ancestor "$COMMIT" "origin/$BRANCH"; then
  echo "$COMMIT is not on origin/$BRANCH yet: push first." >&2
  exit 1
fi

# Release first (optimized, one APK per ABI); the debug build is an extra download.
./gradlew :androidApp:assembleFullRelease \
  -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore -q
./gradlew :androidApp:assembleFullDebug \
  -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore -q
for apk in androidApp/build/outputs/apk/full/release/androidApp-full-*-release.apk; do
  abi=$(basename "$apk" | sed -E 's/^androidApp-full-(.+)-release\.apk$/\1/')
  cp "$apk" "$OUT/Nuvio-$abi.apk"
done
cp "$OUT/Nuvio-arm64-v8a.apk" "$OUT/Nuvio.apk"
cp androidApp/build/outputs/apk/full/debug/androidApp-full-debug.apk "$OUT/Nuvio-debug.apk"

APKSIGNER=$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/*/apksigner | sort -V | tail -1)
for apk in "$OUT"/*.apk; do
  SIGNER=$("$APKSIGNER" verify --print-certs "$apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)
  if [ "$SIGNER" != "$SHARED_KEY_SHA256" ]; then
    echo "$(basename "$apk") is signed with $SIGNER, not the shared key. See docs/shared-signing.md." >&2
    exit 1
  fi
done

VERSION=$(sed -n 's/^MARKETING_VERSION *= *//p' iosApp/Configuration/Version.xcconfig | head -1)
# UTC commit time, the same stamp the app carries (AppVersionConfig.FORK_BUILD).
TAG="shad-$(TZ=UTC git log -1 --format=%cd --date=format-local:%Y%m%d-%H%M HEAD)-$(git rev-parse --short HEAD)"
PREVIOUS=$(gh release list -R "$REPO" --limit 20 --json tagName -q '.[].tagName' | grep '^shad-' | head -1 || true)
NOTES=$(mktemp)
trap 'rm -rf "$NOTES" "$OUT"' EXIT
{
  echo "Nuvio $VERSION, branch \`$BRANCH\`, commit $COMMIT."
  echo
  echo "Signed with the shared key (SHA-256 \`$SHARED_KEY_SHA256\`)."
  echo
  if [ -n "$PREVIOUS" ] && git rev-parse -q --verify "refs/tags/$PREVIOUS" >/dev/null 2>&1; then
    echo "Changes since $PREVIOUS:"
    git log --no-merges --format='- %s' "$PREVIOUS..$COMMIT"
  else
    echo "Recent changes:"
    git log --no-merges --format='- %s' -15 "$COMMIT"
  fi
} > "$NOTES"

gh release create "$TAG" "$OUT"/*.apk -R "$REPO" --target "$COMMIT" \
  --title "Nuvio $VERSION ($(date -u +%Y-%m-%d))" --notes-file "$NOTES" --latest
git fetch -q origin "refs/tags/$TAG:refs/tags/$TAG"
echo "published: https://github.com/$REPO/releases/latest/download/Nuvio.apk"
