# Phase 15 — Production Polish

Status: IMPLEMENTED — local debug/release build verification pending.

## Release configuration

- App version finalized as `1.0.0` / versionCode `1`.
- Release R8 minification enabled.
- Release resource shrinking enabled.
- Retrofit service interfaces and Gson DTOs have explicit keep rules.
- Astronomy Engine API/model package is kept stable for release optimization.

## Privacy and transport hardening

- Android app backup disabled for production.
- Android 11 and older full-backup rules explicitly exclude app files, databases and shared preferences.
- Android 12+ cloud-backup and device-transfer extraction rules explicitly exclude local app state.
- Active/saved coordinates, weather/AQI caches, alert fingerprints and local rendering preferences therefore remain local to the installed app.
- Cleartext HTTP traffic is disabled at the application level.
- Primary Open-Meteo weather, RainViewer and IMD Retrofit clients use HTTPS.

## Runtime reliability inherited from Phase 14

- App live-scene rendering is visibility-aware.
- Hidden Forecast/Wallpaper live previews draw zero frames.
- App and system Live Wallpaper share the same adaptive FPS policy.
- Power Saver / low-battery conditions reduce animation frame rate instead of changing weather/astronomy truth.
- Network/cache refresh stays outside OpenGL frame loops.

## Production UI cleanup

- More screen no longer reports stale Phase 3 status.
- About section describes the current integrated product and displays Version 1.0.0.
- Performance summary reflects adaptive FPS and hidden-preview pausing.
- Widget refresh controls have explicit accessibility descriptions.

## Functional release surface

The production candidate includes:

- Current weather dashboard
- 24-hour + 10-day forecast
- Weather accuracy / 15-minute precipitation cross-check
- Saved cities and current-location flow
- AQI intelligence
- Sun/Moon/astronomy intelligence
- Official India CAP/IMD + Smart Risk alerts
- Radar/map layers
- Compact + forecast home-screen widgets
- Android Live Wallpaper
- Shared weather/astronomy reality state
- Offline/cached fallback and WorkManager refresh

## Final verification checklist

1. `git pull origin main`
2. Android Studio: Clean Project
3. Rebuild Project (debug)
4. Run on a real device
5. Check Home / Forecast / Radar / Wallpaper / More
6. Add both widgets and test refresh/open-app actions
7. Apply Live Wallpaper and confirm launcher remains responsive
8. Toggle Android Power Saver and confirm no crash/freeze
9. Turn network off and confirm cached weather remains usable
10. Build a signed/release candidate and verify R8 build completes
11. Re-test weather fetch, city search, radar, alerts, widgets and astronomy on the release build

Phase 15 does not reopen the frozen Hero visual-art branch. Any future Live Wallpaper changes should be limited to confirmed functional defects unless a separate visual redesign is intentionally started.
