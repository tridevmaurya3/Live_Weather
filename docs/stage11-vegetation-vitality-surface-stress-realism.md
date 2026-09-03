# Stage 11 — Vegetation Vitality & Surface Stress Realism

Stage 11 makes vegetation respond to the same live environment as the rest of the world instead of remaining a mostly static green material.

## Environmental moisture truth

- Current relative humidity is used as an observed atmospheric moisture anchor.
- Dew-point depression refines whether that humidity represents genuinely moisture-rich air.
- Missing humidity/dew-point data stays neutral rather than pretending the environment is dry.
- The new normalized moisture context is presentation-only and never changes weather condition text or forecast truth.

## Retained ground + current weather

The vegetation material policy combines:

- atmospheric moisture context;
- retained soil saturation;
- retained visible ground wetness;
- current rain/drizzle;
- current snow;
- Stage 9/10 thermal material context.

Dry stress requires both real heat context and low moisture evidence. A newly-created renderer therefore cannot declare vegetation dry merely because retained ground history starts empty.

## World material response

The existing shared App Hero / Live Wallpaper world renderer receives allocation-free reusable signals for:

- vegetation vitality;
- dry/heat stress;
- cold stress.

These bounded signals affect forest, hedges, farm crops, river-bank plants, meadow grass and flower density. Wetness, snow, daylight, moonlight and existing scenery variation remain layered on top of the same established material pipeline.

## Safety / compatibility contract

- No new network request.
- No weather code or precipitation state is rewritten.
- No fake frost, snow, rain or seasonal weather is created.
- No manual scenery selection is changed.
- Existing ground wetness/puddle physics stays authoritative for retained surface water.
- Existing GlSceneSnapshot constructor remains source-compatible for older callers/tests.
- No per-frame object allocation is added; the vegetation sample is reused.

## Regression coverage

Tests cover humid-vs-dry atmospheric moisture, neutral missing observations, hot/dry stress, wet-soil protection, rain moisture recovery, cold stress separation and strict signal bounds.
