#!/system/bin/sh
[ -f /data/adb/nuvio-remote/watcher.pid ] && kill "$(cat /data/adb/nuvio-remote/watcher.pid)" 2>/dev/null
rm -rf /data/adb/nuvio-remote
