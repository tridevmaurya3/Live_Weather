# Phase 19 — Forecast Pro

Status: IMPLEMENTED — device verification pending.

## Interactive hourly forecast

- Forecast hourly chips are selectable.
- The selected hour opens a rich detail summary with:
  - weather condition
  - temperature and feels-like
  - humidity and dew point
  - forecast precipitation probability
  - model precipitation / rain / showers / snow amount
  - wind direction / speed / gusts
  - cloud cover
  - visibility
  - pressure
- The hourly strip, temperature chart, precipitation chart and rain-risk timeline share one selected-hour state.

## Interactive responsive charts

- Temperature and precipitation charts support touch selection.
- Selected values are highlighted with a guide line/value label.
- Tapping either chart selects the matching hourly detail.
- X-axis label density adapts to available chart width to reduce overlap on small screens.
- Charts expose loading / unavailable / waiting empty-state messages.
- Temperature rendering continues to respect Phase 16 units.

## 12-hour forecast rain-risk timeline

- A compact 12-hour timeline is injected into the hourly forecast card.
- Every slot shows:
  - local forecast hour
  - weather symbol
  - precipitation probability
  - model precipitation amount
- Timeline wording explicitly says forecast probability/amount are not proof that rain is falling now.
- Timeline slots select the same hourly detail as the hourly strip and charts.
- Phase 18 current-vs-future precipitation semantics remain authoritative.

## Daily forecast expansion

- Up to 10 daily cards are rendered.
- Today starts expanded; tapping a day expands/collapses it.
- Daily header shows condition, H/L, peak precipitation probability and UV.
- Expanded detail includes:
  - actual H/L
  - feels-like H/L
  - precipitation probability and total
  - rain/showers/snow totals
  - wet hours
  - max wind / direction / gusts
  - UV
  - sunrise / sunset
  - sunshine / daylight duration
  - Moon phase / illumination / moonrise / moonset when astronomy coordinates are available

## Status and reliability states

- Live forecast
- Refreshing while retaining available data
- Saved/offline forecast
- Stale saved forecast when cache age exceeds three hours
- Forecast unavailable / retry state
- Waiting for location/data state

No network call is performed by ForecastProRenderer. It consumes the shared WeatherViewModel state and existing cached/provider data.

## Architecture

- `ForecastProRenderer` is a forecast-only presentation overlay.
- `ForecastProBinder` observes the existing activity-scoped `WeatherViewModel` once per MainActivity instance.
- Home and Live Wallpaper renderers are not rewritten by Phase 19.
- Binder tracking uses weak Activity keys and does not retain renderer values in a static map.

## Device verification checklist

1. Clean / Rebuild / Run.
2. Open Forecast page with live data.
3. Tap several hourly chips; selected detail must update.
4. Tap temperature chart and precipitation chart at different hours; selected detail and both chart highlights must stay synchronized.
5. Tap 12-hour rain-risk timeline slots; selected hourly detail must update.
6. Confirm probability is labelled as forecast risk, not current rain.
7. Expand/collapse Today and at least two future days.
8. Verify daily H/L, feels-like, precipitation, wind/gust, UV, sunrise/sunset and Moon events.
9. Change Phase 16 units and confirm hourly/daily/chart values follow the selected units.
10. Test on a small phone width and verify chart labels remain readable.
11. Turn network off with cached forecast and verify Saved/offline status.
12. Test loading/no-cache state and confirm clean waiting/unavailable UI without crash.

## Scope boundary

Phase 19 upgrades Forecast only. Radar is Phase 20 and Alerts are Phase 21.
