# ODM-4 — Moon + Stars + Night Scene Rebuild

Status: COMPLETE — system Live Wallpaper GPU base scene

## Purpose

ODM-4 upgrades the night/celestial presentation while preserving the existing astronomy and weather truth model. The renderer does not invent Moon positions, lunar phases, star visibility or daylight/night transitions.

## Reality Authority

- Sun and Moon altitude/azimuth: existing Astronomy Engine / SkyRealityEngine.
- Moon phase angle and illumination: existing astronomy state.
- Moon visibility: DynamicRealityComposer atmospheric/daylight contract.
- Star visibility: astronomy darkness combined with cloud, fog, precipitation, visibility and AQI haze.
- Weather/network work remains outside the render loop.

## Moon Rendering

- Continuous spherical lunar phase geometry from the real phase angle.
- No icon switching between crescent/quarter/gibbous/full states.
- Procedural maria and fine surface variation.
- Limb darkening and soft terminator.
- Restrained earthshine on the dark side at night.
- Slight warm atmospheric tint near the horizon.
- Local cloud attenuation plus the global atmosphere visibility contract.
- Illumination-aware halo that disappears when the Moon is not actually visible.

## Stars

- Three GPU star depth/brightness classes: faint, medium and bright.
- Deterministic field; no per-frame allocations or network work.
- Bright-star color-temperature variation.
- Subtle, low-amplitude twinkle rather than synchronized blinking.
- Horizon extinction, AQI/fog attenuation and localized Moon-glare suppression.
- Stars remain hidden in daylight and are controlled by the existing Stars wallpaper preference.

## Night Atmosphere

- Twilight/night depth still begins with SkyGradientProfile.
- Corrected horizon haze concentration toward the actual lower horizon instead of the upper sky.
- Restrained deep-night airglow only when astronomical darkness supports it.
- A visible illuminated Moon can add a small cloud/fog-attenuated global lunar fill to the night palette.
- New Moon, below-horizon Moon or atmospherically hidden Moon does not create fake lunar sky glow.

## Renderer Ownership After ODM-4

1. HeroGlCloudSceneRenderer — sky, cloud depth, Sun, Moon, stars and night atmosphere.
2. HeroGlStormOverlayRenderer — storm darkness, lightning and electrical cloud illumination.
3. HeroGlRainOverlayRenderer — rain, drizzle, wet screen and rain exposure response.
4. One EGL buffer swap.

Legacy rain/lightning fragment work was removed from HeroGlCloudSceneRenderer because those effects are already owned by dedicated overlay passes.

## Acceptance Contract

- Moon must remain phase-correct and position-correct.
- Thin crescent must not become a full dark/grey disc.
- Moon must not appear below the horizon.
- Stars must not appear decoratively in daylight/heavy atmospheric obstruction.
- Cloud cover must obscure celestial detail instead of celestial objects simply painting over clouds.
- Full/Gibbous Moon nights may be subtly brighter than New Moon nights, but never like daylight.
- Night scene must retain dark blue/indigo depth rather than a flat black or flat grey wash.
