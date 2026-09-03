# Stage 10 — Seasonal Hemisphere Material Realism

Stage 10 adds a small geographic-season context to the shared App Hero and Live Wallpaper while preserving live weather truth.

## Hemisphere-aware season phase

- Calendar season is resolved from observer latitude plus day-of-year.
- Northern and Southern hemispheres use opposite seasonal phase.
- Tropical latitudes deliberately suppress strong four-season assumptions.
- Temperate/high latitudes receive the strongest seasonal material context.

## Live weather remains authoritative

- Stage 9 `ThermalEnvironmentPolicy` still produces the primary live thermal signal from measured temperature, feels-like temperature, humidity and dew point.
- Stage 10 adds only a small bounded seasonal contribution to that renderer-facing material signal.
- Strong live heat cannot be inverted by a winter calendar phase.
- Strong live cold cannot be inverted by a summer calendar phase.
- Final material thermal bias remains strictly within `-1..1`.

## No fake seasonal weather

Season never creates or changes:

- snow;
- rain or drizzle;
- storm/lightning;
- fog/haze;
- weather codes;
- alerts;
- temperature text;
- manual scenery selection.

The same `GlRealityAdapter` is shared by the foreground Hero and Live Wallpaper, so both receive identical latitude/date seasonal context without a new renderer pass, scheduler, database or network call.

## Regression coverage

Tests verify:

1. northern mid-year warm and January cold seasonal phase;
2. Southern Hemisphere phase inversion;
3. tropical four-season attenuation;
4. strong live heat cannot be inverted by calendar winter;
5. strong live cold cannot be inverted by calendar summer;
6. material output remains bounded.
