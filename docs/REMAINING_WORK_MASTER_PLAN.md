# Live Weather — Remaining Work Master Plan

Status: PHASE 26 ACTIVE — automated release gate passed; consolidated real-device acceptance + explicit final approval pending.

This roadmap tracks the product work after the Phase 15 production-foundation pass. Feature/source work through Phase 25 is implemented. Phase 26 is the final validation gate.

The project must **not** be called Final, Complete, Production Ready or Release Candidate until the remaining Phase 26 real-device acceptance passes and the user explicitly approves finalization.

## Phase 16 — Settings & Units Pro
Status: IMPLEMENTED — final device regression pending.

Implemented temperature, wind, pressure, precipitation and distance units; Metric/Imperial/custom profiles; Auto/Smooth/Battery performance modes; shared formatting across Home, Forecast, Details, Widgets, Radar labels and Smart Risk.

## Phase 17 — Widget Pro
Status: IMPLEMENTED — final launcher/device regression pending.

Implemented independent per-widget configuration, active-weather/fixed-city sources, appearance modes, responsive compact/wide layouts, manual/background refresh, cache/source isolation and cleanup.

## Phase 18 — Weather Intelligence 2.0
Status: IMPLEMENTED — final device regression pending.

Implemented shared weather intelligence with current precipitation kept separate from forecast probability, rain-soon/later wording, comfort/wind/gust/visibility/pressure interpretation, data freshness and Smart Risk truth guards.

## Phase 19 — Forecast Pro
Status: IMPLEMENTED — final device regression pending.

Implemented interactive 24-hour forecast, synchronized charts/rain-risk timeline, expandable 10-day forecast, detailed weather metrics, Sun/Moon events and truthful live/saved/stale/loading states.

## Phase 20A — Accurate Live Weather Reality Foundation
Status: SOURCE IMPLEMENTATION COMPLETE — final real-device visual acceptance pending.

Implemented shared current-weather-driven OpenGL truth, photoreal cloud atlas, depth rain/drizzle, storm/lightning, Sun/Moon/stars/twilight, fog/haze/snow, gust response, diagnostics, temporal smoothing, renderer fault isolation, bounded EGL recovery and adaptive secondary detail.

## Phase 20B — Radar Pro
Status: SOURCE IMPLEMENTATION COMPLETE — automated Debug/Release gate passed; final real-device Radar acceptance pending.

Implemented RainViewer observed radar truth, Open-Meteo Clouds/Wind/Temperature model layers, legends, timeline/replay, freshness/provenance/fallback states, WebView lifecycle/recovery, tile-health guard, bundled Leaflet runtime and bounded persistent metadata/model fallback.

## Phase 21 — Alerts Pro
Status: SOURCE IMPLEMENTATION COMPLETE — automated Debug/Release gate passed; final real-device notification/alert acceptance pending.

Implemented official-warning truth/freshness, saved/stale/unavailable states, Smart Risk separation, source/severity filters, master/per-source notification controls, Android channel/permission awareness, details and stale-background protections.

## Phase 22 — App UX, Responsive & Accessibility Audit
Status: SOURCE IMPLEMENTATION COMPLETE — automated Debug/Release gate passed; final device/accessibility acceptance pending.

Implemented central 48dp interactive-target policy, narrow/large-text reflow, responsive tablet/phone spacing, Radar sizing, TalkBack semantics, decorative-renderer focus cleanup and consistent loading/live/stale/error presentation.

## Phase 23 — Offline, Cache & Data Reliability 2.0
Status: SOURCE IMPLEMENTATION COMPLETE — automated Debug/Release gate passed; final offline/device acceptance pending.

Implemented shared cache-age/location policy, selected-city identity protection, background old-city race guards, AQI isolation, bounded retry/backoff, persistent bounded Radar fallback, corrupt-cache rejection and More-page Data Reliability diagnostics.

## Phase 24 — Live Wallpaper Quality Backlog
Status: SOURCE IMPLEMENTATION COMPLETE — automated Debug/Release gate passed; final real-device OpenGL/visual acceptance pending.

