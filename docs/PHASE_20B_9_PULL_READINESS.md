# Phase 20B.9 — Radar Pro Source Freeze + Pull Readiness

Status: SOURCE FREEZE COMPLETE — READY FOR FIRST LOCAL PULL / SYNC / BUILD.

This checkpoint closes the source-implementation pass for Phase 20B. It does not claim device acceptance or release readiness.

## Final preflight covered

- Phase 20B.1 observed-radar truth boundary and safe RainViewer host/path/timestamp filtering.
- Phase 20B.2 continuous Open-Meteo model-cloud field with no per-point cloud circles/boxes.
- Phase 20B.3 layer controls, selected state and truthful active legend.
- Phase 20B.4 observed timeline, timestamp-preserving history selection and reusable radar tile layer playback.
- Phase 20B.5 freshness, network/cache/fallback provenance and non-destructive refresh.
- Phase 20B.6 lazy WebView lifecycle, bounded renderer recovery and local failure shell.
- Phase 20B.7 aggregate OSM/RainViewer tile-health guard and blank-map vs no-rain distinction.
- Phase 20B.8 bundled Leaflet 1.9.4 build-time runtime and removal of runtime unpkg dependency.

## Step 20B.9 fixes / hardening

- Rechecked the Radar HTML relative Leaflet paths against the Gradle generated-asset destination. Both resolve under `radar/vendor/leaflet/`.
- Confirmed `AndroidManifest.xml` still provides INTERNET permission for provider-backed OSM/RainViewer/Open-Meteo requests.
- Confirmed Radar WebView lazy initialization and hide/show lifecycle remain intact.
- Confirmed the temporary `RadarLeafletRuntime` Java bridge introduced during exploration was removed and has no remaining source reference.
- Confirmed no `unpkg.com` Leaflet runtime reference remains in the repository search.
- Hardened the custom Gradle dependency declaration from dynamic invocation form to explicit `add(radarLeafletRuntime.name, ...)` form to reduce Kotlin DSL accessor ambiguity.
- Declared the WebJar configuration as an explicit input and generated asset directory as an explicit output of `prepareRadarLeafletRuntime`.
- The generation task still fails clearly if expected `leaflet.js` or `leaflet.css` assets are missing.
- The `preBuild` dependency remains the build gate that prepares Leaflet assets before Android packaging.
- No Radar truth, current-weather truth, model/radar distinction, timeline frame semantics, or provider source was changed in this step.

## Source-freeze conclusions

- No known source-level blocker remains that should prevent attempting Gradle Sync / build.
- This is a static source-readiness conclusion, not a successful build result.
- GitHub Actions currently does not provide a build result for this checkpoint.
- Phase 20B visual/device acceptance is still pending until local build and real-phone testing are completed.

## First local verification after pull

1. Pull `main` in Android Studio.
2. Allow Gradle to resolve `org.webjars.npm:leaflet:1.9.4` from Maven Central.
3. Run Gradle Sync.
4. Run a debug build.
5. If build succeeds, install on the real phone.
6. Open Radar and verify local Leaflet engine starts without `unpkg.com`.
7. Test Rain Radar, Model Clouds, Wind and Temperature.
8. Test timeline slider, Replay/Pause and latest observed frame.
9. Test Refresh and Recenter.
10. Switch away from Radar and back repeatedly; confirm no blank map/reset/crash.
11. Temporarily test weak/offline network behavior; confirm tile-delivery warning does not mean `no rain`.
12. Confirm Cloud layer is smooth/continuous with no old circles/boxes.

## Acceptance boundary

Phase 20B source implementation is frozen at this checkpoint. If the first local Sync/build reports an error, fix that error before starting Phase 21. If build/device verification succeeds, Phase 20B can be marked device-accepted and development can proceed to Phase 21 — Alerts Pro.
