# Stage 9 — Thermal & Microclimate Ground Realism

Stage 9 strengthens the environmental feel of the shared App Hero and Live Wallpaper without changing authoritative weather truth.

## Thermal material truth

- World material warmth/cold now uses a shared `ThermalEnvironmentPolicy`.
- Measured temperature remains the anchor.
- Apparent temperature refines how the scene feels.
- Relative humidity and dew point add bounded humid-heat context.
- Dry heat receives a smaller bounded contribution.
- A colder apparent temperature can strengthen cold/wind-chill presentation.
- The output stays strictly within `-1..1` and remains presentation-only.

The existing world renderer already consumes this thermal signal, so no new renderer pass, texture allocation, scheduler or network call was introduced.

## Fog / haze continuity

Stage 8 separated meteorological fog from pollution/dust haze in the atmosphere engine. Stage 9 carries that distinction into Auto scenery:

- fog continues to use the fog-friendly scene pool;
- dry AQI/dust haze no longer pretends to be fog;
- strong haze may select a bounded low-contrast/exposed scene pool;
- storm, snow, rain and cloud truth keep their existing priority order.

## Safety / compatibility contract

- No weather code is rewritten.
- No precipitation, lightning or alert state is created.
- No manual scenery selection is changed.
- No Stage 1–8 cache/location/widget/wallpaper contract is replaced.
- No per-frame allocation or extra network request is added.

## Regression coverage

Tests cover:

1. neutral comfort temperatures;
2. humid heat vs dry heat;
3. apparent cold strengthening cold presentation;
4. bounded extreme signals;
5. haze no longer routing through fog-specific Auto scenery;
6. all existing Auto scenery storm/snow/fog/day-part behavior.
