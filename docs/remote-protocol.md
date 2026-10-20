# Nuvio Remote – Protokoll v1 (Entwurf)

Handy ↔ TV über WebSocket im LAN. Der TV (webOS-JS-Service) ist Server, das Handy Client.

- Endpunkt: `ws://<tv-ip>:<port>/ws` (Port aus mDNS `_nuvio-remote._tcp` oder Default 47800)
- Jede Nachricht ist ein JSON-Objekt mit `v` (Protokollversion, aktuell `1`) und `type`.
- Optional `id`: vom Handy vergebene Request-ID; der TV wiederholt sie in der direkten Antwort.
- Unbekannte Felder werden ignoriert, unbekannte `type`s mit `error{code:"unknown_type"}` beantwortet.
  Neue optionale Felder sind kein Breaking Change; alles andere erhöht `v`.

Kotlin-Typen: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/tvremote/protocol/RemoteProtocol.kt`.
Die TV-Seite implementiert dieselben Typen in JS.

## Ablauf

```
Handy                                   TV
  │── hello {client, token?} ──────────▶│
  │◀──────────── welcome {tv, paired} ──│
  │   (paired=false)                    │
  │── pair.begin ──────────────────────▶│  TV zeigt 6-stelligen Code
  │── pair.confirm {code} ─────────────▶│
  │◀──────── pair.ok {token} / error ───│  Token speichert das Handy pro TV
  │◀──────────────── state {playback} ──│  bei jeder Änderung + alle 5 s als Heartbeat
  │── play {request} ──────────────────▶│
  │── cmd {action, …} ─────────────────▶│
  │── volume {action} ─────────────────▶│
  │◀──────────── volume.state {…} ──────│
```

Solange `paired=false` ist, akzeptiert der TV nur `hello`, `pair.begin`, `pair.confirm` und `ping`.
Alles andere → `error{code:"not_paired"}`. Nach 5 falschen Codes wird ein neuer Code erzeugt.

## Handy → TV

| type | Felder | Zweck |
|---|---|---|
| `hello` | `client{id,name,platform,appVersion}`, `token?` | Verbindungsaufbau |
| `pair.begin` | – | TV soll Code anzeigen |
| `pair.confirm` | `code` | Code bestätigen |
| `play` | `request` (siehe unten) | Wiedergabe auf dem TV starten |
| `cmd` | `action`, `positionMs?`, `deltaMs?`, `trackId?`, `delayMs?` | Player-Befehl |
| `volume` | `action` = `up`/`down`/`mute`/`unmute` | TV-Lautstärke |
| `state.get` | – | aktuellen Zustand anfordern |
| `ping` | `t` | Latenz / Keepalive |

`cmd.action`: `play`, `pause`, `toggle`, `stop`, `seek` (`positionMs`), `seekBy` (`deltaMs`),
`next`, `audio` (`trackId`), `subtitle` (`trackId`, `null` = aus), `subtitleDelay` (`delayMs`),
`skip` (aktuelles Segment aus `PlaybackState.skip` überspringen, wie der Skip-Button am TV).

### PlayRequest

```json
{
  "media": {
    "type": "series", "metaId": "tt0944947", "metaType": "series",
    "videoId": "tt0944947:1:1", "season": 1, "episode": 1,
    "title": "Game of Thrones", "episodeTitle": "Winter Is Coming",
    "poster": "https://…", "background": "https://…", "logo": "https://…"
  },
  "stream": {
    "url": "https://…", "headers": {"User-Agent": "…"}, "responseHeaders": {},
    "name": "1080p", "description": "…", "addonName": "…", "addonId": "…",
    "filename": "…", "videoSize": 123, "bingeGroup": "…", "streamType": "…",
    "subtitles": [{"id": "ext-0", "url": "https://…", "lang": "ger", "label": "Deutsch", "headers": {}}]
  },
  "startPositionMs": 0
}
```

Der TV braucht dafür **keine** passenden Addons: Die Stream-URL ist schon aufgelöst.
Torrent-/localhost-URLs vom Handy sind nicht übertragbar; das Handy verweigert dann das Senden.

**Einschalten:** Kennt das Handy `tv.mac`, schickt es bei jedem `play` Wake-on-LAN (UDP :9, Broadcast
und direkt an die TV-IP) und wartet bis 60 s auf die Verbindung. Ist der TV im Standby, hält der TV
das `play`, bis er an ist, und startet dann Nuvio damit. Am TV muss „Über WLAN/LAN einschalten“ an sein.

## TV → Handy

| type | Felder |
|---|---|
| `welcome` | `tv{id,name,model?,platform,mac?}`, `paired`, `protocol` |
| `pair.ok` | `token` |
| `state` | `playback` (siehe unten) |
| `volume.state` | `level?`, `muted` |
| `error` | `code`, `message?` |
| `pong` | `t`, `serverTime` |

### PlaybackState

```json
{
  "status": "playing",
  "positionMs": 812000, "durationMs": 3420000, "at": 1760900000000, "rate": 1.0,
  "media": { …wie PlayRequest.media… },
  "audioTracks":    [{"id": "a1", "label": "English 5.1", "lang": "eng", "selected": true}],
  "subtitleTracks": [{"id": "s3", "label": "Deutsch", "lang": "ger", "source": "addon", "selected": true}],
  "subtitleDelayMs": 0,
  "hasNext": true,
  "skip": {"kind": "intro", "postCredits": false, "endMs": 95000},
  "error": null
}
```

`skip` ist gesetzt, solange am TV ein überspringbares Segment läuft (auch wenn der TV-Button
schon ausgeblendet ist), sonst `null`. `kind`: `intro`, `recap`, `outro`, `credits`.
`postCredits = true` heißt: der Sprung landet in einer Post-Credits-Szene.

`status`: `idle`, `loading`, `playing`, `paused`, `buffering`, `ended`, `error`.
`at` ist die TV-Uhrzeit beim Messen von `positionMs`. Das Handy interpoliert die Position bei
`playing` selbst und braucht deshalb keine sekündlichen Updates.

## Watch-Progress

Der **TV** speichert Fortschritt und scrobbelt (Trakt/Simkl) wie bei normaler Wiedergabe, weil
er mit demselben Nuvio-Konto synchronisiert. Das Handy schreibt für TV-Wiedergaben **nichts**,
damit nichts doppelt gezählt wird. *(Offen: Was passiert, wenn der TV nicht eingeloggt ist?
Dann könnte das Handy beim `stop` den letzten Stand über `recordExternalPlaybackProgress` speichern.)*
