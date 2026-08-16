# Phase 20A — Accurate Live Weather Reality Foundation

Status: SOURCE IMPLEMENTATION COMPLETE — real-device visual verification required.

## Product priority

Accurate Live Weather in Real Feeling is the app's Hero Part. Radar and later
feature work must preserve the same current-condition truth used by Home and
Live Wallpaper. The project is not Final or Production Ready.

## Cinematic realism + smoothness contract

Real Live Weather should aim for film-quality believability without turning the
phone into a movie-render workstation. Visual realism is achieved with layered,
GPU-friendly motion, deterministic textures, bounded atmospheric effects and
shared scene state rather than repeated UI/network refreshes.

Permanent rules:
- weather/network access never belongs in the per-frame render loop;
- no screen/page refresh is used to animate weather;
- renderer state changes only when real weather/options/location evidence changes;
- normal frame animation uses lightweight shader uniforms and pre-created GPU resources;
- expensive astronomy/weather recomposition is rate-limited and never tied directly
  to every home-screen swipe/parallax callback;
- hidden app surfaces and invisible Live Wallpaper surfaces render zero frames;
- inactive rain/snow/storm passes return immediately;
- performance/battery policy may change frame cadence, but must never change the
  truth of the weather being shown;
- future realism upgrades must prefer perceptual tricks (depth, parallax, lighting,
  occlusion, texture reuse) over large allocations, repeated bitmap creation or
  per-frame Java object generation.

## Checkpoint 20A.1

- Replaced repeating fixed ellipse cloud banks with deterministic multi-scale
  organic cloud masses.
- Added separate far, middle and near cloud depth with independent drift.
- Added soft breakup, bright edges, shaded cloud bases and a continuous storm
  ceiling without square/grid artifacts.
- Kept one shared OpenGL pipeline for in-app scenes and system Live Wallpaper.
- Cloud movement remains controlled by real wind direction and strength.
- Preserved WMO/current precipitation rules: forecast probability alone cannot
  start current rain or thunderstorm visuals.
- Preserved astronomical Sun/Moon positions, lunar phase geometry and real
  cloud/weather occlusion.

## Checkpoint 20A.2 — screenshot correction

- Fixed aspect-normalized cloud coordinates before periodic distance evaluation.
- Connected overlapping cloud masses instead of selecting isolated maximum puffs.
- Added cloud-cover-driven continuous overcast sheet; ordinary overcast no longer
  depends on a thunderstorm ceiling.
- Softened field thresholds and corrected edge-light calculation to remove the
  polygon/stone-like islands visible in the first device screenshot.

## Checkpoint 20A.3 — real animated cloud asset engine

- Retired the analytic cloud-shape renderer from the active pipeline.
- Added a photoreal cloud atlas resource with eight weather-oriented cloud types.
- Added a texture-based OpenGL renderer with continuously wrapping far/mid/near
  layers, independent scale/opacity and parallax.
- Weather cover selects clear/cumulus/broken/overcast sprites; confirmed rain
  and storm select dark rain and shelf-cloud sprites.
- Real wind direction, sustained speed and gust response control motion.
- Day/night brightness and storm intensity tint the same physical cloud assets.
- Atlas background is removed in the shader before alpha blending.

## Checkpoint 20A.4 — recording-driven atlas correction

- Corrected the vertically inverted Android/OpenGL atlas row mapping.
- Overcast now selects layered stratus; confirmed rain selects dark rain cloud;
  confirmed storm selects the shelf-cloud texture.
- Increased cloud-bank scale and overcast coverage.
- Added minimum direction-preserving lateral drift so north/south winds do not
  appear completely static in the 2D scene.
- Increased visible motion while retaining wind/gust control.

## Checkpoint 20A.5 — rain depth and wet-screen response

- Replaced the active flat rain pass with a depth-aware shared rain renderer.
- Added separate far, middle and near rain bands with independent apparent size,
  fall speed and wind lean.
- Kept drizzle visually finer than confirmed rain.
- Added bounded heavy-rain mist and wet-lens detail only when current rain
  evidence is strong enough.

## Checkpoint 20A.6 — storm and lightning realism

- Increased storm darkness according to current storm/cloud/rain evidence.
- Restricted lightning exposure mainly to the active storm cloud field instead
  of flashing the whole screen uniformly.
- Lengthened the main bolt, added deterministic optional forks and tuned glow.
- Kept electrical effects controlled by the existing Lightning option.

