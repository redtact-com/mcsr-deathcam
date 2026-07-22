# mcsr-deathcam

Death dashcam for [MCSR Ranked](https://mcsrranked.com/) — automatically clips your deaths via the OBS replay buffer, together with death cause / phase / coordinates / IGT / seed metadata and the official ranked replay (.rrf), so you can review and analyse how you die.

死んだ瞬間の数十秒前〜直後を OBS リプレイバッファで自動クリップ化し、死因・フェーズ・座標・IGT・seed・公式リプレイ (.rrf) と一緒にライブラリ管理する MCSR Ranked 用外部ツールです。

## How it works (tournament-legal by design)

- Standalone Java app (fat jar) in the same family as Ninjabrain Bot / Julti / PaceMan Tracker.
- **Never touches the game**: it reads files and drives OBS via obs-websocket v5 — nothing is injected.
- **During a run it reads only files allowed to be read live**: the world's statistics file (`stats/<uuid>.json`, whose `minecraft:deaths` counter drives detection — statistics reads are explicitly permitted by speedrun.com rule A.3.10.a) and SpeedrunIGT's mod outputs (`latest_world.json`, `events.log`; A.8.14.a). Vanilla log/world files — `latest.log` (for the death cause) and `level.dat` (for hunger-reset detection) — are read **only after the match ends**, which is outside A.3.10's before/during-a-run scope.
- This is exactly the category of external program permitted by the speedrun.com Minecraft rules (v7) and it is invisible to the Ranked mod whitelist, which only checks the loaded Fabric mod set.
- No screen reading, no audio listening, no macros, no world resetting (all forbidden by A.10) — and none are needed.

## Features (MVP, work in progress)

- Death detection in real time from the world's **statistics file** (`minecraft:deaths` in `stats/<uuid>.json`): a death triggers a game save that flushes the stat, so it is picked up within ~1 s. The death **cause** (only present in the vanilla death message) is read from `latest.log` after the match and paired to each detected death.
- OBS replay buffer control: pre-roll (default 30 s) + post-roll (default 5 s), buffer length configured automatically.
- Metadata per death: cause + killer, run phase (overworld / nether / bastion / fortress / blind / stronghold / end), death coordinates, IGT at death, final IGT/RTA, all three seeds, seed type / bastion type / result (self-focused: opponent name and Elo are intentionally not stored).
- Archives the official ranked replay `.rrf` for each death match before the mod rotates it away, so you can rewatch the match in-game from any perspective later.
- Skips intentional hunger-reset deaths (bed/anchor respawn point, detected from `level.dat` after the match) — configurable. Every death is clipped on detection; a hunger-reset clip is removed once the post-match check confirms it.
- Per-world-type recording toggles: ranked (type 2), private room (type 3), and other worlds (practice maps / singleplayer). Ranked and private are the same `mcsrranked #…` world and can only be told apart after the match, so a clip of a type you've turned off is recorded first and then deleted automatically once the API confirms its type.
- **Clip-only resolution** (downscale-only): record clips at a lower resolution than your stream to save space. The app sets OBS's *recording* rescale (Advanced output mode), which affects the replay buffer only — your streaming resolution is untouched, and it never upscales past your base canvas. Requirements: OBS in **Advanced output mode**, the recording **Encoder set to a real encoder (not "Use stream encoder")**, and the change applied while outputs are stopped (the app bounces the replay buffer for you; don't apply it mid-stream). The settings dialog shows OBS's actual state (green ✓ once applied).
- **Storage cap** (容量削減): optionally keep the clips folder under a size limit (GB). When exceeded, the oldest clip **videos** are deleted while their death records — cause, phase, IGT, seed, coordinates and any `.rrf` — are kept, so your statistics survive.
- SQLite-backed library with a Swing UI.
- Dashboard UI in **Japanese or English** — toggle in the header (defaults to Japanese; the choice is remembered). Cause and phase names are localized.

## Metadata sources

Each death is enriched from whatever is available, in this order:

- **statistics file** (`stats/<uuid>.json`) — real-time death detection during the run, from the `minecraft:deaths` counter (phase comes from `events.log`, world from the folder name).
- **latest.log** — death cause + killer, read **after the match** and paired to the detected deaths in order.
- **Ranked API** (`api.mcsrranked.com`, no auth) — after the match is indexed (~15 s), fills IGT at death, result, and seed info: seed id, overworld/bastion type, obsidian tower heights, structure variations, plus a `projectelo.timeline.death` cross-check of death count/IGT. Works for both ranked (type 2) and private/practice (type 3) matches. (Opponent name and Elo are intentionally not stored.)
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

## REST API (OpenAPI / Swagger)

The embedded dashboard server exposes a small REST API, described by an OpenAPI 3 document:

- `GET /api/docs` — **Swagger UI**, bundled in the jar (no CDN), light theme. The "Select a
  definition" dropdown switches between **this app's API** and the **official MCSR Ranked API**.
- `GET /api/openapi.yaml` — this app's OpenAPI 3 spec
- `GET /api/mcsrranked.yaml` — the vendored official MCSR Ranked spec
- `GET /api/records` — all death records (the `DeathRecord` schema)
- `GET /media/clip/{id}` — stream a clip (supports HTTP Range)

The external MCSR Ranked API this app consumes is pinned to its official spec at
[`openapi/mcsrranked.yaml`](openapi/mcsrranked.yaml) (from `MCSR-Ranked/api-docs`); a contract test
checks the fields `RankedApiClient` reads are still present in it.

## Getting the jar

Every push builds the fat jar in CI and uploads it as an artifact — grab the latest from the
repo's **Actions** tab → newest run → **Artifacts → `mcsr-deathcam-jar`**. Or build locally:

```
./gradlew shadowJar
# → build/libs/mcsr-deathcam-<version>.jar
```

## License

Apache-2.0
