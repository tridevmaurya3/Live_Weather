# Stage 6 — Rain, Storm, Wind and Wetness

Stage 6 completes one shared precipitation-motion contract for the app Hero and Live Wallpaper.

## Delivered

- Rain remains split into far, middle and near depth layers with perspective-weighted opacity.
- A shared allocation-free policy now controls fall speed, wind lean and turbulence.
- Heavy rain falls faster than drizzle; wind changes angle while gravity remains downward.
- Current gust/storm strength can add bounded turbulence without inventing precipitation.
- Low-detail profiles disable expensive near drops and wet-glass work.
- Wet-glass activates only for sufficiently strong current liquid precipitation.
- Snow uses its own bounded wind/storm/snow turbulence curve.
- Existing truth-gated storm scheduler remains the only lightning trigger.
- Existing process-shared ground controller retains wetness across Hero/wallpaper surface recreation.
- Surface wetness, soil saturation, puddle depth and puddle spread accumulate separately.
- After rain, puddle depth recedes before the damp footprint; warmth and wind accelerate drying.
- Existing irregular puddle mask, reflection and surface-shine shader inputs remain intact.

## Preserved

- Stage 4 Reality Fusion truth.
- Current rain/storm/snow weather-code gates.
- Existing shaders, user effect toggles and performance settings.
- Weather, radar, widget and wallpaper cache connections.

## Next stage

Stage 7 will unify app, widgets and wallpaper freshness/status presentation around the same active weather snapshot.
