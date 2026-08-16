# Live Weather — Pre-Release Status

Current product version: 1.0.0

Status: PHASE 26 ACTIVE — automated release gate PASSED; real-device acceptance + explicit user final approval pending.

## Important boundary

The project is **not yet** labelled Final, Complete, Production Ready or Release Candidate.

The full source/product roadmap through Phase 25 is implemented. Phase 26 has now passed its automated build/release gate, including Debug, unit-test, Release lint, R8/minification, resource shrinking, Release APK, Release AAB and output verification. What remains is the consolidated real-device acceptance matrix and explicit user approval.

## Automated release gate — PASS

Authoritative GitHub Actions run:
- Workflow: `Phase 26 Release Gate`
- Run: `#9`
- Run ID: `31965101892`
- Head commit: `b8a6a0f483ed253f49a89ab8cb14e3793916314a`
- Result: SUCCESS
- Artifact ID: `9268321760`
- Artifact digest: `sha256:5e5dadb31ea6f9757274593aaf55669195511cb870572ee7de5e3e32563027be`

Passed checks:
- Debug APK;
- Debug unit tests;
- Release lint;
- minified/shrunk Release APK via R8;
- Release AAB;
- R8 mapping output;
- release lint report;
- APK/AAB output verification;
- configuration-cache-enabled build;
- release verification artifact upload.

## Phase 26 fixes/hardening

- Strict Release lint found two genuine API-level errors for `android:windowLightNavigationBar` on minSdk 26.
- The API-27-only theme item was moved out of base day/night resources into API-27+ qualified day/night theme resources.
- Release signing material is protected by `.gitignore` patterns for JKS/keystore/signing property files.
- No upload/release keystore is stored in GitHub.
- CI release automation uses current major lines: checkout v7, setup-java v5, upload-artifact v7.
- Backup/data-extraction/cleartext restrictions remain enabled.
- Radar attribution/User-Agent/mixed-content behavior was audited.

## Current version checkpoint

- `versionName = 1.0.0`
- `versionCode = 1`

Before a Google Play upload, compare `versionCode` with any prior Play Console build for the same application ID. If a prior build exists, use a value greater than the highest versionCode already used.

## Remaining real-device acceptance

One consolidated phone pass is still required:
1. Home/current location/saved city/refresh.
2. Forecast hourly/charts/daily/Sun-Moon/units.
3. Radar local Leaflet/base tiles/RainViewer/model layers/replay/recenter/weak-offline warning/lifecycle.
4. Alerts permission/channels/filters/details/notification navigation/stale states.
5. Compact + wide widgets, active/fixed city, resize/taps/offline refresh/removal.
6. Live Wallpaper preview/apply and real OpenGL visual parity across available dry/rain/storm/cloud/fog/night scenes and Auto/Smooth/Battery modes.
7. Offline restart/cache age/city-switch race/Data Reliability alignment.
8. Narrow screen/large font/TalkBack/touch/navigation restore.
9. Background/foreground/tab switching/renderer lifecycle smoke test.
10. Final store signing/version/provider-terms decision if publishing.

## Provider/distribution note

OpenStreetMap, Open-Meteo, RainViewer and Leaflet attribution is present in the Radar experience. RainViewer public API use must still match the intended distribution/use scale; broad commercial/high-volume release may require suitable commercial terms or a provider change.

## Completion rule

Only after the consolidated real-device acceptance passes **and the user explicitly approves finalization** may this status change to Final / Complete / Production Ready / Release Candidate.

See `PHASE_26_FINAL_RELEASE_GATE.md` for the full authoritative gate record.
