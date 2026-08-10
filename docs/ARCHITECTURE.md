# Live Weather Architecture

Live Weather is designed as a modular Java + XML Android application where the main weather app, home-screen widgets, live wallpaper engine, and background refresh system share one reliable weather state without becoming tightly coupled.

## Package map

The application will grow under `com.tridev.liveweather` using these package boundaries:

- `core` — app-wide constants, configuration, shared helpers and common primitives.
- `data.local` — cached and persistent local weather/location data.
- `data.remote` — weather/location network sources and transport mapping.
- `data.repository` — concrete repositories coordinating local and remote sources.
- `domain.model` — clean weather models shared by app UI, widgets and wallpaper.
- `domain.repository` — repository contracts consumed by presentation/rendering layers.
- `ui.home` — current-weather dashboard and main navigation surface.
- `ui.details` — hourly/daily forecast, weather details, AQI, sun/moon and radar UI.
- `ui.settings` — units, refresh, appearance, widget and wallpaper settings.
- `wallpaper` — WallpaperService, renderer, scene state, particles and weather effects.
- `widget` — Android home-screen weather widgets and widget update coordination.
- `worker` — battery-aware scheduled weather refresh and background orchestration.

## Dependency direction

`UI / Wallpaper / Widget -> Domain contracts & models -> Data repositories -> Local / Remote sources`

Presentation and rendering layers must not call weather APIs directly. Weather fetching, caching and refresh decisions will pass through repositories so the same trusted state can power the app, widgets and live wallpaper.

## Live-weather rendering principle

The live wallpaper must never depend on continuous network access. Network refresh produces a cached weather scene state; animation then runs locally from that state. For example, rain intensity, cloud cover, wind speed, daylight and thunder conditions become renderer inputs rather than per-frame API requests.

## Performance principles

- Offline-first cached weather state.
- No per-frame or continuous network access.
- Battery-aware background refresh.
- Wallpaper rendering only when visible.
- Adaptive animation quality/FPS based on device and battery conditions.
- One shared weather state for app, widgets and wallpaper to avoid duplicated work.

## Development rule

Each phase is added incrementally through GitHub. After every step the project must remain buildable, the user pulls the latest `main` branch into Android Studio, tests the step, and only then development continues.

## Current phase

Phase 0 establishes architecture and build foundations only. Functional weather data, widgets and live wallpaper implementations will be added in later phases.
