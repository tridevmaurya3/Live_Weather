# Live Weather — Phase 9 Radar Engine

Status: COMPLETE — REAL RADAR + MODEL OVERLAYS

## Source contract

### Rain / precipitation radar

- Source: RainViewer public Weather Maps API.
- The layer is observed radar history, not a fabricated forecast.
- Public API timeline is the past two hours in approximately 10-minute steps.
- Universal Blue color scheme (ID 2) is used.
- Radar tile zoom is capped at 7, matching the public API limit.
- Radar metadata is cached in memory for five minutes to reduce repeated API load.
- Visible attribution is preserved in the Radar screen and map.

### Clouds / wind / temperature

- Source: Open-Meteo Forecast API current conditions.
- A 5x5 grid around the active weather location is requested in one multi-coordinate API call.
- Variables: cloud_cover, wind_speed_10m, wind_direction_10m, temperature_2m.
- This is a model field, not satellite imagery and not a physical radar measurement.
- Model-field data is cached in memory for ten minutes.

### Base geography

- OpenStreetMap raster base tiles are used with visible OpenStreetMap attribution.

## Phase 9 features

- Real RainViewer precipitation radar tiles.
- Animated observed radar timeline.
- Play / pause control.
- Manual timeline scrubbing.
- UTC frame timestamp display.
- Current Cloud model overlay.
- Current Wind vector overlay with speed labels.
- Current Temperature field overlay.
- Active-location marker.
- Recenter action.
- Manual refresh action.
- Same active location as the shared WeatherCache used by the app and Live Wallpaper.
- Saved-city changes and current-GPS changes propagate through the shared weather cache.
- Radar page watches for a newer weather-cache location while visible.
- Graceful fallback when radar or model overlays are unavailable.
- Radar and model responses are independent, so one layer can still work if the other fails.

## 2026 provider limitation

RainViewer removed public future nowcast frames and satellite IR data on 1 January 2026. Therefore Live Weather does not display fake future radar or fake satellite imagery. The Rain layer is explicitly labelled as observed history.

Clouds are current model-estimated cloud cover from Open-Meteo. They must not be described as satellite cloud photography.

## Security / rendering architecture

- Network weather/radar requests run in the Android data layer, not inside the animation/render loop.
- The map is a controlled local WebView surface.
- No JavaScript-to-native bridge is exposed.
- Native Android sends sanitized JSON to the map with evaluateJavascript.
- External page navigation is blocked.
- JavaScript is used only for the controlled map renderer.

## Accuracy boundaries

Radar coverage is not guaranteed everywhere. A missing RainViewer echo can mean no precipitation echo, weak/blocked radar signal, provider outage, or lack of local radar coverage.

Open-Meteo cloud/wind/temperature overlays are sampled model values and should be read as regional atmospheric guidance, not street-level observations.

The app should always distinguish:

**observed radar history** from **model atmospheric overlays**.
