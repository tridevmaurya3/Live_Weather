# Stage 5 — Atmosphere and Sky Realism

Stage 5 completes a continuous, location-aware sky-lighting layer shared by the app and Live Wallpaper.

## Delivered

- Astronomy Engine remains the authority for observer-specific Sun/Moon altitude and azimuth.
- Sunrise, sunset and all three twilight bands now feed continuous lighting curves instead of relying only on hard palette labels.
- Warm horizon scattering peaks near sunrise/sunset and is naturally reduced by cloud obstruction.
- Day/night exposure remains bounded: heavy cloud, fog or storms can darken the world but cannot create a crushed-black daytime scene.
- Stars are gated continuously from civil light through astronomical darkness, then further reduced by cloud, precipitation, visibility, haze and lunar glare.
- Moon position, phase geometry and illumination remain physically calculated; no decorative Moon is fabricated.
- Fog, AQI haze and low visibility are combined into a horizon-weighted depth veil.
- Lightning environment lift is truth-gated by current storm strength and capped to prevent full-screen white flashes.
- The policy is pure arithmetic with no allocations in the render-frame path.
- `GlRealityAdapter` and `SkyGradientProfile` are shared by the in-app sky and Live Wallpaper, so both receive identical atmosphere truth.

## Preserved

- Weather condition and Reality Fusion decisions.
- Existing Astronomy Engine calculations.
- Existing cloud textures, storm renderer, rain/snow and scenery controls.
- User visual toggles and performance profiles.

## Next stage

Stage 6 will focus on rain depth, storm motion, wind response, surface wetness and recovery after precipitation.
