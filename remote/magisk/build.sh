#!/bin/sh
# Packs the Magisk module: remote/magisk/build.sh -> remote/magisk/dist/nuvio-remote-watcher-<version>.zip
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
MODULE="$HERE/nuvio-remote-watcher"
VERSION="$(sed -n 's/^version=//p' "$MODULE/module.prop")"
OUT="$HERE/dist/nuvio-remote-watcher-$VERSION.zip"
mkdir -p "$HERE/dist"
rm -f "$OUT"
cd "$MODULE"
chmod 755 service.sh watcher.sh action.sh uninstall.sh customize.sh META-INF/com/google/android/update-binary
zip -qr -X "$OUT" META-INF module.prop customize.sh service.sh watcher.sh action.sh uninstall.sh
echo "$OUT"
