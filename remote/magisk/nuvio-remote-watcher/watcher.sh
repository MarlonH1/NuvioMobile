#!/system/bin/sh
# Nuvio Remote watcher (root, started by service.sh).
#
# Holds ONE idle TCP connection to the TV daemon's /watch stream. The TV writes a line only
# when playback starts or stops, plus a keepalive every 10 min, so the phone sleeps in between
# (an incoming packet wakes it, exactly like push messages). On "active":true it starts
# Nuvio's TvRemoteNowPlayingService, which shows the media notification.
#
# Battery notes:
# - `sleep` and `read -t` use the monotonic clock, which stops while the phone is suspended,
#   so backoff and timeouts cost nothing in deep sleep.
# - No Wi-Fi: nothing is attempted, only a cheap check once a minute while the CPU is awake.
# - Nuvio writes the target TV to files/tv_remote_watch.conf; without it the watcher idles.

# Overridable for manual tests: NUVIO_WATCH_STATE=<dir> NUVIO_WATCH_CONFIG=<file> sh watcher.sh
STATE=${NUVIO_WATCH_STATE:-/data/adb/nuvio-remote}
LOG=$STATE/watcher.log
PACKAGES="com.nuvio.app com.nuviodebug.com"
SERVICE=com.nuvio.app.features.tvremote.TvRemoteNowPlayingService
ACTION_WAKE=com.nuvio.app.tvremote.WAKE
READ_TIMEOUT=1500 # s of awake time without any line (TV keepalive is 600 s) -> reconnect
BACKOFF_MAX=60
NC=nc
command -v nc > /dev/null 2>&1 || NC=/system/bin/nc

mkdir -p "$STATE"

log() {
  if [ -f "$LOG" ] && [ "$(wc -c < "$LOG")" -gt 262144 ]; then mv "$LOG" "$LOG.1"; fi
  echo "$(date '+%m-%d %H:%M:%S') $*" >> "$LOG"
}

find_config() {
  [ -n "$NUVIO_WATCH_CONFIG" ] && { echo "$NUVIO_WATCH_CONFIG"; return 0; }
  for pkg in $PACKAGES; do
    file=/data/data/$pkg/files/tv_remote_watch.conf
    [ -f "$file" ] && { echo "$file"; return 0; }
  done
  return 1
}

value() {
  sed -n "s/^$1=//p" "$2" | head -n 1
}

on_wifi() {
  ip -4 addr show wlan0 2> /dev/null | grep -q "inet "
}

wake_app() {
  log "TV is playing ($1) -> waking $2"
  # stdin must not be the response FIFO: SELinux forbids system_server to read it and the
  # binder call then fails ("Failed transaction").
  out=$(/system/bin/am start-foreground-service -n "$2/$SERVICE" -a "$ACTION_WAKE" < /dev/null 3>&- 2>&1)
  case "$out" in
    *rror* | *Exception* | *Failure* | *Failed*) log "am failed: $out" ;;
  esac
}

# One connection; returns when it ends. Two FIFOs so nc gets a stdin that stays open without
# a helper process, and the reader can time out and kill nc.
watch_once() {
  host=$1 port=$2 token=$3 pkg=$4
  req=$STATE/req.fifo
  resp=$STATE/resp.fifo
  rm -f "$req" "$resp"
  mkfifo "$req" "$resp" || return 1

  # No -w: busybox nc treats it as an idle timeout and would drop the quiet connection.
  $NC "$host" "$port" < "$req" > "$resp" 2> /dev/null &
  nc_pid=$!
  exec 3> "$req"
  printf 'GET /watch?token=%s HTTP/1.0\r\nHost: %s\r\n\r\n' "$token" "$host" >&3

  connected=0
  was_active=0
  while IFS= read -r -t "$READ_TIMEOUT" line; do
    case "$line" in
      HTTP/1.*" 200"*) connected=1; log "connected to $host:$port" ;;
      HTTP/1.*" 401"*) log "TV rejected the token (re-pair in Nuvio)"; connected=2 ;;
      *'"type":"watch"'*)
        case "$line" in
          *'"active":true'*)
            if [ "$was_active" = 0 ]; then
              title=$(echo "$line" | sed -n 's/.*"title":"\([^"]*\)".*/\1/p')
              wake_app "${title:-?}" "$pkg"
            fi
            was_active=1
            ;;
          *) was_active=0 ;;
        esac
        ;;
    esac
  done < "$resp"

  kill "$nc_pid" 2> /dev/null
  exec 3>&-
  rm -f "$req" "$resp"
  return $connected
}

# Single instance: two watchers would share the FIFOs and keep breaking each other's link.
if [ -f "$STATE/watcher.pid" ] && kill -0 "$(cat "$STATE/watcher.pid")" 2> /dev/null; then
  exit 0
fi
echo $$ > "$STATE/watcher.pid"

log "watcher started (pid $$)"
backoff=5
while true; do
  config=$(find_config)
  if [ -z "$config" ]; then
    sleep 300
    continue
  fi
  if ! on_wifi; then
    sleep 60
    continue
  fi
  host=$(value host "$config")
  port=$(value port "$config")
  token=$(value token "$config")
  pkg=$(value package "$config")
  if [ -z "$host" ] || [ -z "$token" ]; then
    sleep 300
    continue
  fi

  # One ping first: a TV that is off would otherwise cost a ~2 min TCP connect attempt.
  if ping -c 1 -W 2 "$host" > /dev/null 2>&1; then
    watch_once "$host" "${port:-47800}" "$token" "${pkg:-com.nuvio.app}"
    result=$?
  else
    result=0
  fi
  if [ "$result" = 1 ]; then
    # Was connected: the TV went away or the link dropped. Retry soon.
    backoff=5
  elif [ "$result" = 2 ]; then
    backoff=600
  else
    backoff=$((backoff * 2))
    [ "$backoff" -gt "$BACKOFF_MAX" ] && backoff=$BACKOFF_MAX
  fi
  sleep "$backoff"
done
