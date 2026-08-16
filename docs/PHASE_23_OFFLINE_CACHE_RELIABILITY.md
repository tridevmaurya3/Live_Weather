# Phase 23 — Offline, Cache & Data Reliability 2.0

Status: SOURCE IMPLEMENTATION COMPLETE — build/device/offline verification pending.

## Product contract

Phase 23 improves reliability without changing meteorological truth:
- saved data may remain visible offline, but age/stale state must be explicit;
- a cached location must never masquerade as a different selected city;
- background refresh must never move the active app/wallpaper location back to an older location after the user switches cities;
- fixed-city widget snapshots must remain isolated from the active app/wallpaper pointer;
- failed refresh keeps usable saved data instead of blanking the UI;
- retries are bounded/backed off rather than tight-looped;
- Radar persistent fallback is metadata/model context only; OSM/RainViewer image tiles remain provider/network backed.

## 23.1 — Shared cache-age policy

Added `DataReliabilityPolicy` with shared cache-age semantics:
- weather recent: up to 45 minutes;
- weather aging: 45 minutes to 3 hours;
- weather stale: 3 to 12 hours;
- weather very stale: over 12 hours;
- AQI recent: up to 45 minutes;
- AQI aging: 45 minutes to 3 hours;
- AQI stale: over 3 hours;
- bounded background retry helper: maximum three attempts for retrying one work instance.

The policy also owns:
- human-readable cache age labels;
- saved-weather fallback wording;
- cross-component same-location matching at the existing app-level coordinate tolerance;
- a local reliability diagnostics report.

## 23.2 — Selected-city / cache identity hardening

`WeatherViewModel` startup now checks the persisted selected city before publishing saved weather.

Behavior:
- selected saved city -> load only that city's per-location weather snapshot;
- selected city with no snapshot -> show no unrelated weather and wait for the selected-city live refresh;
- device/current-location mode -> continue using the active weather cache as offline fallback.

This removes the old possibility of briefly showing the previous active cache while a different persisted selected city is being restored.

During refresh:
- a target-location cache is used only for that target location;
- refresh failure can fall back only to weather already associated with the requested location;
- fallback message includes age/staleness wording.

## 23.3 — Active-pointer race protection

Added `WeatherCache.saveIfStillActive(...)`.

Background work that began from the active cache now re-checks active identity at write time:
- if the same location is still active, refreshed weather remains the active app/wallpaper snapshot;
- if the user changed active location while the request was running, the old response is stored only as a per-location snapshot and cannot move the active pointer back.

Applied to:
- `WallpaperWeatherRefreshWorker`;
- follow-active `WidgetRefreshWorker`.

Fixed-city widgets continue to use `saveSnapshot(...)` and never move the active pointer.

## 23.4 — AQI location isolation

`AirQualityCache` now supports `saveSnapshot(...)` and clears malformed per-location entries.

Wallpaper background refresh mirrors weather identity behavior:
- AQI refresh for the still-active location may update the generic last-AQI pointer;
- AQI arriving for a location that stopped being active is stored as an isolated snapshot.

`AirQualityViewModel` continues to load exact-location cache only and now includes saved-data age in refresh/failure wording.

## 23.5 — Offline foreground behavior

Weather/AQI foreground behavior now keeps exact-location saved data when live refresh fails.

Weather startup and error states expose age-aware saved-data wording instead of treating every cache as equally fresh.

No new network polling loop was added.

## 23.6 — Background retry/backoff

Wallpaper periodic weather refresh now has explicit exponential WorkManager backoff starting at 30 seconds.

Wallpaper network failures:
- retry only within the bounded Phase 23 attempt limit;
- after the retry budget is exhausted, return success and keep existing saved data until the next normal periodic window rather than repeatedly hammering the provider.

Manual widget refresh now also uses explicit exponential 30-second backoff and the same bounded retry budget.

Network constraints remain `CONNECTED` for WorkManager jobs that require provider access.

## 23.7 — Radar persistent fallback after process restart

