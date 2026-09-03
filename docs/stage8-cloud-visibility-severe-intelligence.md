# Stage 8 — Cloud, Visibility and Severe Weather Intelligence

Stage 8 upgrades atmospheric realism without replacing the Stage 1–7 weather, cache, widget, wallpaper, alert or rendering contracts.

## Delivered

- Open-Meteo low, mid and high cloud-cover variables are now requested for current and hourly payloads.
- Existing cached payloads remain readable; missing vertical layers use a conservative total-cloud fallback.
- Provider high/mid/low cloud percentages map into the existing far/mid/near GPU depth channels, so no extra shader pass is required.
- Current precipitation can strengthen physically plausible low/mid source layers, but cannot invent precipitation.
- Current WMO thunderstorm truth can vertically connect cloud layers as before.
- A bounded severe-cloud transition envelope reads nearby 15-minute provider weather-code intervals.
- The envelope may deepen/darken clouds shortly before or after a verified thunderstorm interval.
- The envelope never changes `stormIntensity`, so it cannot invent lightning, rain, alerts or a thunderstorm condition.
- Meteorological fog/mist is now separated from AQI aerosol haze.
- Dry polluted air can reduce atmospheric clarity through the haze channel without creating a white fog layer.
- Explicit WMO fog codes remain authoritative.
- Saturated, low-visibility air may resolve a bounded mist/fog layer even when the WMO code is temporarily non-fog.
- App Hero and Live Wallpaper continue to consume the same `DynamicRealityComposer` scene truth.
- Active widgets continue to consume the same Stage 7 active weather identity; fixed-city widgets remain isolated.
- No new network client, refresh loop, GL pass, database or background scheduler was introduced.

## Performance bounds

- Vertical cloud classification is constant-time per weather snapshot.
- Severe transition scanning is limited to the already-downloaded short 15-minute weather-code list.
- Existing cloud shaders and temporal smoothing are reused.
- No allocations were added to the GL per-frame hot path.

## Regression coverage

- Provider cloud-layer DTO parsing.
- Provider-backed high/mid/low mapping and legacy fallback.
- Fog versus dry AQI haze separation.
- Explicit WMO fog authority.
- Approaching/retreating thunderstorm cloud-only envelope.
- Upcoming severe interval cannot switch the current cloud mode to `STORM` by itself.

## Preserved

- Stage 4 Reality Fusion and current-condition truth gates.
- Stage 5 atmosphere/sky and Stage 6 precipitation/wetness behavior.
- Stage 7 generation-safe active snapshot identity.
- Existing rain, snow and lightning scheduling.
- Existing app/widget/wallpaper settings and fixed-city widget isolation.
