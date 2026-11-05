# AutoComp – Plan to v1.0.0

Real-time audio compressor for Android. The user only hears the processed sound
(effects attached to the audio sessions of other apps), so music/SFX that are too loud
versus quiet voices get evened out.

## Decisions (validated)

| Topic | Choice |
|---|---|
| Interception | Per-session audio effects, privileged via **Shizuku** (no root). Optional root mode is out of scope for v1.0.0 |
| Audience | Personal + advanced users (Shizuku/ADB setup documented) |
| minSdk | 28 (Android 9), target = latest stable. Test device: Galaxy S10 (Android 12) |
| DSP | Android `DynamicsProcessing` (multiband compressor + limiter). Custom C++ DSP = post-1.0 |
| Voice | Plain global compression (no voice-band boost, no ML separation) |
| Stack | Kotlin, Jetpack Compose, Material 3 (dynamic color), package `io.github.linuxkernel44.autocomp` |
| Name | "AutoComp", adaptive vector icon |
| Languages | English + French (UI), README EN + FR |
| License | GPL-3.0 |
| Signing | Local keystore, never committed; built and signed on the dev machine (no CI) |
| Workflow | Autonomous; one milestone per phase; conventional commits on `main`; pre-release tag only if useful, final release `v1.0.0` |

## v1.0.0 feature scope

- Global On/Off + presets (Soft / Normal / Strong / Night)
- Manual compressor controls (threshold, ratio, attack, release, knee, makeup gain)
- Multiband compression (adjustable bands) + output limiter
- Per-app profiles (preset per app, exclusion list)
- Foreground service with notification (On/Off action), live level / gain-reduction meter
- Quick Settings tile, start on boot, battery-optimization exemption request
- EN/FR UI

## Phases

### Phase 0 – Feasibility spike
Prove the core on the S10: Shizuku binding, discovering other apps' audio sessions
without root, attaching `DynamicsProcessing` to them, audible compression.
Exit: a throwaway prototype audibly compresses YouTube/Spotify on the S10; findings
(and fallbacks) written to `docs/FINDINGS.md`.

### Phase 1 – Project scaffold
Gradle (version catalog), Compose + M3, package, adaptive icon, EN/FR resources,
lint/ktlint config, GPL-3.0, README skeleton, debug build running on the S10.

### Phase 2 – Audio engine core
Session tracker (open/close, per-app mapping), effect manager lifecycle,
single-band compressor + limiter, parameters model, unit tests of the parameter mapping.

### Phase 3 – Shizuku integration & onboarding
Permission flow, binder service, state handling (Shizuku missing / not running / denied),
guided setup screens (wireless debugging on Android 11+, ADB on 9–10).

### Phase 4 – Foreground service & app shell
Foreground service hosting the engine, notification with On/Off, DataStore settings,
main screen (switch + preset selector), service robustness (restart, crash recovery).

### Phase 5 – Manual controls & multiband
Advanced screen for compressor parameters, multiband editor, limiter, preset system
(built-ins + user presets), parameter validation.

### Phase 6 – Per-app profiles
Detected apps list (with icons), per-app preset / disable, exclusion list, persistence.

### Phase 7 – Meter & visualizer
Level / gain-reduction readout (Visualizer or effect metering, depending on Phase 0 findings),
efficient Compose rendering.

### Phase 8 – System integration
Quick Settings tile, boot receiver, battery-optimization exemption prompt,
Android 13+ notification permission, Samsung-specific quirks found on the S10.

### Phase 9 – Hardening & QA
Test on S10 (real apps: YouTube, Spotify, Netflix, VLC, games), Bluetooth/headset
route changes, battery/CPU profiling, emulator check on newer API, bug fixing,
FR/EN review, full README (EN + FR) with screenshots, privacy statement (no network, no data).

### Phase 10 – Release v1.0.0
R8/shrink, version 1.0.0, local keystore generation (+ backup reminder), signed
release APK verified with `apksigner verify`, CHANGELOG, git tag `v1.0.0`,
GitHub Release with the APK and checksum.

## Known risks

- Some apps do not expose session IDs or use offloaded/low-latency paths (AAudio MMAP);
  coverage may be partial. Documented in README.
- OEM (Samsung) effect handling may differ from AOSP; validated on the S10.
- Shizuku must be running (restart needed after reboot on non-rooted Android < 11).

## Status (v1.0.0)

All phases 0–10 completed. Deviations from the initial plan:
- Phase 7: the meter shows **output peak level** (Visualizer, RECORD_AUDIO granted through Shizuku) rather than gain reduction,
  because `DynamicsProcessing` does not report gain reduction.
- Phase 9: validated on the S10 with YouTube; the listening check with other apps (Spotify, Netflix, VLC, games),
  Bluetooth route changes and long-run battery profiling remain to be done by ear/over time.
