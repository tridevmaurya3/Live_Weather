# Stage 15 — Measured Visibility & Distance-Layer Atmospheric Perspective

## Goal
Make the rendered environment respect real horizontal visibility as a distance-depth signal, so 2–4 km visibility no longer looks almost identical to a clear 16 km scene, while preserving all Stage 1–14 weather truth and existing renderer connections.

## Existing truth reused
No provider variable or network request is added. Stage 15 consumes the existing smoothed `GlSceneSnapshot.visibilityFactor`, which already comes from `AtmosphericDepthReality` and combines:

- provider visibility
- bounded humidity/dew-point distance softening
- AQI haze attenuation

Fog, AQI haze, rain, snow, storm and weather codes remain separate authoritative signals.

## New atmospheric perspective policy
`AtmosphericPerspectivePolicy` converts the normalized visibility factor into four presentation-only transmission channels:

- far terrain transmission
- mid terrain transmission
- near foreground transmission
- secondary micro-detail visibility

The mapping is distance ordered: far detail disappears first, then mid detail, while nearby objects retain useful readability even at very low visibility.

### Clear-weather compatibility
A visibility factor of `1.0` returns exactly `1.0` for every channel. Therefore clear scenes keep the Stage 14 appearance instead of receiving an always-on atmospheric filter.

### Low-visibility safety
At the minimum supported visibility factor, far terrain can become strongly attenuated, but near foreground transmission retains a safe floor. This avoids turning a realistic low-visibility daytime scene into an unreadable black or flat screen.

Invalid NaN/infinite renderer input fails neutral rather than blackening the scene.

## Existing world shader integration
The existing `HeroGlAnalyticWorldRenderer` receives four additional scalar uniforms. No new texture, framebuffer or fullscreen draw pass is introduced.

The policy affects:

- far/mid/near terrain alpha by different amounts
- forest/canopy depth between mid and near distance
- ridge micro-detail visibility
- distant terrain rim, solar and moon edge contrast
- terrain snow highlights at the same physical distance
- designed settlement/structure depth
- existing high-frequency scenery detail through the micro-visibility channel

Foreground ground, retained wetness, puddles and weather truth remain intact.

## Interaction with fog and haze
Stage 15 does not convert reduced visibility into fake fog or fake AQI haze. Existing fog/haze shader attenuation remains independent, while measured visibility adds a separate distance-transmission layer.

This means:

- WMO/current fog still owns fog truth
- AQI still owns aerosol haze truth
- heavy rain can still reduce visibility through provider visibility without being relabeled as fog
- future forecast visibility does not change current atmospheric perspective unless it becomes part of the current resolved snapshot

## Shared App Hero + Live Wallpaper
Both surfaces already use the same `GlSceneSnapshot`, transition controller and `HeroGlAnalyticWorldRenderer`, so measured distance clarity stays unified automatically.

`GlSceneTransitionController` already smooths `visibilityFactor`; Stage 15 reuses that signal and does not add a second transition system.

## Performance
The new policy uses only a few scalar arithmetic operations and one reusable sample object. It performs no allocation in the frame loop.

GPU cost is four scalar uniforms and simple multiplications inside the existing world pass. Draw-call count, textures and framebuffer count are unchanged.

## Regression coverage
Tests verify:

- clear visibility is exactly neutral
- reduced visibility attenuates far > mid > near in physical order
- dense visibility loss still preserves near-world readability
- every transmission channel changes monotonically with visibility
- invalid values fail neutral
- out-of-range values stay bounded

## Truth safeguards
- No weather-code changes.
- No fake fog, haze, rain, snow or storm.
- No alert changes.
- No provider/network changes.
- No location/cache/database/scheduler changes.
- No manual scenery behavior changes.
- No extra GL render pass.
