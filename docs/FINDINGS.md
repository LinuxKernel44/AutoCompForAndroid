# Phase 0 findings (Galaxy S10, Android 12 / One UI 4, API 31)

## Result: the approach works without root

Tested with YouTube playing (AudioTrack, session 13905):

1. **Shizuku**: `ShizukuProvider` + permission request + `bindUserService` work. A user service runs as
   `shell` and can run `dumpsys`.
2. **Session discovery**: `dumpsys audio` lists `AudioPlaybackConfiguration ... u/pid:UID/PID state:started ... sessionId:N`
   for each player (usable to map session -> uid -> package and to know who is playing).
   Caveats: `sessionId` is `0`/`-1` for SoundPool, MediaPlayer-in-system and some AAudio players.
   `dumpsys media.audio_flinger` ("Tracks of which N are active": columns Client pid / Session) is a second source.
3. **Effect on a foreign session**: from our own app process (no special permission beyond
   `MODIFY_AUDIO_SETTINGS`), `DynamicsProcessing(0, session, null)` is created, `hasControl()==true`,
   `setEnabled(true)==SUCCESS`, and AudioFlinger shows the effect *Registered/Enabled* on YouTube's session.
   The effect lives in our process, so the user service is only needed for discovery.

## Gotchas

- `DynamicsProcessing.Config.Builder` with custom band counts (0 or 1 bands per stage) throws
  `IllegalArgumentException: AudioEffect: bad parameter value` in `setEngineArchitecture` on this device.
  The `null` config (default engine) works: 2 channels, 6 PreEq / 6 MBC / 6 PostEq bands, limiter on.
  => Engine architecture is fixed; we configure through the setters (e.g. single-band behaviour = same
  parameters on all 6 MBC bands).
- Default MBC cutoffs: 220 / 542 / 1336 / 3293 / 8115 / 20000 Hz.
- Session ids are only known while the player exists; the tracker must poll/subscribe and release effects of dead sessions.

## Open items for next phases

- Subjective listening check of the effect strength (done in Phase 9 with several apps).
- Apps with `sessionId` 0/-1 (AAudio/SoundPool): check flinger table or fall back to session 0 (global mix) test.
- Re-check behaviour after Shizuku restarts and on emulator API 33+ (default config may differ).
