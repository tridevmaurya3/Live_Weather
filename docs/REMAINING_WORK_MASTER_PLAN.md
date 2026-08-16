# Live Weather — Remaining Work Master Plan

Status: ACTIVE DEVELOPMENT

This document is the forward roadmap after the Phase 15 production-foundation pass. The project must not be called Final / Production Ready / Release Candidate until this roadmap is completed and the user explicitly approves finalization.

## Phase 16 — Settings & Units Pro

Status: IMPLEMENTED — device verification pending.

Implemented:
- Temperature: Celsius / Fahrenheit
- Wind: km/h / mph / m/s / knots
- Pressure: hPa / mbar / inHg
- Precipitation: mm / inch
- Visibility: km / miles
- Persistent UnitPreferences
- Metric and Imperial presets
- Custom per-category unit selection
- Consistent display conversion across Home, Forecast, Details, Widgets, Radar labels and Smart Risk messages
- Forecast temperature chart unit conversion
- Functional Performance selector: Auto / Smooth / Battery
- Current Unit and Performance summaries on More page
- Provider/cache/risk thresholds remain metric internally

Acceptance checkpoint: clean/rebuild and real-device verification of Metric, Imperial, custom units, widget repaint, Radar labels and Performance modes.

## Phase 17 — Widget Pro

Status: IMPLEMENTED — device / launcher verification pending.

Implemented:
- Independent per-widget configuration
- Follow active weather or fixed saved-city source
- Fixed-city weather refresh without changing the app / wallpaper active-location pointer
- Glass and transparent widget appearance modes
- Compact and Forecast responsive resize-state handling
- Fresh / Saved / Stale / Refreshing / Offline status presentation
- Current body → Home, Forecast strip → Forecast, brand → reconfigure
- Manual refresh targets only the tapped widget source
- Widget-only refresh no longer fetches unused AQI data
- 30-minute network-constrained fixed-city scheduler
- Coordinate deduplication, 45-minute cache reuse and capped stale fixed-city refresh work
- Existing widget backward compatibility
- Per-widget preference cleanup and no-widget periodic-work cancellation
- Application-start recovery for previously placed widgets

Acceptance checkpoint: add Current + Forecast widgets, test active vs fixed city, appearance modes, resize, tap destinations, offline refresh, units repaint and removal cleanup.

## Phase 18 — Weather Intelligence 2.0

Status: IMPLEMENTED — device verification pending.

Implemented:
- Central WeatherIntelligence2 report shared by Home / Forecast details / Smart Risk wording
- Current precipitation evidence kept separate from hourly/daily forecast probability
- Rain likely/possible soon and later states without converting probability into Raining now
- Weak precipitation signal remains explicitly unconfirmed
- Adjacent previous/next 15-minute slots can corroborate but cannot independently become current rain/thunderstorm
- Current rain/snow/thunderstorm semantics kept distinct
- Feels-like / humidity / dew-point comfort explanation
- Sustained wind vs gust interpretation
- Visibility/fog interpretation without inventing an exact cause
- Short pressure trend explanation with a warning that pressure alone does not determine weather
- Higher / Standard / Limited model-consistency wording
- Forecast data freshness and saved/offline context in Advanced Details
- Forecast 24h wording changed to forecast rain-risk window
- Smart Risk heavy-rain-now requires confirmed current rain/showers
- Snowfall cannot trigger heavy-rain-now alert
- Daily heavy-rain potential explicitly remains a forecast risk when current rain is not confirmed
- Shared LiveConditionResolver accuracy improvement also protects app/Live Wallpaper current scene state

Acceptance checkpoint: verify dry-now + rain-later, weak trace, confirmed rain, Forecast Advanced Details, units, offline freshness and Smart Risk wording on device.

## Phase 19 — Forecast Pro

Status: IMPLEMENTED — device verification pending.

Implemented:
- Interactive 24-hour forecast strip with selected-hour state
- Rich selected-hour details: condition, temperature, feels-like, humidity, dew point, forecast precipitation probability/amount, wind/gust, clouds, visibility and pressure
- Interactive temperature and precipitation charts synchronized with selected hour
- Responsive chart label density and selected-value guide/highlight
- 12-hour forecast rain-risk timeline showing probability and model amount without implying rain-now
- Rain timeline, hourly strip and charts share one selection
- Expand/collapse 10-day forecast cards
- Daily H/L and feels-like H/L
- Daily precipitation probability/totals/wet hours
- Daily wind/gust/UV details
- Daily sunrise/sunset, sunshine/daylight duration
- Daily Moon phase/illumination/moonrise/moonset from CelestialForecastEngine
- Live / refreshing / saved-offline / stale / unavailable / waiting forecast states
- Forecast-only binder architecture without rewriting Home or Live Wallpaper renderer
- Weak activity binding marker to avoid static renderer retention

Acceptance checkpoint: test hourly chip/chart/timeline synchronization, daily expansion, Sun/Moon events, Phase 16 units, small-screen chart readability and offline/loading states.

## Phase 20A — Accurate Live Weather Reality Foundation

