# Phase 24 — Live Wallpaper Quality Backlog

Status: SOURCE IMPLEMENTATION COMPLETE — build/real-device visual acceptance pending.

## Product contract

Phase 24 upgrades visual quality in the active shared OpenGL pipeline only. It does not change weather-provider truth, Phase 23 cache identity, Forecast probability semantics, Alert truth, Radar truth, or location selection.

The same active renderer path remains authoritative for both the in-app LiveSky surfaces and the Android system Live Wallpaper:

`Weather/AQI -> GlRealityAdapter -> GlSceneSnapshot -> HeroGlPipeline`

The app Hero and applied wallpaper therefore share the same cloud, world, atmosphere, storm, rain and snow passes. Surface-specific performance policy may change frame pacing and may reduce secondary shader samples under sustained pressure, but it must not invent/remove the resolved current weather condition.

## 24.1 — Rain depth and naturalness

Updated `HeroGlDepthRainRenderer`:
- keeps separate drizzle/far/mid/near depth bands;
- adds per-streak width/length/speed variation from the deterministic noise texture;
- adds wind-dependent sway instead of rigid parallel motion;
- adds a lower-screen perspective response so near streaks read closer without changing rain truth;
- retains governor-controlled near-band and secondary-noise detail;
- keeps the pass zero-output when both current rain and drizzle evidence are absent.

Forecast precipitation probability is not consumed by this renderer and cannot activate current rain.

## 24.2 — Wet-glass treatment

The shared rain pass now includes a restrained wet-glass material response during sufficiently strong current precipitation:
- gravity-moving screen droplets;
- irregular droplet rims/highlights;
- short trailing water paths;
- lower wet-film accumulation;
- moving lower-film ripple/specular variation.

The treatment is intentionally bounded so ordinary drizzle does not become an exaggerated windshield effect. Lower-detail performance states may remove the secondary droplet layer while retaining primary rain evidence.

## 24.3 — Wet ground / surface reflections

Updated `HeroGlAnalyticWorldRenderer`:
- wet-ground sheen activates only from resolved current rain/drizzle;
- reflection ripple bands move subtly with time;
- generic night settlement lights may create a restrained wet reflection when precipitation is actually present;
- dry weather produces no rain-driven wet-ground reflection.

This is a material/light response, not a claim that a particular real location contains a road, lake or landmark.

## 24.4 — Storm and lightning quality

Updated `HeroGlPortableStormRenderer`:
- strike position and availability vary deterministically by time cell and storm strength;
- main bolt has multi-frequency path breakup and broad glow;
- higher detail tiers may add forks and a rare companion channel;
- multi-pulse flash remains short and non-continuous;
- cloud-local electrical illumination is driven by cloud density/storm ceiling/near-cloud mass;
- a restrained horizon response helps lightning belong to the world instead of looking pasted over the screen;
- storm darkness remains linked to current storm/rain/cloud truth;
- the electrical option can disable lightning without altering the underlying weather snapshot.

No lightning is generated from forecast probability alone.

## 24.5 — Cloud realism and continuity

Updated `HeroGlTextureCloudRenderer` while retaining the existing photoreal cloud atlas:
- `cloudDensity` now contributes directly to rendered mass rather than being effectively unused;
- far/mid/near opacity responds to both cover and density;
- atlas sprites use horizontally wrapped placement to avoid edge pop when drifting off-screen;
- far/mid/near layers use distinct speed, scale, tint and vertical response;
- gusts add bounded cross-drift/lift variation without changing cloud type;
- secondary sprites are governor-controlled;
- high-cover/high-density scenes receive a continuous overcast ceiling/sheet;
- underside shading adds depth for rain/storm/low-brightness cloud decks;
- texture sampling remains local GPU work with no network/cache access in the frame path.

The cloud atlas remains the same packaged app asset; no runtime CDN/image dependency was added.

## 24.6 — Background-world realism

Updated `HeroGlAnalyticWorldRenderer`:
- richer far/mid/near terrain silhouettes using multiple spatial frequencies;
- deterministic foreground tree/forest breakup;
- restrained generic settlement silhouettes rather than location-specific fabricated landmarks;
- stronger atmospheric distance separation using scene light, fog/haze and lunar light;
- night lights remain subtle and suppressed by fog;
- wet-ground response is precipitation-gated.

A shader edge case where foreground-tree shaping could produce equal `smoothstep` edges was caught in source preflight and fixed before source freeze.

