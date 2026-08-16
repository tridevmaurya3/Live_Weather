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

Status: SOURCE IMPLEMENTATION COMPLETE — build/device acceptance pending.

Implemented source checkpoints:
- Central `AlertTruthPolicy` for official-warning delivery/freshness truth
- Official network, network-empty, saved-cache, unavailable and not-applicable states
- Separate current check time from official warning-data timestamp
- `304 Not Modified` refreshes official validation time
- Stale saved official data cannot be presented as a live all-clear
- Stale official fallback cannot generate a new official notification
- Persistent Official / Smart Risk visibility filters
- Persistent minimum severity: All / Yellow+ / Orange+ / Red only
- Persistent per-source notification controls for Official and Smart Risk
- Master notification control remains separate from per-source delivery
- Android 13+ notification permission/app-level block handling
- Official and Smart Risk channel block-state handling
- Explicit `IMD OFFICIAL` vs `SMART RISK` notification labels
- Alert-row detail dialog with source/severity/area/validity/issue-time disclosure
- Android notification settings entry point
- Background worker exits early when alert notification delivery is disabled/blocked
- Background Smart Risk cannot be generated from cached weather older than 90 minutes
- Existing notification tap navigation continues to open the Alerts Center
- No false implication that an unavailable official source means no danger

Acceptance checkpoint: pull/build, permission flow, source/severity persistence, master + per-source notification controls, Android channel blocking, fresh/304/stale official states, alert details, notification navigation and stale-background guards on a real device.

## Phase 22 — App UX, Responsive & Accessibility Audit

Status: SOURCE IMPLEMENTATION COMPLETE — build/device/accessibility verification pending.

Implemented source checkpoints:
- Central `UiQualityPolicy` applied from the MainActivity lifecycle to static and dynamically-created views
- 48dp minimum interactive-target policy, including exact fixed-size controls below the baseline
- Radar Refresh/Recenter/layers/Replay/timeline controls hardened directly to 48dp in XML
- Responsive horizontal page padding for phone, 600dp+ and 840dp+ screen classes
- Large-text/narrow-screen reflow for weighted Home/Forecast/More card rows
- Large-text stacking for Home condition metadata, More city search and Radar location/Refresh rows
- Stacked cards switch to wrap-content height to avoid clipped text
- Home temperature bounded autosizing
- Adaptive Radar map minimum height for compact phones, normal phones and tablets
- Navigation label/icon readability increased without changing destination logic
- Accessibility pane titles and heading semantics for Home / Forecast / Radar / Wallpaper / More
- Explicit TalkBack descriptions for primary navigation/refresh/location/Radar/Wallpaper/City/Widget actions
- Dynamic clickable card fallback descriptions generated from bounded child text
- Decorative LiveSky surfaces and duplicate canvas chart surfaces excluded from noisy TalkBack focus
- Existing accessible Forecast selected-hour/hourly controls remain the semantic path for chart data
- Loading/checking/live/stale/error/retry states receive consistent semantic colors and polite accessibility live-region treatment
- Contrast audit retained the established atmospheric palette while promoting action/status text to stronger semantic roles
- Existing BottomNavigation selection/restoration, Radar lazy WebView lifecycle, Alerts truth, weather providers and Live Wallpaper truth were not rewritten
- See `PHASE_22_APP_UX_ACCESSIBILITY.md` for the full source-freeze and acceptance checklist

Acceptance checkpoint: Gradle Sync/debug build, normal/narrow phones, 600dp+ tablet, maximum available font scaling (including 200% where supported), TalkBack focus order, touch targets, Radar control reachability, dynamic Alerts/City/Settings cards, status/retry semantics and bottom-navigation restoration.

## Phase 23 — Offline, Cache & Data Reliability 2.0

Status: SOURCE IMPLEMENTATION COMPLETE — build/device/offline verification pending.

