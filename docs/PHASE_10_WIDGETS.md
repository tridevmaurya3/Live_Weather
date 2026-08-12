# Phase 10 — Weather Widgets

Status: IMPLEMENTED — device build/launcher verification pending.

## Widgets

### Live Weather · Current
- Current temperature
- Confidence-aware resolved condition
- Weather symbol
- Humidity
- Wind speed + direction
- Last updated time
- Manual refresh button
- Tap body to open Live Weather
- Resizable RemoteViews layout

### Live Weather · Forecast
- Current temperature + condition
- Humidity, wind and current precipitation
- Four-slot hourly strip (Now + next three hours)
- Hourly weather symbols and temperatures
- Last updated time
- Manual refresh button
- Tap body to open Live Weather
- Resizable RemoteViews layout

## Data and battery contract

Widgets never run the OpenGL Live Wallpaper engine and never perform network work from AppWidgetProvider callbacks.

They consume the same persistent WeatherCache used by the app/wallpaper data layer. Network refresh runs in WorkManager:
- Existing wallpaper weather worker refreshes the cache every 15 minutes when network is available and now repaints all placed widgets.
- Widget refresh buttons enqueue a unique one-shot WidgetRefreshWorker.
- Opening/refreshing weather in the app repaints placed widgets from the latest cache.

This keeps launcher widgets lightweight and avoids per-frame or per-minute battery drain.

## App integration

More → Widgets now opens a two-template chooser. On launchers supporting Android's requestPinAppWidget API, the selected widget is offered directly for placement. Unsupported launchers receive instructions to use Home screen → Widgets → Live Weather.

## Verification checklist

1. Clean/Rebuild/install the app.
2. Open Live Weather and obtain a fresh weather state.
3. More → Widgets → choose Current; add it to Home.
4. More → Widgets → choose Forecast; add it to Home.
5. Confirm temperature/condition match the app.
6. Tap widget body and confirm Live Weather opens.
7. Tap ↻ and confirm the widget shows Updating… and then refreshed time/data.
8. Change active city in the app and confirm widgets repaint to the new cached location/weather.
9. Resize both widgets and confirm content remains usable.
