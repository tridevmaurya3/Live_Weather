# Live Weather — Sky Reality Engine Contract

## Product invariant

Live Weather is not a static forecast app with decorative sky graphics.
The app UI, widgets and Live Wallpaper must consume the same shared weather + astronomy reality state.

A visible Sun, Moon, stars, cloud layer, precipitation effect or scene brightness must be justified by the selected location, current time and current/forecast weather state. Random decorative celestial placement is not allowed in production rendering.

## Shared inputs

The Sky Reality Engine combines:

1. Active weather latitude / longitude.
2. Current UTC time and location-aware timezone.
3. Observer-relative Sun and Moon astronomy.
4. Moon phase and illuminated fraction.
5. Sun/Moon rise and set events.
6. Current cloud cover.
7. Current visibility.
8. Precipitation-first live condition state.
9. Weather day/night state and sunrise/sunset forecast data.
10. Later performance/battery policy from the Smart Performance Engine.

## Astronomy source

Astronomy Engine for Kotlin/JVM is used from Java for local astronomical calculations.
It provides observer-relative Sun/Moon positions, Moon illumination/phase and rise/set primitives without requiring a network request for every animation frame.

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

The app and Live Wallpaper must use real observer-relative Sun position.

Phase 6 live preview recalculates the Sun's current position from the active coordinates and clock every 30 seconds while the preview is attached. This is a local astronomy calculation; it does not fetch weather every 30 seconds.

Examples:

- High Sun → bright sky and stronger daylight.
- Low Sun → warm horizon / golden-hour lighting.
- Sun just below horizon → twilight gradients.
- Sun far below horizon → astronomical night.

Cloud cover, fog, rain and storms can reduce or diffuse visible sunlight without changing the real astronomical Sun position.

## Moon

The app and Live Wallpaper must use:

- Real Moon altitude / azimuth.
- Real Moon phase.
- Real illuminated fraction.
- Horizon visibility.
- Location-aware moonrise / moonset.

Phase 6 adds a 10-day Moon progression strip. Each day calculates its own phase, illuminated percentage and local moonrise/moonset events. The live preview recalculates the current Moon position every 30 seconds from the active location and clock.

A Moon below the observer's horizon must not be rendered as if it were visible overhead.
Clouds/fog/precipitation may dim or hide an otherwise astronomically visible Moon.

## Stars

Stars must not be a permanently visible decorative texture.

Star intensity is controlled by at least:

- Sun altitude / astronomical darkness.
- Cloud cover.
- Visibility / fog.
- Precipitation-first live condition.
- Moon illumination and Moon-above-horizon glare.

The current normalized star-visibility percentage is consumed by the Phase 6 live preview. The later Dynamic World / Live Wallpaper renderer will combine it with an astronomically oriented star catalogue / sky rotation model.

Expected examples:

- Daylight → stars hidden.
- Civil twilight → nearly hidden.
- Astronomical night + clear sky → strong star visibility.
- Overcast night → stars heavily reduced or hidden.
- Dense fog / heavy rain / storm → stars hidden.
- Bright Moon above horizon → fewer faint stars visible than on a dark Moonless sky.

## Precipitation consistency

The visual sky must use the same precipitation-first condition resolver as the weather dashboard.

If current/nearest 15-minute precipitation signals indicate rain while a raw weather code is clear, the stronger rain signal can override the clear presentation. The same resolved condition drives Home, Forecast current summary and the live Wallpaper preview.

## Scene light

The engine exposes an ambient scene-light percentage derived from astronomical daylight plus weather dimming and Moon contribution.

The app shell, weather scene, widgets and Live Wallpaper use this shared value for gradual brightness / atmosphere changes.

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

Phase 6 preview uses a 30-second astronomy tick while the view is attached. Full wallpaper animation will use a battery-aware renderer and will not trigger a weather API request for every celestial-position update.

## Phase 6 delivered foundation

Phase 6 provides:

- Live Sun position preview.
- Live Moon position preview.
- Daily Moon phase / illumination progression.
- Sun rise/set and Moon rise/set calculations.
- Weather-aware stars and scene brightness in the live preview.
- Precipitation graphics tied to the shared resolved current condition.
- A shared live preview on Forecast and Wallpaper screens.

## Later rendering work

The later Live Wallpaper and Dynamic World phases will turn the shared state into the final Android home-screen renderer:

- Android WallpaperService integration.
- Smoother Sun and Moon movement.
- Astronomically oriented star catalogue / sky rotation.
- Cloud layers moving in front of celestial objects.
- Fog / precipitation visibility loss.
- Sunrise/sunset/twilight color transitions.
- Night illumination influenced by Moon phase and cloud cover.
- Smooth scene-light transitions shared with the app's visual atmosphere.

The rule remains: one reality state, multiple surfaces.
