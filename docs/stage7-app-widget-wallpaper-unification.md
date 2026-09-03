# Stage 7 — App, Widget and Wallpaper Unification

Stage 7 establishes one authoritative active-weather identity and freshness contract across the foreground app, follow-active widgets and Live Wallpaper.

## Delivered

- Added an immutable `ActiveWeatherSnapshot` carrying weather payload, coordinates, provider observation time, fetch time, timezone, location label, scope and freshness together.
- Added an active target/generation store so location identity changes immediately instead of waiting for a network response.
- Foreground refresh responses now commit only when their request generation is still authoritative.
- Older responses for the same location and responses from a previously active location cannot replace a newer active truth.
- Follow-active widget refresh and wallpaper background refresh commit through the same active snapshot gate.
- Live Wallpaper now consumes the shared active snapshot and receives an immediate change signal, while retaining its 45-second safety reload.
- Active widgets keep using the shared active pointer; a newly selected location with no cache shows a waiting state instead of leaking old-location weather.
- Fixed-city widgets remain isolated per-location snapshots and never move the app/wallpaper active pointer.
- Weather freshness is normalized to `LIVE`, `CACHED` and `STALE` from the existing reliability thresholds.
- Cached weather remains available as an offline fallback with its original fetch timestamp; no synthetic live timestamp is created.

## Preserved

- Stage 4 Reality Fusion weather truth.
- Stage 5 atmosphere/sky pipeline.
- Stage 6 precipitation, storm and wetness pipeline.
- Existing saved-city storage, widget configuration, wallpaper visual toggles and performance governor.
- Existing per-location weather cache format and legacy migration.
- Fixed-city widget periodic refresh limits and cache reuse policy.

## Regression coverage

- Exact active request generation is accepted.
- Older same-location generation is rejected.
- Old-location response is rejected even when a generation number matches.
- Missing generation cannot become authoritative.
- Observation time, timezone, location identity and freshness remain bundled in one snapshot.
- `LIVE`, `CACHED` and `STALE` thresholds are verified.
- Fixed-city snapshots retain an explicit isolated scope.

## Next stage

Stage 8 can build on this unified truth layer for deeper cloud, visibility and severe-weather intelligence without creating separate app/widget/wallpaper interpretations.
