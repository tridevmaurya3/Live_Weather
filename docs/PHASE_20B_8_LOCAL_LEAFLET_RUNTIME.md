# Phase 20B.8 — Local Leaflet Runtime + First-Load Offline Engine

Status: SOURCE IMPLEMENTED — Gradle and real-device verification pending.

## Goal

Remove Radar's runtime Leaflet CDN dependency without changing observed-radar truth, model-field truth, playback behavior, tile-health logic, or provider tile sources.

## Implemented

- Added a dedicated Gradle configuration for Leaflet 1.9.4 as a build-time WebJar input.
- Added `prepareRadarLeafletRuntime`, which extracts only the WebJar `dist/` tree into generated Android assets under `radar/vendor/leaflet/`.
- The generated asset directory is wired into the app `main` asset source set and the extraction task runs before `preBuild`.
- Build validation fails clearly if the expected generated `leaflet.js` or `leaflet.css` files are missing, preventing a silent empty Radar map caused by an unexpected WebJar layout change.
- `radar_map.html` no longer references `unpkg.com` for Leaflet JavaScript or CSS.
- Leaflet JavaScript and CSS are loaded from relative APK asset paths: `vendor/leaflet/leaflet.js` and `vendor/leaflet/leaflet.css`.
- Leaflet's `dist/images` assets are extracted with the rest of the `dist/` tree, so relative CSS image references remain local as well.
- The local-engine startup timeout is reduced because engine loading no longer depends on the network.
- The Radar shell now describes a bundled-runtime failure accurately and does not tell the user to reconnect merely to start Leaflet.
- `RadarApp.getEngineInfo()` exposes `runtime=bundled` and the Leaflet version for lightweight diagnostics.
- Existing OSM base tiles and RainViewer observed radar tiles remain provider/network-backed. This step does not claim persistent offline map/radar tile availability.
- Open-Meteo field data remains model context only; no future radar frame or current-weather evidence is fabricated.
- Phase 20B.7 tile-health aggregation and the existing Radar lifecycle/playback bridge remain intact.

## Offline boundary

After a successful app build, the Leaflet map engine itself is packaged with the app and does not require a CDN on first Radar open. The visible base map and observed radar imagery can still need network access unless WebView/provider cache already contains the required tiles. Persistent offline tile caching remains a later data-reliability concern.

## Verification boundary

- Changes are on `main` only.
- No local Android Studio / Gradle build was run in this step.
- The generated WebJar assets have not yet been inspected inside a built APK.
- No real-device first-open-offline Radar test has been run yet.
- Phase 20B is not declared complete.
