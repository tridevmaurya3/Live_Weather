# ODM-5 — Final Cinematic Atmosphere + App/Wallpaper Match

Status: CODE COMPLETE — FINAL REAL-DEVICE VISUAL ACCEPTANCE PENDING

## Visual reference

The original generated “Live Weather Wallpaper / Weather App — All Pages Design” concept remains the visual acceptance reference for the Hero experience. ODM-5 does not claim camera/video reality or exact photorealism. The target is a high-fidelity, live-feeling GPU/procedural atmosphere driven by real weather, astronomy, time and cached environmental state.

## The architecture gap ODM-5 closes

Before ODM-5, Android system Live Wallpaper used the OpenGL engine while the in-app `LiveSkyView` still used the older Canvas Hero stack. That meant the app and wallpaper could never remain visually identical even when they shared the same weather data.

ODM-5 replaces the in-app Canvas scene with a `TextureView` + EGL14 OpenGL ES 2.0 surface and centralizes rendering in `HeroGlPipeline`.

Both the app and system Live Wallpaper now use the same rendering ownership and order:

1. `HeroGlCloudSceneRenderer` — sky, Sun, Moon, stars and cloud depth.
2. `HeroGlAtmosphereOverlayRenderer` — distance atmosphere, horizon scattering, fog/haze/rain veil, lunar fill and restrained optical vignette.
3. `HeroGlStormOverlayRenderer` — storm atmosphere, localized cloud illumination and electrical effects.
4. `HeroGlRainOverlayRenderer` — drizzle/rain depth, heavy-rain curtain, wet glass, sliding droplets, trails and lower wet film.

## Shared reality contract

Rendering never decides whether weather is occurring. The pipeline consumes the existing normalized `GlSceneSnapshot`, produced by `GlRealityAdapter` from `DynamicRealityComposer`.

The shared reality state continues to own:

- confidence-aware current weather,
- cloud presence and layer amounts,
- precipitation intensity,
- visibility/fog/AQI haze,
- wind speed and direction,
- Sun/Moon astronomical altitude and azimuth,
- Moon phase angle and illumination,
- astronomical star visibility,
- scene light.

No network request is made per frame.

## App / wallpaper parity

`HeroGlPipeline` is now shared by:

- Android `LiveWeatherWallpaperService` through `GlWallpaperRenderThread`,
- the global in-app live background,
- Forecast live-sky preview,
- Live Wallpaper settings preview.

The in-app `LiveSkyView` retains its public API so the rest of the application does not need a separate weather integration. App LiveSkyView instances share the same current weather/AQI/location/options state and hidden preview surfaces stop their animation loop.

## Cinematic atmosphere pass

`HeroGlAtmosphereOverlayRenderer` is intentionally restrained and procedural. It adds only effects justified by the resolved scene state:

- denser atmosphere toward the actual lower horizon,
- fog and AQ haze distance veil,
- precipitation atmosphere without replacing rain particles,
- low-Sun warm scattering,
- illuminated visible-Moon cool fill at night,
- storm lower-atmosphere depth,
- subtle edge vignette for optical depth.

It does not add buildings, mountains, roads, camera footage or fake local scenery.

## Preview polish

The app-wide GPU background has been made more visible while retaining a dark readability overlay for cards and text. Forecast and Wallpaper GPU previews are clipped to their drawable/card outlines so the OpenGL surface does not appear as an artificial rectangular cutout.

## Lifecycle and performance contract

- App and wallpaper OpenGL work runs away from the UI thread.
- Network/cache fetching is outside the render loop.
- Reality/astronomy state refreshes periodically, not every frame.
- Hidden app preview surfaces stop scheduling frames.
- System wallpaper stops rendering while Android reports it hidden.
- EGL surface recreation reuses shader programs while the context remains alive; programs are released once with the context lifecycle.
- Rain and storm passes early-out when their phenomena are inactive.
- Battery-adaptive frame cadence remains controlled by the system wallpaper service.

## Asset / copyright contract

No artwork or texture from the externally inspected reference APK is copied into Live Weather. That app was studied only for rendering architecture ideas. Live Weather uses original procedural/GPU rendering.

## Final acceptance checklist

Final real-device acceptance should evaluate the complete scene, not isolated substeps:

- no rectangular/square cloud artifacts,
- no cartoon/scalloped cloud rows,
- clear/partly-cloudy/overcast cloud presence agrees with current conditions,
- multi-depth cloud motion is visible without a single scrolling grey sheet,
- drizzle/rain/heavy rain are visually distinct,
- wet-screen effects do not look like repeated tiles,
- storm lightning is short, irregular and localized rather than a full-height glowing cable,
- Moon position and phase agree with astronomy state,
- Moon and stars are not shown decoratively when reality state says they should be hidden,
- night retains depth rather than becoming flat black/grey,
- in-app scene and Android Live Wallpaper have the same visual language and rendering behavior,
- UI cards/text remain readable over the live scene,
- no severe jank, overheating or ANR regression.

ODM-1 through ODM-5 are code-complete only after this stage. Exact visual acceptance against the original generated concept remains a real-device decision.
