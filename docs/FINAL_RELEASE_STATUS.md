# Live Weather — Pre-Release Status

Current product version: 1.0.0

Status: ACTIVE DEVELOPMENT — Phase 25 source implementation complete; Phase 26 Final Release Gate not started.

## Important boundary

The project is **not** yet labelled Final, Complete, Production Ready or Release Candidate.

Phases 16 through 25 have now been implemented at source level, and the current project debug-build checkpoint has passed with those source changes included. Full real-device regression and the release/R8 gate still remain.

## Implemented product scope

The current product includes:
- current weather and location-aware dashboard;
- saved cities and current-location switching;
- advanced hourly and 10-day Forecast Pro;
- weather intelligence with current-vs-forecast precipitation truth separation;
- AQI and Sun/Moon intelligence;
- official-warning / Smart Risk Alerts Pro;
- Radar Pro with observed RainViewer radar and model Clouds/Wind/Temperature layers;
- configurable home-screen widgets;
- Units and Performance settings;
- offline/cache/data-reliability protections and diagnostics;
- responsive/accessibility policy;
- shared cinematic OpenGL Hero and Android Live Wallpaper pipeline;
- Phase 24 rain/cloud/storm/wet-surface/world-quality upgrades;
- Phase 25 product-completeness wiring and user-facing wording cleanup.

## Current verification state

Confirmed:
- current project Gradle Sync/debug integration checkpoint has passed locally;
- Phase 25 source is included in that current source line;
- versionName is 1.0.0;
- the five primary destinations are Home, Forecast, Radar, Wallpaper and More;
- active product UI is no longer intentionally labelled as a development/roadmap screen.

Still required before release approval:
- full real-device product regression;
- runtime OpenGL/Live Wallpaper visual verification;
- Radar weak/offline network and provider-tile behavior;
- alerts/notification permission and channel behavior;
- widget launcher/configuration behavior;
- offline/cache restart and city-switch race checks;
- narrow/large-text/TalkBack smoke checks;
- Release/R8 build and shrinker validation;
- final versionCode/versionName/release packaging decision.

## Phase 26 — Final Release Gate

Phase 26 has **not** started.

It may begin only on explicit user instruction. It will be the final validation gate, not another feature-development phase.

Required gate:
1. latest Debug build;
2. Release/R8 build;
3. real-device smoke/regression;
4. Widgets;
5. Live Wallpaper/OpenGL;
6. Radar;
7. Alerts/notifications;
8. offline/cache reliability;
9. final version decision;
10. Play Store/release preparation if requested;
11. explicit user approval before final status.

## Completion rule

Do not call this project Final, Complete, Production Ready or Release Candidate until Phase 26 is completed and the user explicitly approves finalization.