Status: SOURCE IMPLEMENTATION COMPLETE — real-device visual verification pending.

Implemented:
- Accurate current-condition truth remains the authority for every visual layer
- Photoreal weather-selected cloud atlas shared by app and Live Wallpaper
- Far/mid/near cloud depth, overcast coverage and real wind-driven motion
- Bounded verified-gust response without inventing storm state
- Depth-aware drizzle/rain with restrained wet-screen response
- Storm darkness, cloud-local lightning exposure and branched bolts
- Astronomy-preserving Sun/Moon/stars with smoother twilight transitions
- Layered fog/haze atmosphere and dedicated depth-aware snow
- Shared `HeroGlDiagnostics` with resolved evidence, active effects, GL/GPU identity,
  surface size and renderer quality label for real-device troubleshooting
- No square/grid cloud artifacts by design and no future probability presented as current weather
- See PHASE_20A_ACCURATE_LIVE_REALITY.md for checkpoint and acceptance details

Acceptance checkpoint: real-phone comparison of Home Hero vs applied Live Wallpaper across clear, partly cloudy, overcast, rain, storm, night, fog/haze, snow where available and high-gust scenes. Confirm `LiveWeatherGL` diagnostics agree with the displayed current weather.

## Phase 20B — Radar Pro

Status: SOURCE IMPLEMENTATION COMPLETE — Gradle Sync passed; debug build and real-device Radar acceptance pending.

Implemented source checkpoints:
- Observed RainViewer radar truth boundary and sanitized past timeline
- Continuous Open-Meteo model cloud surface without old circles/boxes
- Professional Rain / Model Clouds / Wind / Temperature controls and legends
- Timestamp-aware observed timeline and reusable playback layer
- Freshness, source provenance and cache/fallback states
- WebView lifecycle/memory hardening and bounded recovery
- OSM/RainViewer tile-delivery health guard
- Bundled Leaflet runtime with no runtime CDN dependency for the map engine
- Source freeze / pull-readiness pass and subsequent Gradle Sync compatibility fixes

Acceptance checkpoint: debug build, real-phone Radar layers, replay/timeline, refresh/recenter, tab lifecycle, weak/offline network tile warning and local Leaflet startup verification.

## Phase 21 — Alerts Pro

Status: IMPLEMENTATION STARTED — Step 21.1 source implementation complete; build/device verification pending.

Step 21.1 implemented:
- Central `AlertTruthPolicy` for official warning source delivery/freshness state
- Official network, network-empty, saved cache, unavailable and not-applicable states
- Separate current check time from official warning-data timestamp
- Stale saved official data cannot be shown as a live all-clear
- `304 Not Modified` refreshes official validation time
- Stale official fallback alerts are excluded from new notification candidates
- Alerts Center empty-state wording is source/freshness aware
- Saved stale official warnings are visibly marked `SAVED OFFICIAL`
- Smart Risk remains explicitly app-derived and separate from official warnings

Remaining Phase 21 scope:
- Alert settings surface
- Official warning vs Smart Risk filters
- Severity preferences
- Notification preferences and clear source labels
- Alert detail/navigation polish
- Background/cached/stale warning-state hardening
- Final Alerts Pro integration and device verification

## Phase 22 — App UX, Responsive & Accessibility Audit

- Home / Forecast / Radar / Wallpaper / More full visual consistency
- Tablet/small-phone/large-phone layout audit
- Text scaling audit
- Touch-target audit
- TalkBack/content-description audit
- Contrast/readability audit over live backgrounds
- Loading/empty/error/retry consistency
- Navigation-state consistency

## Phase 23 — Offline, Cache & Data Reliability 2.0

- Cache age visibility
- Stale-data rules
- Active-city/cache identity audit
- Widget/app/wallpaper cache consistency
- Network-off behavior
- Retry/backoff review
- Location fallback behavior
- Data diagnostics page/section for troubleshooting provider/time/location mismatches

## Phase 24 — Live Wallpaper Quality Backlog

Current cross-device analytic renderer is stable and should not be repeatedly redesigned casually.

Only address this phase deliberately. Backlog includes:
- Rain depth/naturalness
- Wet-glass and surface-reflection quality
- Storm/lightning quality
- Cloud realism
- Background-world realism
- App preview vs applied wallpaper parity

The existing stable renderer remains the fallback checkpoint.

## Phase 25 — Product Completeness Audit

- Compare every implemented page/feature against the original Live Weather design reference
- Identify missing pages/actions/settings
- Remove placeholder/static cards
- Remove stale phase/development wording from user-facing UI only after all work is complete
- Full real-device regression test

## Phase 26 — Final Release Gate

This phase may start ONLY after explicit user approval that feature development is complete.

Required:
- Debug build pass
- Release/R8 build pass
- Real-device smoke test
- Widgets test
- Live Wallpaper test
- Radar test
- Alerts test
- Offline/cache test
- Final version/code decision
- Play Store/release preparation if requested

## Rule

Do not call the project Final, Complete, Production Ready or Release Candidate before Phase 26 and explicit user approval.