Added `RadarPersistentCache` for:
- validated RainViewer metadata;
- Open-Meteo sampled cloud/wind/temperature model field.

`RadarRepository` writes successful network responses to this persistent cache and can recover them after process restart when a provider/network refresh fails.

Safety bounds:
- persistent observed-radar metadata fallback: maximum 6 hours;
- persistent model-field fallback: maximum 3 hours;
- RainViewer host/frame paths are still re-validated through `RadarObservedDataPolicy` before use;
- UI delayed/stale labels remain authoritative;
- no future radar frames are fabricated.

Important limitation:
- Leaflet runtime is local from Phase 20B;
- Radar metadata/model context can now persist;
- OpenStreetMap and RainViewer image tiles are still external/provider-backed and are NOT claimed as persistent offline tiles.

## 23.8 — Data Reliability diagnostics

Added a `Data Reliability` card to the More page.

It reports locally without starting a network request:
- weather cache freshness + exact age;
- active cache coordinates;
- AQI cache freshness + age for the active weather location;
- selected-city vs active-cache alignment;
- persisted Radar metadata age;
- persisted Radar model-field age for the active location;
- app + Live Wallpaper shared-cache contract;
- fixed-city widget snapshot isolation;
- explicit Radar tile offline limitation.

`Refresh diagnostics` re-reads local state only.

## 23.9 — Cross-surface consistency audit

Confirmed/strengthened:
- app live weather success writes the active cache;
- Live Wallpaper reads the same active cache;
- follow-active widgets read the same active cache;
- fixed-city widgets read/write per-location snapshots;
- background old-location responses cannot overwrite a newly selected active location;
- AQI consumers use exact weather coordinates when rendering app/wallpaper data;
- Alerts retain their separate Phase 21 official/cache freshness truth;
- Radar model/observed truth boundaries remain separate.

## Static source preflight

Checked source contracts:
- `WeatherCache.saveIfStillActive(...)` callers match its boolean return contract;
- `AirQualityCache.saveSnapshot(...)` is available to the wallpaper worker;
- `DataReliabilityPolicy.sameLocation(...)` is shared by Weather/AQI ViewModels;
- `LiveWeatherApplication` installs `DataReliabilityBinder` without replacing existing Settings/Forecast/UX binders;
- `RadarPersistentCache` Gson types match the existing RainViewer/RadarField DTO shapes;
- `RadarRepository` still exposes the existing `DeliverySource` enum values expected by `RadarUiState` and `Phase9Renderer`;
- WorkManager backoff APIs used are available through the existing WorkManager dependency/minSdk setup;
- no Phase 24 renderer-quality changes were introduced.

## Acceptance gate still required

After pull:
1. Gradle Sync and Debug build.
2. Start app online, populate weather/AQI/Radar caches, restart app offline.
3. Verify selected saved city never flashes weather from another cached city.
4. Switch city while a background/manual refresh is in flight; confirm active location does not revert.
5. Verify Home/Forecast retain exact-location saved weather with age/stale wording offline.
6. Verify AQI fallback remains tied to the active weather coordinates.
7. Verify follow-active widget and Live Wallpaper remain aligned with app active cache.
8. Verify fixed-city widget refresh never changes app/wallpaper active location.
9. Verify manual widget offline refresh shows saved/offline state and does not retry indefinitely.
10. Verify Radar restart/offline fallback can recover recent saved metadata/model context where available.
11. Verify delayed/stale Radar labels appear for old fallback data.
12. Verify Radar/OSM image tiles are not falsely represented as offline when provider requests cannot load.
13. Open More -> Data Reliability and verify ages/identity update after Refresh diagnostics.
14. Test corrupted/cleared app data paths where practical; malformed per-location cache should be discarded rather than crash parsing.

## Verification boundary

- All Phase 23 source changes are intended for `main` only.
- No new branch is created.
- Source implementation is complete; local Gradle/debug/device verification has not yet been run for these final Phase 23 changes.
- Phase 24 has not started.
