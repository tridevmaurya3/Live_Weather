# Phase 20A — Accurate Live Weather Reality Foundation

Status: IMPLEMENTATION STARTED — real-device visual verification required.

## Product priority

Accurate Live Weather in Real Feeling is the app's Hero Part. Radar and later
feature work must preserve the same current-condition truth used by Home and
Live Wallpaper. The project is not Final or Production Ready.

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

## Acceptance required

Test on a real phone in clear, partly cloudy, overcast, rain, storm and night
conditions. Compare Home preview and applied Live Wallpaper at the same time and
city. Confirm clouds have soft natural masses, no rectangular tiles, continuous
motion and no abrupt celestial disappearance. Source implementation alone is
not visual acceptance.

## Remaining Phase 20A work

- Tune rain depth, droplets and wet-screen response from real-device recordings.
- Tune storm darkness, lightning exposure and bolt scale.
- Verify Moon/Sun/stars across cloud cover and twilight boundaries.
- Verify dust/haze/fog/snow and high-gust scenes.
- Add device diagnostics for active weather evidence and renderer quality mode.