Implemented source checkpoints:
- Shared `DataReliabilityPolicy` for weather/AQI cache age, stale state and location matching
- Weather saved-data age bands: recent through 45 min, aging through 3 h, stale through 12 h, very stale beyond 12 h
- Selected saved city is resolved before startup cache publication so another city's active cache cannot flash on screen
- Refresh failures can retain only exact-request-location saved weather/AQI
- `WeatherCache.saveIfStillActive(...)` protects the active app/wallpaper pointer from old background responses
- Wallpaper background weather/AQI refresh cannot revert a location changed while its request was in flight
- Follow-active widget refresh cannot revert the active app/wallpaper location; fixed-city widgets remain isolated snapshots
- `AirQualityCache.saveSnapshot(...)` prevents stale-location AQI work from moving the generic AQI pointer
- Explicit WorkManager exponential 30-second backoff plus bounded retry budget for wallpaper and manual widget refresh work
- Persistent `RadarPersistentCache` for validated RainViewer metadata and per-location Open-Meteo model fields after process restart
- Persistent Radar metadata fallback bounded to 6 h and model-field fallback bounded to 3 h
- RainViewer host/frame safety is revalidated before persisted radar metadata can be reused
- Radar image tiles remain provider/network backed and are explicitly not represented as persistent offline tiles
- Added More-page `Data Reliability` diagnostics for cache ages, active coordinates, selected-city alignment, AQI/Radar cache state and cross-surface identity
- Diagnostics refresh reads local state only and does not start network work
- Corrupt per-location weather/AQI/Radar cache entries are discarded rather than used as valid state
- App + Live Wallpaper continue to share the active weather cache; fixed-city widgets retain separate snapshots
- See `PHASE_23_OFFLINE_CACHE_RELIABILITY.md` for the complete source contract and acceptance checklist

Acceptance checkpoint: Gradle Sync/debug build, online-to-offline restart, selected-city identity, in-flight city-switch race, saved weather/AQI age labels, app/widget/wallpaper alignment, fixed-widget isolation, bounded retry behavior, Radar restart fallback and More-page Data Reliability diagnostics.

## Phase 24 — Live Wallpaper Quality Backlog

Status: SOURCE IMPLEMENTATION COMPLETE — build/real-device visual acceptance pending.

Implemented source checkpoints:
- Shared `HeroGlTextureCloudRenderer` now uses cloud density as rendered mass, seamless horizontal wrapping, richer far/mid/near depth and continuous overcast shaping
- Cloud gust response adds bounded cross-drift/lift without changing current cloud truth
- Shared `HeroGlDepthRainRenderer` adds per-streak variation, stronger perspective/depth separation and wind sway
- Wet-glass response adds gravity-moving droplets, short trails and restrained lower-film ripple only during sufficient current precipitation
- Shared analytic world gains richer atmospheric terrain depth, deterministic foreground breakup and generic non-location-specific settlement silhouettes
- Rain/drizzle-gated wet-ground sheen/reflections improve surface realism without implying a real road/lake/landmark
- Storm renderer gains less repetitive strike placement, multi-frequency bolt geometry, detail-gated forks/companion channels and cloud-local electrical illumination
- Lightning remains gated by resolved current storm state and the user lightning visual option; forecast probability cannot create lightning
- Home/app LiveSky surfaces and Android Live Wallpaper continue to share `GlRealityAdapter -> GlSceneSnapshot -> HeroGlPipeline`
- `CinematicPerformanceGovernor` keeps the same shader detail scale for APP_HERO and LIVE_WALLPAPER within each tier; surface differences are frame pacing only
- Adaptive performance may remove secondary samples under load but cannot change weather truth
- Existing hidden-surface zero-frame rule, renderer fault isolation, bounded EGL recovery and network-free render hot path are preserved
- Source preflight fixed a world-shader `smoothstep` equal-edge corner case before freeze
- See `PHASE_24_LIVE_WALLPAPER_QUALITY.md` for the full renderer contract and visual acceptance matrix

Acceptance checkpoint: Gradle Sync/debug build plus real-phone comparison of dry, drizzle, heavy rain, storm/lightning, partly cloudy, overcast, high wind, day/night/twilight, fog/haze and Home Hero / Wallpaper preview / applied Live Wallpaper parity across Auto/Smooth/Battery modes.

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