## Checkpoint 20A.7 — celestial and twilight visibility

- Removed duplicate whole-screen weather attenuation from the fixed-star pass.
- Shared sky-reality visibility remains authoritative; cloud textures rendered
  afterwards perform actual local star occlusion.
- Smoothed Sun and Moon twilight visibility and halo behavior without changing
  astronomical positions or lunar phase geometry.

## Checkpoint 20A.8 — fog, haze and snow

- Separated fog and haze atmosphere behavior instead of using one flat grey veil.
- Added low rolling fog bands and broader horizon haze behavior.
- Added a dedicated depth-aware snow renderer with far/mid/near flakes, wind
  drift and restrained heavy-snow depth mist.
- Snow remains tied only to resolved current snow evidence and the Snow option.

## Checkpoint 20A.9 — high-gust response

- Preserved sustained wind as the base motion source.
- Increased bounded motion response to verified current gust excess/ratio.
- High-gust cloud motion now gains subtle speed modulation, cross-flow and lift.
- Rain and snow inherit the same shared normalized wind-strength response.
- Gust evidence cannot invent rain or storm state.

## Checkpoint 20A.10 — renderer diagnostics

- Added a shared read-only HeroGlDiagnostics surface used by the same pipeline as
  the in-app Hero and Android Live Wallpaper.
- Diagnostics expose current resolved weather evidence, active visual effects,
  cloud/rain/drizzle/snow/fog/storm/haze intensities, wind strength/direction,
  visibility, scene light and star visibility.
- Captures OpenGL vendor, renderer, version and active surface resolution to help
  explain emulator/Adreno/Mali device-only differences.
- Exposes the active renderer quality label (`FULL_SHARED_GL`) and visual option
  states without changing weather truth or rendering behavior.
- `LiveWeatherGL` reports are formatted only on explicit capture, GL surface
  creation or a meaningful resolved weather-evidence class change; diagnostics
  are not a per-frame logging workload.
- Forecast probability is not converted into current precipitation evidence by
  this diagnostic layer.

## Checkpoint 20A.11 — source build-safety and smoothness audit

- Rechecked the active shared renderer lifecycle: sky, stars, texture clouds,
  world, atmosphere, storm, depth rain and depth snow remain wired through one
  HeroGlPipeline for both app Hero and Live Wallpaper.
- Preserved early-return behavior for inactive precipitation/storm effects.
- Confirmed app Hero and Live Wallpaper rendering runs on dedicated display-priority
  render threads rather than the UI/network thread.
- Preserved lifecycle stop behavior: hidden app Hero and invisible wallpaper draw
  no frames.
- Confirmed wallpaper network refresh remains independent through WorkManager and
  is not coupled to animation FPS.
- Added cached-state change detection so the 45-second lightweight wallpaper cache
  check does not re-send unchanged weather/options through the renderer.
- Removed unnecessary weather recomposition when only a visual option changes.
- Added bounded home-screen parallax recomposition so rapid launcher offset events
  cannot trigger full astronomy/weather composition on every callback.
- Kept diagnostics report formatting off the frame hot path.
- No renderer in the active Phase 20A pipeline performs network I/O, bitmap creation
  or large Java object allocation per frame.

This checkpoint is a source-level safety audit. A local Android Gradle build and
real-device GPU validation are still required before Phase 20A visual acceptance.

## Acceptance required

Source implementation is complete, but Phase 20A is not accepted until it is
verified on a real phone.

Test clear, partly cloudy, overcast, rain, storm, night, fog/haze, snow where
available and a high-gust scene. Compare Home preview and applied Live Wallpaper
at the same time and city.

Confirm:
- clouds have soft natural masses and no rectangular tiles;
- cloud motion is continuous and wind-responsive;
- drizzle/rain/snow have believable depth;
- heavy rain wet-screen behavior is restrained and continuous;
- storm darkness and lightning are natural rather than full-screen white flashes;
- Sun, Moon and stars transition smoothly and are locally occluded by clouds;
- fog/haze do not look like a flat opaque rectangle;
- Home Hero and applied Live Wallpaper remain visually consistent;
- `LiveWeatherGL` diagnostics match the weather actually being displayed;
- launcher swipes/parallax do not cause stutter or visible scene resets;
- unchanged cached weather does not cause a visible refresh/flicker.

Source implementation alone is not visual acceptance.
