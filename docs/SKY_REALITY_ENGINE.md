# Live Weather — Sky Reality Engine Contract

## Product invariant

Live Weather is not a static forecast app with decorative sky graphics.
The app UI, widgets and Live Wallpaper must consume the same shared weather + astronomy reality state.

A visible Sun, Moon, stars, cloud layer, precipitation effect or scene brightness must be justified by the selected location, current time and current/forecast weather state. Random decorative celestial placement is not allowed in production rendering.

## Shared inputs

The Sky Reality Engine combines:

1. Active weather latitude / longitude.
2. Current UTC time and the location-aware weather timezone.
3. Accurate Sun and Moon astronomical calculations.
4. Moon phase and illuminated fraction.
5. Current cloud cover.
6. Current visibility.
7. Current precipitation / rain / snow / fog state.
8. Weather day/night state and sunrise/sunset forecast data.
9. Later performance/battery policy from the Smart Performance Engine.

## Astronomy source

Astronomy Engine for Kotlin/JVM is used from Java for local astronomical calculations.
It provides observer-relative Sun/Moon positions, Moon illumination/phase and other astronomy primitives without requiring a network request for every frame.

The astronomical calculation result is separate from weather obstruction:

- Astronomy answers where the Sun/Moon should be.
- Weather answers whether the sky is clear enough to see them strongly.
- The renderer combines both.

## Sky stages

The shared reality state differentiates:

- Daylight
- Golden hour
- Civil twilight
- Nautical twilight
- Astronomical twilight
- Astronomical night

The final rendering must transition continuously between these stages instead of switching between one hard-coded day image and one hard-coded night image.

## Sun

The final app and Live Wallpaper must use real observer-relative Sun position.

Examples:

- High Sun → bright sky and stronger daylight.
- Low Sun → warm horizon / golden-hour lighting.
- Sun just below horizon → twilight gradients.
- Sun far below horizon → astronomical night.

Cloud cover, fog, rain and storms can reduce or diffuse visible sunlight without changing the real astronomical Sun position.

## Moon

The final app and Live Wallpaper must use:

- Real Moon altitude / azimuth.
- Real Moon phase.
- Real illuminated fraction.
- Horizon visibility.

A Moon below the observer's horizon must not be rendered as if it were visible overhead.
Clouds/fog/precipitation may dim or hide an otherwise astronomically visible Moon.

## Stars

Stars must not be a permanently visible decorative texture.

Star intensity is controlled by at least:

- Sun altitude / astronomical darkness.
- Cloud cover.
- Visibility / fog.
- Precipitation.
- Moon illumination and Moon-above-horizon glare.

Current Phase 5 exposes a normalized star-visibility percentage. The later Dynamic World / Live Wallpaper renderer will consume this value and combine it with an astronomically oriented star catalogue / sky rotation model.

Expected examples:

- Daylight → stars hidden.
- Civil twilight → nearly hidden.
- Astronomical night + clear sky → strong star visibility.
- Overcast night → stars heavily reduced or hidden.
- Dense fog / heavy rain / storm → stars hidden.
- Bright Moon above horizon → fewer faint stars visible than on a dark Moonless sky.

## Scene light

The engine exposes an ambient scene-light percentage derived from astronomical daylight plus weather dimming and Moon contribution.

The final app shell, weather scene, widgets and Live Wallpaper will use this shared value for gradual brightness / atmosphere changes.

Examples:

- Clear noon → high ambient light.
- Thick daytime cloud → dimmer daylight.
- Sunset → progressively warmer and darker scene.
- Twilight → gradual blue-hour transition.
- Clear Moonlit night → subtle night illumination.
- Moonless overcast night → very dark scene.

This must be a gradual transition, not an abrupt day/night switch.

## Rendering separation

Network weather refresh must never run per animation frame.

- Weather/state refresh: scheduled/cached.
- Astronomy state: inexpensive local calculations at controlled intervals.
- Animation frame: reads the latest cached Reality State only.

This separation is required for battery efficiency.

## Phase 5 delivered foundation

Phase 5 provides:

- Accurate current Sun position state.
- Accurate current Moon position state.
- Moon phase / illumination state.
- Astronomical sky-stage state.
- Weather-aware star-visibility estimate.
- Weather/astronomy-aware ambient-light estimate.
- Sky Reality panel in Forecast.
- The same state surfaced in the Live Wallpaper preview text.

## Later rendering work

The later Live Wallpaper and Dynamic World phases will turn the shared state into full real-time visuals:

- Moving Sun and Moon.
- Astronomically oriented star field.
- Cloud occlusion in front of celestial objects.
- Fog / precipitation visibility loss.
- Sunrise/sunset/twilight color transitions.
- Night illumination influenced by Moon phase and cloud cover.
- Smooth scene-light transitions shared with the app's visual atmosphere.

The rule remains: one reality state, multiple surfaces.
