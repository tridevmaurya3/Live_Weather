# Live Weather — Cinematic Reality R1–R7

Status: **SOURCE IMPLEMENTATION COMPLETE — automated build gate pending at this checkpoint; real-device OpenGL visual acceptance still required.**

This pass is intentionally not a Play Store/release phase. Its only goal is to make the shared app Hero + Android Live Wallpaper reality engine smoother, more natural and more cinematic without fabricating weather.

## Permanent truth rules

- Current observed/resolved weather remains authoritative.
- Forecast probability never fabricates current rain, lightning, snow or fog.
- Clear weather receives no decorative cloud/rain/storm floor.
- Performance modes may remove secondary samples only; they must not change actual weather state/intensity.
- App Hero and Live Wallpaper continue to use the same `HeroGlPipeline` and `GlRealityAdapter` scene truth.
- Widgets are not continuous OpenGL surfaces. They receive fresher data, not fake animation.

## R1 — Cloud realism and motion

Active renderer: `HeroGlTextureCloudRenderer`

- Directly consumes resolved far/mid/near/storm-ceiling cloud layers.
- Layer-specific drift creates real depth/parallax.
- Slow bounded vertical breathing removes pasted-sprite feel.
- Atlas mirroring/variation reduces visible repetition.
- Overcast ceiling follows resolved cloud/storm ceiling truth.
- Restrained Sun/Moon edge lighting responds to real celestial visibility.
- Motion remains wrapped/continuous and allocation-free in the frame loop.

## R2 — Sun, sky, sunrise/sunset and twilight

Active renderer: `HeroGlSkyCelestialRenderer`

- Astronomy-driven Sun position remains unchanged.
- Improved horizon scattering, twilight band continuity and low-Sun extinction.
- Sun disc, glow and aureole remain bounded to avoid a synthetic lens-flare look.
- Fog/haze/visibility reduce direct celestial contrast naturally.
- Current apparent temperature adds only a subtle thermal atmospheric bias.

## R3 — Moon and stars

Active renderers: `HeroGlSkyCelestialRenderer`, `HeroGlFixedStarRenderer`

- Moon phase geometry remains astronomy-driven.
- Lunar maria/crater response, limb shading, earthshine and halo are analytic/deterministic.
- Stars use deterministic positions and subtle per-star scintillation.
- Stars never blink in/out because of decorative randomness; global visibility comes from the shared sky-reality engine and clouds occlude them locally by render order.

## R4 — Rain, wet glass and wet world

Active renderers: `HeroGlDepthRainRenderer`, `HeroGlAnalyticWorldRenderer`

- Drizzle/far/mid/near rain retains depth-specific streak geometry.
- Wind creates coherent lean and bounded gust modulation.
- Heavy rain adds restrained lower-world impact splashes.
- Wet-glass droplets/trails remain gated by current rain/drizzle.
- Wet ground adds subtle ripple/sheens and Sun/Moon/settlement reflections only while precipitation has actually wet the scene.

## R5 — Storm and lightning

Active renderer: `HeroGlPortableStormRenderer`

- Lightning remains strictly gated by resolved storm intensity and the user Lightning option.
- Fixed repeating strike timing was replaced by storm-strength-dependent variable intervals.
- Multi-pulse main/fork/companion channels remain bounded by performance detail.
- Night exposure is slightly stronger while daylight remains visible without an artificial full-screen flash.
- Cloud-local charge/glow remains coupled to actual cloud mass/ceiling.

## R6 — Fog and haze

Active renderer: `HeroGlAtmosphereOverlayRenderer`

- Fog is a layered low-atmosphere moisture field, not a flat grey overlay.
- Three slow moving bands create natural depth without network/bitmap work in the frame path.
- Haze stays horizon-biased and combines with real visibility loss.
- Sun/Moon scattering is restrained by current fog/haze/storm values.

## R7 — Snow and hot/cold atmosphere

Active renderers: `HeroGlSnowRenderer`, `HeroGlAtmosphereOverlayRenderer`, `HeroGlAnalyticWorldRenderer`

- Snow keeps far/mid/near depth with wind direction and bounded gust turbulence.
- `GlRealityAdapter` derives `thermalBias` from current `apparent_temperature`, falling back to `temperature_2m` only when necessary.
- Thermal bias is presentation-only: it cannot create/remove any weather condition.
- Hot air receives a very subtle lower-atmosphere shimmer/tone; cold air receives a restrained cool/crisp atmospheric/world response.
- Thermal transitions are smoothed in `GlSceneTransitionController` rather than jumping when data refreshes.

## Real-time cross-surface behavior

- Live Wallpaper periodic weather refresh already uses the Android periodic-work floor of 15 minutes.
- Installed widgets now use a 15-minute periodic refresh with a 5-minute flex window instead of 30 minutes.
- App foreground refresh remains independent and may update sooner through the existing weather flow.
- OpenGL animation never performs network requests in the frame loop.

## Smoothness / performance contract

- No per-frame Java object allocation was added to the shared transition path.
- Shader detail scale still controls secondary cloud/storm/rain/snow samples.
- Hidden app Hero and invisible Wallpaper retain zero/paused rendering behavior from the existing lifecycle/performance system.
- Weather truth is never reduced to gain FPS.

## Required real-device acceptance

Test both Home Hero and an applied Live Wallpaper:

1. Clear day: natural blue gradient, real Sun path, no fake precipitation/cloud floor.
2. Sunrise/sunset: gradual warm horizon and Sun extinction without abrupt color jumps.
3. Clear night: correct Moon phase, restrained halo, subtle star scintillation.
4. Partly cloudy: multiple cloud depths moving continuously without edge pop/repeating obvious tiles.
5. Overcast: continuous ceiling without giant isolated sprite blobs.
6. Drizzle: fine streaks; no heavy impact splash.
7. Heavy rain: depth streaks, wet glass, lower-world splashes and restrained wet reflections.
8. Thunderstorm: irregular multi-pulse lightning only when current storm truth is present.
9. Fog/haze: layered low veil with distant contrast loss, not a flat opaque screen.
10. Snow: depth layers and wind turbulence without teleport/jitter.
11. Hot apparent temperature: only subtle atmospheric warmth/shimmer.
12. Cold apparent temperature: subtle cool/crisp response; no fake snow.
13. Switch city/current location and watch all layers ease rather than hard-reset.
14. Compare Home Hero and applied Wallpaper for the same weather/time/location.
15. Check Smooth/Auto/Battery modes for identical weather truth and only reduced secondary detail/FPS.

GLSL shaders compile on the actual device GPU when the OpenGL surface is created, so Android/CI build success is necessary but not sufficient for visual/GPU acceptance.
