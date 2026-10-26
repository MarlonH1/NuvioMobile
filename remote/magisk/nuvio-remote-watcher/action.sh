#!/system/bin/sh
# Magisk "Action" button: shows what the watcher is doing.
PID=$(cat /data/adb/nuvio-remote/watcher.pid 2>/dev/null)
if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then echo "Watcher running (pid $PID)"; else echo "Watcher not running"; fi
echo "--- last log lines ---"
tail -n 15 /data/adb/nuvio-remote/watcher.log 2>/dev/null
