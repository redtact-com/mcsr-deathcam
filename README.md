# mcsr-deathcam

Death dashcam for [MCSR Ranked](https://mcsrranked.com/) — automatically clips your deaths via the OBS replay buffer, together with death cause / phase / coordinates / IGT / seed metadata and the official ranked replay (.rrf), so you can review and analyse how you die.

死んだ瞬間の数十秒前〜直後を OBS リプレイバッファで自動クリップ化し、死因・フェーズ・座標・IGT・seed・公式リプレイ (.rrf) と一緒にライブラリ管理する MCSR Ranked 用外部ツールです。

## How it works (tournament-legal by design)

- Standalone Java app (fat jar) in the same family as Ninjabrain Bot / Julti / PaceMan Tracker.
- **Never touches the game**: it only reads `latest.log`, SpeedrunIGT state files (`latest_world.json`, `events.log`, `record.json`), world files after saves, and the ranked replay files the mod itself writes — and drives OBS via obs-websocket v5.
- This is exactly the category of external program explicitly permitted by the speedrun.com Minecraft rules (v7, A.8.14.a: reading mod-outputted state "for changing OBS properties" etc.) and it is invisible to the Ranked mod whitelist, which only checks the loaded Fabric mod set.
- No screen reading, no audio listening, no macros, no world resetting (all forbidden by A.10) — and none are needed.

## Features (MVP, work in progress)

- Death detection in real time from the vanilla death message in `latest.log` (always English on the `[Server thread]` line).
- OBS replay buffer control: pre-roll (default 30 s) + post-roll (default 5 s), buffer length configured automatically.
- Metadata per death: cause + killer, run phase (overworld / nether / bastion / fortress / blind / stronghold / end), death coordinates, IGT at death, final IGT/RTA, all three seeds, opponent + Elo.
- Archives the official ranked replay `.rrf` for each death match before the mod rotates it away, so you can rewatch the match in-game from any perspective later.
- Skips intentional hunger-reset deaths (respawn point is a bed/anchor) — configurable.
- Per-world-type recording toggles: ranked (type 2), private room (type 3), and other worlds (practice maps / singleplayer). Ranked and private are the same `mcsrranked #…` world and can only be told apart after the match, so a clip of a type you've turned off is recorded first and then deleted automatically once the API confirms its type.
- SQLite-backed library with a Swing UI.

## Metadata sources

Each death is enriched from whatever is available, in this order:

- **latest.log** — cause, killer, phase, world (always, live).
- **Ranked API** (`api.mcsrranked.com`, no auth) — after the match is indexed (~15 s), fills IGT at death, opponent + Elo, Elo change, result, and seed info: seed id, overworld/bastion type, obsidian tower heights, structure variations. Works for both ranked (type 2) and private/practice (type 3) matches.
- **.rrf** (official ranked replay, real ranked only) — the plaintext numeric seeds and precise death coordinates; the file itself is archived so you can rewatch the match in-game.
- **events.log** — an offline IGT fallback when the API is unreachable.

Notes:
- The numeric seed is only available for real recorded matches (from the `.rrf`); the ranked mod encrypts it in `level.dat` and this tool does not decrypt it. For practice worlds the API seed id + structure breakdown is provided instead.
- Not every `mcsrranked #…` world becomes a recorded match — SeedQueue reset/practice worlds create local folders but no API match, so those deaths keep only the local fields.

## Requirements

- Java 17+
- OBS 28+ with the WebSocket server enabled (Tools → WebSocket Server Settings) and the **Replay Buffer enabled** (Settings → Output). For in-browser playback set the recording format to **Fragmented MP4** (Settings → Output → Recording) — the dashboard warns on `.mkv` clips, which some browsers can't play.
- **Replay buffer length**: set OBS's *Maximum Replay Time* comfortably longer than your pre-roll + post-roll (about +5 s). A saved clip is always the last N seconds up to the moment of saving, so detection/save latency shifts that window forward; if the OBS buffer only just equals pre+post, the lead-in gets clipped. The app reads your OBS buffer length and warns in Settings if it's too short (it never shrinks it — you size it). Auto-starting the replay buffer on connect is a toggle in Settings.
- MCSR Ranked 1.16.1 setup with SpeedrunIGT (the standard ranked install)

## Building

```
./gradlew shadowJar
# → build/libs/mcsr-deathcam-<version>.jar
```

## License

Apache-2.0
