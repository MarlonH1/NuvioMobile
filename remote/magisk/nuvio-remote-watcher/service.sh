#!/system/bin/sh
# Magisk late_start service: runs the watcher in the background, at low priority.
MODDIR=${0%/*}
# Wait until boot finished so `am` works.
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 5; done
mkdir -p /data/adb/nuvio-remote
nohup nice -n 10 sh "$MODDIR/watcher.sh" > /dev/null 2>&1 &
