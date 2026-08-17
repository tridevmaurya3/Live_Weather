# Live Weather — Cinematic Reality Pass R2–R7

Status: **SOURCE IMPLEMENTATION COMPLETE — automated Debug/tests/Release lint/R8 APK+AAB gate PASSED; real-device GPU visual acceptance remains authoritative**

Play Store release is intentionally out of scope for this pass.

## Automated gate record

- GitHub Actions run: `32003788817` (run #78)
- Authoritative code head: `49395d5d3613c3ffa2d80a0d85faf68a54133d31`
- Result: **success**
- Debug build: passed
- Unit tests: passed
- Release lint: passed
- Minified/R8 Release APK: passed
- Release AAB: passed
- Release output verification: passed
- Verification artifact ID: `9279398018`
- Artifact SHA-256: `c8327a768504e1f7721ab77d77620b703c195bd6a53c907afd99dea8ac3322c3`

## Goal

Make the shared OpenGL weather scene feel like naturally photographed weather rather than a collection of screen effects. The same reality pipeline is used by the Home Hero and Android Live Wallpaper. Weather truth always wins over visual drama.

## Non-negotiable rules

- Current observed/resolved weather drives rain, drizzle, snow, fog, storm and cloud presence.
- Forecast probability never becomes current rain/lightning.
- Sun/Moon astronomical positions and Moon phase remain authoritative.
- Performance tiers may reduce secondary sampling only; they may not change weather truth.
- No network, cache parsing, bitmap decode or large allocation is added to the frame hot path.
- Hidden Hero / invisible wallpaper rendering behavior remains unchanged.
- No Play Store/release work is part of this visual pass.

## R1 — Cloud volume and motion

Already completed before this combined pass:

- resolved far/mid/near/storm-ceiling cloud truth consumed directly by the atlas renderer
- different depth speeds and parallax
- wrapped horizontal travel without hard edge reset
- subtle vertical breathing and wind shear
- atlas variation/mirroring to reduce obvious repetition
- overcast continuity and storm ceiling
- restrained Sun/Moon cloud illumination
- governor-controlled secondary samples

## R2 — Sun, sky and twilight

The active `HeroGlSkyCelestialRenderer` already contains the cinematic sky refinement used by this checkpoint:

- astronomy-driven Sun position
- low-Sun extinction and warm disc response
- narrow + wide atmospheric glow instead of a flat icon-like Sun
- horizon aureole and twilight continuity
- fog/haze/visibility attenuation
- restrained real-temperature atmospheric tone
- smooth lunar/solar visibility transitions through the shared transition controller

No duplicate sky renderer was introduced.

## R3 — Moon and real-time stars

### Moon

The active celestial pass already provides:

- astronomy-driven Moon position
- illumination/phase geometry
- earthshine on the dark side
- limb shading
- restrained maria/crater surface variation
- low-altitude warmth and night halo

### Stars — upgraded in this pass

`GlRealityAdapter` now provides:

- observer latitude
- local sidereal angle calculated from epoch time + longitude

`HeroGlFixedStarRenderer` now stores stars in celestial coordinates (RA/Dec) instead of fixed screen X/Y:

- compact bright-star catalogue for recognizable bright anchors
- deterministic faint celestial background distributed on the celestial sphere
- GPU projection from observer latitude + local sidereal time
- horizon culling/fade
- subtle independent scintillation, stronger near the horizon
- restrained warm/cool stellar tones
- cloud renderer still provides local occlusion after the star pass

The star field therefore rotates with the real sky rather than being pinned to the phone screen.

## R4 — Rain, wet glass and wet world

The active `HeroGlDepthRainRenderer` already contains the corrected cinematic rain pass:

- top-origin gravity direction corrected so drops visibly travel downward
- drizzle/far/mid/near depth bands
- short translucent streaks instead of bright white lines
- wind lean without violating gravity
- gust variation
- heavy-rain mist/veil
- restrained heavy-rain wet-glass droplets/trails
- lower-world film/ripple
- rain impact splash near the ground

The active world renderer already consumes current rain/drizzle for restrained wet material response; no wet reflection exists in dry weather.

## R5 — Storm and lightning

The active storm renderer already had:

- current-storm truth gate
- deterministic irregular strike timing
- multi-pulse strike exposure
- main channel + detail-gated forks/rare companion channel
- cloud-local charge/glow
- storm darkness tied to storm/cloud/rain truth
- visual lightning option separate from storm truth

This pass adds weather-aware optical diffusion:

- clear air keeps a sharper bolt
- fog, rain and low visibility soften the bolt slightly
- cloud illumination becomes wider in low visibility
- diffusion remains local/upper-cloud weighted and does not become full-screen periodic blinking

## R6 — Fog, haze and atmosphere

`HeroGlAtmosphereOverlayRenderer` was upgraded from visibly periodic sine bands to deterministic multi-scale noise:

- low-frequency fog density drifts with resolved wind
- secondary/tertiary samples are gated by performance detail
- fog/haze/rain/distance-loss remain truth-driven
- Sun forward-scatter responds to atmospheric aerosol load
- Moon scatter remains night/illumination gated
- hot/cold ambience remains subtle and current apparent-temperature driven
- storm vignette is restrained

The noise texture is created once with the GL surface and reused. No per-frame bitmap/network work is introduced.

## R7 — Snow and cold atmosphere

The active `HeroGlSnowRenderer` already supplies:

- far/mid/near snow depth
- wind drift and gust turbulence
- performance-gated near layer
- low-visibility snow mist
- real thermal/cold tone response

The shared atmosphere and world passes also consume the same thermal/snow truth, so snow/cold weather is not only an overlay of white particles.

## Final smoothing and performance parity

`GlSceneTransitionController` now uses frame-rate-independent exponential easing instead of frame-step-sensitive interpolation.

- a bounded 80 ms visual delta prevents a single janky/resume frame from teleporting the scene
- Sun/Moon positions, sidereal angle, cloud layers, rain, snow, fog, storm, wind, scene light and thermal state all transition through the same reusable snapshot
- no per-frame snapshot allocation
- circular values use shortest-path angle/cycle interpolation

`HeroGlPipeline` now sends the same performance detail scale to:

- world
- clouds
- atmosphere
- storm
- rain
- snow

Weather truth is never downscaled by the governor.

## Shared-surface parity

The Home Hero and Android Live Wallpaper continue to use the same `HeroGlPipeline` and `GlRealityAdapter`. The cinematic upgrades therefore apply to both surfaces without maintaining a second visual truth implementation.

Android launcher widgets are not continuous OpenGL surfaces. They continue to prioritize accurate current data and efficient refresh rather than pretending to provide continuous cinematic animation.

## Real-device acceptance matrix

After pulling `main`, verify on a physical phone:

1. Clear day: clean sky, natural Sun disc/glow, no fake rain/wetness.
2. Sunrise/sunset: gradual warm horizon and low-Sun extinction, no abrupt palette flip.
3. Clear night: stars rotate/shift with real sky time and do not look screen-pinned; twinkle is subtle.
4. Moon night: phase/earthshine/halo look restrained, not like a flat white circle.
5. Partly cloudy: cloud layers move at different apparent depths without edge pop.
6. Overcast: continuous deck, not obvious repeated isolated sprites.
7. Drizzle: fine short rain, no heavy wet-glass exaggeration.
8. Moderate/heavy rain: depth bands, downward gravity, natural wind lean, lower-world impact.
9. Storm: irregular lightning, cloud-connected flash, no periodic full-screen blinking.
10. Fog + storm: lightning becomes optically softer/wider without becoming brighter everywhere.
11. Fog/haze: rolling density looks organic, no clearly repeating sine bands.
12. Snow: multiple depths, natural wind response, no uniform synchronized flakes.
13. Hot weather: thermal ambience remains subtle; no orange filter over the whole screen.
14. Cold weather: subtle cool atmosphere/material response, not a blue filter.
15. Auto/Smooth/Battery: secondary detail may reduce; current weather presence/intensity must not change.
16. Home Hero vs Wallpaper preview vs applied wallpaper: weather layers and options remain consistent.
17. Background/foreground and wallpaper visibility transitions: no teleport, flash, EGL crash or obvious one-frame jump.
18. Check `LiveWeatherGL` diagnostics for renderer/shader faults.

## Acceptance boundary

Java/resource/R8 CI validates the Android source graph and release build outputs, but GLSL programs compile on the actual GLES driver when the surface is created. Therefore the real-device GPU/visual pass remains mandatory before declaring this cinematic reality checkpoint visually accepted.
