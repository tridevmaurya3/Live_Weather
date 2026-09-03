# Stage 3 — Location Continuity

Stage 3 keeps current-location weather aligned with meaningful device movement without running GPS continuously.

## Delivered

- Persists the last accepted device latitude, longitude, accuracy and capture time.
- Restores that snapshot immediately on launch while a fresh one-shot fix is requested.
- Rechecks location on foreground entry at most once per five minutes.
- Ignores ordinary GPS drift below 750 metres.
- Activates a new weather location after meaningful movement or when the snapshot is 15 minutes old.
- Rejects invalid coordinates and fixes whose reported accuracy is worse than 5 km.
- Keeps manually selected cities isolated from all automatic device-location rechecks.
- Leaves WeatherCache as the single active-coordinate source for app, active widgets and wallpaper workers.
- Retains existing permission, disabled-location and Play Services error UI.

## Preserved behavior

- Current location still uses a bounded one-shot Fused Location request.
- Manual refresh still forces a location and weather refresh.
- Fixed-city widgets keep their own coordinate snapshots.
- Saved-city selection and removal semantics are unchanged.
- Weather, radar, AQI, alerts and rendering APIs are unchanged.

## Contract tests

- Small GPS drift does not reload weather.
- Meaningful movement changes the active location.
- Stale snapshots refresh even without movement.
- Invalid and extremely inaccurate fixes are rejected.
- Foreground checks remain power bounded.

## Next stage

Stage 4 will fuse current weather, precipitation nowcast, radar observations and source confidence into one truthful reality state.
