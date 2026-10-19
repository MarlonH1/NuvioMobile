# Nuvio Remote Watcher (Magisk-Modul)

Zeigt die TV-Steuerung von Nuvio als Medien-Benachrichtigung, sobald auf dem Fernseher etwas
läuft, auch wenn Nuvio auf dem Handy geschlossen ist. Braucht den Nuvio-Remote-Daemon auf dem
Fernseher (`NuvioTVSmart/remote/daemon`).

```
TV-Daemon ──/watch (eine ruhende TCP-Verbindung)──▶ watcher.sh (root, Magisk)
                                                        │ "Wiedergabe gestartet"
                                                        ▼
                     am start-foreground-service … TvRemoteNowPlayingService (WAKE)
                                                        │
                     Nuvio verbindet sich, zeigt die Benachrichtigung, endet mit der Wiedergabe
```

## Akku

- **Leerlauf:** eine stille TCP-Verbindung. Der Fernseher schreibt nur bei Start und Stopp der
  Wiedergabe und alle 10 Minuten ein Lebenszeichen. Dazwischen schläft das Handy.
- **Zeitgeber:** `sleep` und `read -t` laufen auf der monotonen Uhr und stehen im Tiefschlaf
  still. Wiederholversuche kosten dann nichts.
- **Kein WLAN:** es wird keine Verbindung versucht.
- **TV aus:** ein einzelner Ping statt eines langen TCP-Verbindungsversuchs.
- **Ressourcen:** ein BusyBox-Shell-Skript mit etwa 1 MB RAM, kein Java-Prozess.

## Einrichtung

1. In Nuvio unter Einstellungen → Wiedergabe → TV-Fernbedienung den Fernseher koppeln und als
   Ziel auswählen. Nuvio schreibt dann `files/tv_remote_watch.conf` (Host, Port, Token).
2. `remote/magisk/build.sh` erzeugt `dist/nuvio-remote-watcher-<version>.zip`.
3. Das ZIP in der Magisk-App installieren und neu starten, oder:
   `adb push … && adb shell su -c "magisk --install-module <zip>"`.

Log und Status: `/data/adb/nuvio-remote/watcher.log` oder in der Magisk-App der Button
„Aktion“ beim Modul.
