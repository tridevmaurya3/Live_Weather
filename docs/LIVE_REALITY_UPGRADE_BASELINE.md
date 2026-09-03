# Live Reality Upgrade — Stage 1 Baseline

Status: COMPLETE IN SOURCE — CI and real-device verification required.

This document locks the working product contracts that must survive the Live
Reality upgrade. Stage 1 intentionally changes no runtime weather behaviour,
screen layout, provider connection, cache schema, widget behaviour or renderer.

## Baseline source

- Branch: `main`
- Inspected head before Stage 1: `6d87fe1c252df039bdddc3e6e04bfb073885f39c`
- Product version: `1.0.0` (`versionCode 1`)
- Android range: API 26 through target API 36

## Provider and truth contracts

These boundaries are invariants for every later stage:

1. Open-Meteo supplies current, 15-minute, hourly and daily model weather.
2. RainViewer past frames are observed radar; they are never labelled forecast.
3. Open-Meteo radar overlays are model fields, not observed radar.
4. IMD/CAP alerts remain separate from app-derived smart-risk guidance.
5. A weak or adjacent precipitation trace alone must not start current rain.
6. Cached data must retain its age/stale label and must not be presented as live.
7. A response for an old location must not overwrite a newly selected location.
8. A fixed saved city must never be replaced by device-current location.

## Shared surface contracts

- Home and Live Wallpaper consume the same active weather cache.
- Fixed-city widgets keep isolated snapshots.
- App, widgets and wallpaper retain their existing navigation and actions.
- Sun, Moon, lunar phase and star visibility remain location/time based.
- Auto, Smooth and Battery rendering modes remain available.
- Radar keeps its local Leaflet runtime and provider attribution.

## Protected user-visible behaviour

- Home, Forecast, Radar, Wallpaper and More remain the five destinations.
- Manual refresh remains available.
- Saved-city search, save, use, remove and current-location controls remain.
- Offline weather remains visible when available with truthful freshness text.
- Approximate location remains supported when precise location is not granted.
- Alert, widget and wallpaper entry points remain registered.

## Regression matrix for every later stage

| Area | Required check |
| --- | --- |
| Home | Current/saved location, refresh, condition, details and quick actions |
| Forecast | Hourly selection, charts, 10-day expansion, Sun/Moon and units |
| Radar | Observed frames, model layers, replay, refresh, recenter and lifecycle |
| Alerts | Official/smart separation, filters, stale wording and notification tap |
| Widgets | Active/fixed city, resize, refresh, taps and removal cleanup |
| Wallpaper | Preview/apply, cache parity, weather gating and renderer recovery |
| Offline | Restart, cache age, selected-city identity and no false live claim |
| Location | Precise, approximate, denied, unavailable and city/device isolation |
| Accessibility | Narrow screen, large text, TalkBack labels and touch targets |
| Performance | Auto/Smooth/Battery modes, background/foreground and no crash |

## Stage acceptance rule

A later stage is accepted only when:

1. its change is limited to that stage's stated scope;
2. automated tests and Android build/lint pass;
3. the regression matrix has no known failure;
4. data source and freshness wording remain truthful; and
5. the commit summary lists changed behaviour, preserved behaviour and the next stage.

## Next stage

Stage 2 will add the foreground Live Data Freshness Engine: lifecycle-aware
adaptive refresh, duplicate-request protection, unified freshness metadata and
explicit network timeout/retry policy. It must not yet alter location movement,
reality fusion or visual rendering.