## 24.7 — App preview vs applied wallpaper parity

Source audit confirms:
- `LiveSkyView` owns a `HeroGlPipeline` and composes reality using `GlRealityAdapter`;
- `GlWallpaperRenderThread` owns the same `HeroGlPipeline` class and composes reality using the same `GlRealityAdapter`;
- both consume the same `WallpaperPreferences.Options` categories for rain/clouds/lightning/snow/fog/stars;
- visual options are separate from weather/AQI truth and do not force a fake weather recomposition;
- both refresh reality on a bounded cadence rather than performing network/cache parsing inside the frame hot path;
- Android wallpaper parallax is an expected surface difference, not a different weather renderer.

`CinematicPerformanceGovernor` uses the same shader detail scale for APP_HERO and LIVE_WALLPAPER within each tier. Surface differences are frame intervals only:
- Cinematic: same detail, app may target a slightly faster frame interval;
- Balanced: same detail and same nominal interval;
- Eco: same detail, wallpaper may use a slower interval for battery savings.

`AdaptiveFrameTimeGuard` may independently reduce secondary detail when a particular surface is under sustained GPU pressure. That is an intentional performance safeguard; primary weather truth remains unchanged.

## 24.8 — Lifecycle and hot-path invariants

Preserved:
- hidden in-app LiveSky surfaces render zero frames;
- invisible Live Wallpaper renders zero frames;
- weather/network refresh stays outside the render loop;
- no cache JSON parsing was added to the GPU frame path;
- no new per-frame Java object allocation loop was introduced by Phase 24;
- existing renderer fault isolation remains in `HeroGlPipeline`;
- existing bounded EGL recovery remains on app and wallpaper surfaces;
- performance governor still controls only frame pacing/secondary detail.

## 24.9 — Source integration preflight

Checked after remote writes:
- cloud shader declarations match Java uniform lookups/uploads;
- rain shader declarations match Java uniform lookups/uploads;
- storm shader declarations match Java uniform lookups/uploads;
- world `uTime` declaration, lookup and upload are aligned;
- all new effects use existing `GlSceneSnapshot` truth fields;
- no `GlSceneSnapshot` constructor/signature migration was required;
- no `HeroGlPipeline` draw-order rewrite was required;
- app and wallpaper therefore automatically receive the same upgraded renderer classes;
- no Phase 23 reliability files were changed;
- no Phase 25 product-completeness work was started.

## Real-device acceptance gate still required

After pull/build, verify on a real phone:

1. Clear/dry scene: no rain streaks, wet glass or rain-driven ground sheen.
2. Light drizzle: fine/far rain appears without heavy near streaks or exaggerated droplets.
3. Moderate/heavy rain: far/mid/near depth is visible and wind changes streak angle naturally.
4. Heavy rain: wet glass appears restrained, droplets move rather than remaining frozen, and lower film does not obscure the whole scene.
5. Storm: cloud deck darkens naturally; electrical flashes are intermittent rather than periodic screen-wide blinking.
6. Lightning: bolt/branches remain cloud-connected and do not appear when lightning visual option is disabled.
7. Partly cloudy: atlas clouds cross screen edges without obvious pop/reset.
8. Overcast: cloud deck reads continuous rather than as isolated repeated sprites.
9. High wind/gust: cloud motion responds without teleporting or becoming unnaturally fast.
10. Day/night/twilight: world depth remains readable and Sun/Moon/stars keep their existing truth/occlusion behavior.
11. Rain at night: wet-ground sheen/reflections remain subtle and do not look like a mirror.
12. Fog/haze: new world layers do not punch through atmosphere unnaturally.
13. Home Hero vs Wallpaper-page preview vs applied Android Live Wallpaper: same condition, cloud family, rain/storm presence and visual-option state.
14. Performance Auto/Smooth/Battery: secondary detail may reduce, but weather condition/effects must not change truth.
15. Home hidden / wallpaper invisible: confirm no unexpected battery-heavy rendering regression.
16. Check `LiveWeatherGL` diagnostics/logs if a device reports EGL/shader renderer failure.

## Verification boundary

- All Phase 24 source changes are on `main` only.
- No new branch was created.
- Source implementation is complete.
- No local Android Studio build, GPU shader compilation on the user's device, or real-device visual acceptance has been run for these final Phase 24 changes from this environment.
- Phase 25 has not started.
