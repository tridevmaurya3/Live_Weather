# Stage 13 — Real Solar Irradiance, Directional/Diffuse Light & Surface Glare Realism

## Goal
Make the rendered world respond to actual incoming solar energy instead of cloud percentage and astronomical Sun position alone, while preserving all Stage 1–12 weather truth and renderer connections.

## Provider truth added
Open-Meteo current and hourly requests now include:

- `shortwave_radiation` — global horizontal shortwave radiation (GHI), W/m²
- `direct_radiation` — direct solar radiation on the horizontal plane, W/m²
- `diffuse_radiation` — diffuse horizontal radiation (DHI), W/m²
- `direct_normal_irradiance` — direct normal irradiance (DNI), W/m²

Current conditions can expose hourly weather variables. Older cached JSON remains compatible because all new DTO fields are nullable.

## Solar irradiance reality policy
`SolarIrradianceRealityPolicy` converts provider W/m² values into bounded renderer-only signals:

- global light factor
- direct light factor
- directional Sun visibility factor
- diffuse-light fraction

The policy never changes weather code, cloud amount, precipitation, storm state, Sun position or forecast truth.

### Fallback behavior
If an older cached payload has no solar fields, the policy returns neutral factors. Stage 12 lighting therefore remains unchanged instead of guessing solar energy from season or location.

## Shared App Hero + Live Wallpaper behavior
`GlRealityAdapter` is the common truth-to-GPU adapter, so both surfaces receive the same calibrated Sun visibility and scene illumination.

The existing world shader already uses shared Sun visibility and scene light for:

- directional terrain/vegetation warmth
- river/water glint
- wet-ground sheen/reflection
- golden-hour directional emphasis
- general world exposure

Stage 13 therefore improves these effects without adding a new renderer pass or per-frame provider work.

## Direct versus diffuse realism
Bright direct irradiance keeps the Sun disc and specular highlights strong. Low direct irradiance suppresses the visible beam even if total cloud cover alone would have suggested a brighter Sun.

Diffuse irradiance preserves useful ambient daylight under bright overcast. This avoids treating every cloudy scene as equally dark.

Broken-cloud shadow variation is still driven by the existing Stage 11 cloud/wind model, but Stage 13 gates its strength by actual direct-beam energy: weak direct solar energy cannot cast an unrealistically strong moving cloud shadow.

## Safety / truth safeguards
- No fake Sun at night.
- No weather-code changes.
- No cloud, rain, fog, storm or snow fabrication.
- No new location/season assumptions.
- No changes to fixed-city widget isolation or Stage 7 active snapshot identity.
- No new GL pass and no per-frame allocation introduced.
- Provider model values are bounded before affecting visual exposure.

## Regression coverage
- current solar DTO parsing
- hourly solar DTO parsing
- old cached payload neutral fallback
- strong direct-sun behavior
- diffuse-overcast behavior
- zero night irradiance cannot create visible Sun
- weak observed irradiance dims the same atmospheric scene
- bright diffuse sky retains ambient light
- stronger direct beam permits stronger broken-cloud shadow response

## Performance
Solar policy executes only when a new weather snapshot is composed. Existing GPU uniforms and renderer paths are reused, so Stage 13 does not add a texture, framebuffer, draw pass or network request beyond four extra variables on the existing Open-Meteo weather call.
