# Phase 26 — Final Release Gate

Status: AUTOMATED RELEASE GATE PASSED — real-device acceptance + explicit user final approval pending.

The project is not yet labelled Final, Complete, Production Ready or Release Candidate. Phase 26 is the final validation gate, and its automated/source portion has passed. The remaining gate is consolidated real-device acceptance plus explicit user approval.

## Authoritative automated gate

GitHub Actions workflow: `Phase 26 Release Gate`

Authoritative run:
- Run: `#9`
- Run ID: `31965101892`
- Head commit: `b8a6a0f483ed253f49a89ab8cb14e3793916314a`
- Result: SUCCESS
- Artifact: `phase-26-release-verification`
- Artifact ID: `9268321760`
- Artifact digest: `sha256:5e5dadb31ea6f9757274593aaf55669195511cb870572ee7de5e3e32563027be`

The automated gate successfully ran:
- Gradle wrapper verification;
- Debug APK build;
- Debug unit tests;
- Release lint;
- minified/shrunk Release APK build through R8;
- Release AAB build;
- Release APK/AAB output existence checks;
- R8 `mapping.txt` existence check;
- Release lint-report existence check;
- verification artifact upload.

Gradle reported `BUILD SUCCESSFUL` and stored the configuration-cache entry.

## Release lint blocker found and fixed

The first strict Release lint run correctly failed instead of being hidden behind a lint baseline.

Both errors were the same API-level problem:
- `android:windowLightNavigationBar` requires API 27;
- app `minSdk` is 26;
- the attribute existed in both normal and night API-26-compatible theme resources.

Fix:
- removed the API-27-only item from base `values/themes.xml` and `values-night/themes.xml`;
- added API-qualified `values-v27/themes.xml` and `values-night-v27/themes.xml` resources retaining the intended navigation-bar behavior on API 27+.

The subsequent Release lint gate passed.

## Build toolchain

Current project line:
- Android Gradle Plugin: 9.3.1
- Gradle: 9.5.0
- CI JDK: 17
- compile SDK: Android API 36 line
- target SDK: 36
- min SDK: 26
- Java source/target compatibility: 11

Release build configuration keeps:
- R8/minification enabled;
- resource shrinking enabled;
- optimized default ProGuard rules plus project rules;
- Retrofit/Gson DTO/API keep coverage;
- Astronomy Engine keep coverage.

The CI workflow uses current non-deprecated major lines verified during Phase 26:
- `actions/checkout@v7`
- `actions/setup-java@v5`
- `actions/upload-artifact@v7`

Docs-only commits do not retrigger the expensive release gate.

## Privacy / backup / transport hardening

Verified:
- `android:allowBackup="false"`;
- legacy backup rules exclude local file/database/shared-preference/external app state;
- Android 12+ data-extraction rules exclude the same state from cloud backup and device transfer;
- `android:usesCleartextTraffic="false"`;
- Radar WebView blocks mixed content;
- no obvious committed localhost/test-host, cleartext HTTP URL or embedded API-key/password/token pattern surfaced in the Phase 26 repository scan.

## Signing-secret protection

Added release-signing exclusions to `.gitignore`:
- `*.jks`
- `*.keystore`
- `keystore.properties`
- `signing.properties`

No upload/release keystore was added to GitHub. Store signing/upload-key handling remains an explicit release-distribution step rather than a repository secret.

## Version checkpoint

Current app configuration:
- `versionName = 1.0.0`
- `versionCode = 1`

This is a valid first-release version line. If the same `applicationId` has already had a build uploaded/published in Google Play, the next store upload must use a versionCode greater than the highest versionCode already used there. Play Console history is not available in this repository audit, so final store-upload versionCode must be confirmed against Play Console before publication.

## Radar / third-party release hygiene

Verified in the active Radar implementation:
- OpenStreetMap attribution is visible on the map;
- Open-Meteo model-overlay attribution is visible;
- RainViewer radar attribution is visible;
- Leaflet attribution control remains enabled;
- Radar WebView appends `LiveWeather/1.0` to its User-Agent;
- OSM/RainViewer tiles are viewport/network backed and are not bulk-prefetched or falsely represented as persistent offline tiles;
- mixed content is disabled.

Important distribution boundary: RainViewer public API suitability must be checked against the intended distribution/use scale. A broad commercial/high-volume release may require appropriate commercial terms or a provider change; the app does not silently assume unlimited commercial rights.

## Current verification boundary

Automated/source release checks are PASSED.

Still required on a real Android phone before final approval:

1. **Home + location** — fresh current location, saved city switching, refresh, correct weather, no wrong-city flash.
2. **Forecast** — 24-hour selection, charts/timeline synchronization, daily expansion, Sun/Moon values, units repaint.
3. **Radar** — local Leaflet startup, OSM base map, RainViewer observed frames, Clouds/Wind/Temperature layers, replay/timeline, recenter/refresh, leave/return lifecycle, weak/offline truthful tile warning.
4. **Alerts** — Android notification permission, master/per-source settings, channel blocking, severity/source filters, alert details, notification tap navigation, stale-source wording.
5. **Widgets** — add/configure compact and wide widgets, active vs fixed city, resize, appearance, taps, manual/offline refresh, removal cleanup.
6. **Live Wallpaper / OpenGL** — preview and Android applied wallpaper, dry/drizzle/rain/storm/lightning/cloud/fog/night scenes as available, Hero/wallpaper parity, Auto/Smooth/Battery behavior, no unexpected renderer crash.
7. **Offline/cache** — online-to-offline restart, saved age labels, selected-city identity, app/widget/wallpaper alignment, Data Reliability diagnostics, city-change race protection.
8. **UX/accessibility** — narrow display, large font, TalkBack/focus/touch-target smoke pass, bottom-navigation restore.
9. **Lifecycle** — background/foreground, tab switching, Radar/WebView recovery and wallpaper visibility behavior without crashes.
10. **Release distribution** — confirm final Play versionCode/signing/upload-key/provider-terms decision if publishing to Google Play.

## Final completion rule

Only after the consolidated real-device checks pass and the user explicitly approves finalization may documentation/status be changed to Final / Complete / Production Ready / Release Candidate.