Implemented richer cloud mass/wrapping/overcast continuity, multi-depth rain variation, wet-glass treatment, rain-gated world reflections, richer world depth and branched/multi-pulse lightning while preserving current-weather truth and shared Hero/Wallpaper renderer parity.

See `PHASE_24_LIVE_WALLPAPER_QUALITY.md` for the visual acceptance matrix.

## Phase 25 — Product Completeness Audit
Status: SOURCE IMPLEMENTATION COMPLETE — automated Debug/Release gate passed; final full real-device product regression pending.

Verified all five primary destinations (Home, Forecast, Radar, Wallpaper, More), navigation/actions, runtime binders, Settings/Data Reliability, product-facing wording/version and absence of an additional unbound roadmap destination.

See `PHASE_25_PRODUCT_COMPLETENESS_AUDIT.md`.

## Phase 26 — Final Release Gate
Status: AUTOMATED RELEASE GATE PASSED — real-device acceptance + explicit user final approval pending.

### Automated gate — PASSED

Authoritative GitHub Actions run:
- run `#9` / ID `31965101892`;
- head commit `b8a6a0f483ed253f49a89ab8cb14e3793916314a`;
- result SUCCESS;
- artifact `phase-26-release-verification`, ID `9268321760`;
- artifact digest `sha256:5e5dadb31ea6f9757274593aaf55669195511cb870572ee7de5e3e32563027be`.

Passed:
- Gradle wrapper/JDK toolchain gate;
- Debug APK build;
- Debug unit tests;
- Release lint;
- Release R8/minification + resource shrinking;
- Release APK;
- Release AAB;
- R8 mapping output;
- APK/AAB/lint output verification;
- configuration-cache-enabled build;
- release verification artifact upload.

### Phase 26 fixes/hardening completed

- Added a permanent GitHub Actions release gate.
- Strict Release lint exposed two API-level theme errors rather than hiding them behind a baseline.
- Moved API-27-only `android:windowLightNavigationBar` behavior out of API-26 base themes into API-27+ day/night qualified themes.
- Protected `*.jks`, `*.keystore`, `keystore.properties` and `signing.properties` from accidental Git commit.
- Kept release/upload signing secrets out of the repository.
- Audited backup/data extraction, cleartext/mixed-content, R8 rules and obvious secret/test-host leftovers.
- Audited Radar OpenStreetMap/Open-Meteo/RainViewer/Leaflet attribution and WebView request identity.
- Modernized CI to `actions/checkout@v7`, `actions/setup-java@v5` and `actions/upload-artifact@v7`.

### Current version checkpoint

- `versionName = 1.0.0`
- `versionCode = 1`

If the same application ID has already been uploaded to Google Play, the final store build must use a versionCode higher than any previously used versionCode. Play Console history is outside the repository and must be checked before publication.

### Remaining consolidated real-device acceptance

1. Home/current-location/saved-city/refresh/weather truth.
2. Forecast hourly/charts/timeline/daily/Sun-Moon/units.
3. Radar local Leaflet, base map, RainViewer, model layers, replay, recenter, weak/offline warning and leave/return lifecycle.
4. Alerts permission, channels, source/severity settings, details, notification tap and stale-source behavior.
5. Compact/wide widgets, active/fixed city, resize, appearance, taps, offline/manual refresh and removal.
6. Live Wallpaper preview/apply and OpenGL parity across available dry/rain/storm/cloud/fog/night scenes plus Auto/Smooth/Battery profiles.
7. Online-to-offline restart, cache ages, selected-city identity, city-switch race and Data Reliability alignment.
8. Narrow screen, large font, TalkBack/focus/touch targets and bottom-navigation restore.
9. Background/foreground/tab switching/WebView/renderer lifecycle smoke test.
10. Final signing/version/provider-terms/Play distribution decision if publishing.

See `PHASE_26_FINAL_RELEASE_GATE.md` for the authoritative detailed gate record.

## Completion rule

Only after the consolidated real-device acceptance passes **and the user explicitly approves finalization** may the project status change to Final / Complete / Production Ready / Release Candidate.
