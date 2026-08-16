# Phase 20A.16 — Final Integration & Static Compile Preflight

Status: SOURCE PREFLIGHT COMPLETE — local Gradle build and real-device GPU validation still required.

## Scope

This checkpoint adds no new visual weather feature. It reviews the Phase 20A shared OpenGL chain after the cinematic governor, fault isolation, temporal smoothing and adaptive frame-time work.

## Static contract checks

- App module compiles against Java 11 source/target compatibility.
- Shared `HeroGlPipeline` still owns one active chain for App Hero and Live Wallpaper:
  Sky/Celestial -> Stars -> Texture Clouds -> World -> Atmosphere -> Storm/Lightning -> Depth Rain -> Snow.
- Every active renderer exposes the lifecycle methods used by the pipeline: `setSnapshot`, `onSurfaceCreated`, `onSurfaceChanged`, `drawFrame`, and `release`.
- The governor-controlled heavy passes (Clouds, Storm, Rain, Snow) expose `setDetailScale(float)` as required by `HeroGlPipeline`.
- `GlSceneSnapshot` retains the public constructor shape used by `GlRealityAdapter` while also providing package-local reusable mutation helpers used only by the GL pipeline.
- `GlSceneTransitionController` and renderer-specific reusable views remain allocation-free in the steady per-frame transition path.
- `AdaptiveFrameTimeGuard` imports and methods match both App Hero and Wallpaper render-thread callers.
- EGL recovery methods still recreate the pipeline only on actual failure and do not add normal-frame shader/program reconstruction.
- High-gust reality mapping calls `CurrentWeather.getWindGusts10m()`, and that getter exists in `WeatherResponse`.
- Active Phase 20A shader Java strings were re-read for separator/bracket continuity; no obvious Java string-assembly mismatch was found in the active Sky, Stars, Cloud, World, Atmosphere, Storm, Rain or Snow passes.

## Integration fixes made during this preflight

### Visual options no longer invalidate reality composition

`LiveSkyView` now versions visual options independently from weather/AQI/location data. Changing clouds/rain/lightning/snow/fog/stars wakes the render loop and updates `HeroGlPipeline.setOptions(...)`, but does not increment the weather truth version or force `GlRealityAdapter.compose(...)` / astronomy recomposition.

This removes a regression where a simple visual toggle could behave like a full scene refresh.

### Frame guard no longer treats normal vsync wait as overload

`AdaptiveFrameTimeGuard` still observes successful draw + swap cost, but pressure is now judged against the selected frame interval itself. `eglSwapBuffers` may legitimately wait for display presentation; that normal wait must not automatically downgrade detail on a healthy 60/90/120 Hz device.

The existing EWMA + hysteresis remains: sustained over-budget pressure steps secondary detail down, while stable headroom restores detail more slowly.

## Build/verification boundary

- No local Android Studio/Gradle build was run in this checkpoint.
- No GitHub automated status/check result was available for the latest source commit during the preflight.
- GLSL runtime compilation is still device/driver dependent and is protected by the Phase 20A.13 renderer isolation/recovery path.
- Phase 20A remains unaccepted until a real-device build and visual/GPU validation are completed.

This document is a source-level preflight record, not a claim that the app is Final, Production Ready, or build-verified.
