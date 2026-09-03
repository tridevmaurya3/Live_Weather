# Stage 12 — Snowpack Accumulation, Melt & Surface Thaw Realism

## Goal

Keep falling snow and accumulated ground snow physically separate. Current snowfall remains live weather truth, while observed ground snow can persist after flakes stop and thaw only when surface conditions support melting.

## Delivered

- Requests Open-Meteo `snow_depth` and `soil_temperature_0cm` in current and hourly weather data.
- Parses both fields without breaking older cached JSON payloads.
- Adds provider snow depth and surface temperature to the shared GL scene snapshot.
- Uses current 2 m air temperature only as a compatibility fallback when an older payload has no surface temperature.
- Adds a process-shared, allocation-free `SnowSurfaceController` for App Hero and Live Wallpaper.
- Existing observed snow depth anchors surface snow immediately on process start.
- Current snowfall can add retained snow between provider refreshes only when the surface is cold enough to retain it.
- Positive surface temperature drives gradual thaw; warm rain and stronger scene illumination can accelerate an already-valid thaw.
- Sub-freezing dry weather preserves existing snow and never creates melt, frost, ice or snowfall.
- Surface coverage retreats more slowly than normalized pack depth to avoid an instant all-or-nothing disappearance.
- Retained coverage is injected only into the world-material renderer view. The dedicated snowfall renderer continues receiving current `snowIntensity` only.
- Melt water feeds the existing process-shared ground-moisture reservoirs through a separate non-rain input, allowing thawing surfaces to become damp without generating precipitation exposure.
- AUTO scenery continues using current resolved snowfall truth, not retained snowpack.
- The user snow visual toggle still disables both falling and retained snow presentation.

## Truth safeguards

- Retained snow never changes WMO weather codes or resolved condition text.
- Retained snow never triggers snowflakes.
- Snow depth is not inferred from calendar season or latitude.
- Missing historical `snow_depth` is represented as unavailable, not as zero.
- No frost or ice layer is fabricated from cold temperature alone.
- Surface-temperature fallback does not invent seasonal values.
- Melt water never increments rain exposure and is never sent to the rain renderer.

## Performance

- No network, preferences or wall-clock reads are added to the GL hot path.
- Controller state uses primitive fields only and allocates nothing per frame.
- App Hero and Live Wallpaper share one process state with monotonic advance gates, preventing double-speed accumulation, melt or melt-water wetting when both surfaces are alive.
- Existing shaders and renderer passes are reused; no extra GL pass is added.

## Regression coverage

- Existing observed depth appears without active snowfall.
- Cold live snowfall can accumulate between provider refreshes.
- Above-freezing surfaces melt retained snow and generate a bounded melt signal.
- Sub-freezing dry weather does not invent thaw.
- Warm rain accelerates thaw compared with equally warm dry conditions.
- Melt water can increase physical ground wetness without using the atmospheric rain path.
- All snow-surface state remains within strict 0..1 bounds.
- DTO tests verify current and hourly `snow_depth` / `soil_temperature_0cm` parsing.

## Preserved

Stages 1–11 weather resolution, active-location synchronization, fixed-city widget isolation, cloud/visibility logic, rain/lightning truth, wind physics, wet-ground memory, thermal/seasonal material context, vegetation vitality, user visual options and release workflow remain unchanged except for the explicitly documented Stage 12 snow-surface inputs and presentation state.
