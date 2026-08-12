# Phase 17 — Widget Pro

Status: IMPLEMENTED — device / launcher verification pending.

## What changed

### Per-widget configuration
- Both Current and Forecast widgets use the Android AppWidget configure flow.
- Every placed widget stores its own configuration.
- Source modes:
  - Follow active weather — follows the app's current active location / selected city.
  - Fixed saved city — remains attached to a saved city even if the app switches elsewhere.
- Appearance modes:
  - Glass card.
  - Transparent card.
- Existing pre-Phase-17 widgets remain backward-compatible and default to Follow active weather + Glass.
- Tapping the widget brand reopens configuration on supported launchers / direct widget action.

### Cache identity safety
- WeatherCache now distinguishes an active save from a background per-location snapshot save.
- Fixed-city widget refreshes never move the app / Live Wallpaper active-location pointer.

### Refresh contract
- Manual refresh targets only the tapped widget source.
- Widget refresh no longer downloads AQI because widgets do not display AQI.
- Active-source widgets continue to share the existing app / wallpaper weather refresh cache.
- Fixed-city widgets have a 30-minute network-constrained periodic worker.
- Periodic fixed-city refreshes are deduplicated by coordinates, reuse snapshots for 45 minutes and cap network work to four stale unique locations per run.
- Fresh fixed locations are skipped so additional cities are not permanently starved.

### State visibility
- Fresh cache: Live / updated time.
- Older cache: Saved / updated time.
- More than three hours old: Stale / updated time.
- Manual network failure: Offline / saved time (or no saved weather).
- Manual refresh: Refreshing…

### Responsive widgets
- Compact widget can hide its secondary Humidity / Wind row at very short launcher heights.
- Forecast widget hides the four-hour strip when resized below its usable height / width threshold.
- AppWidgetProvider reacts to option changes immediately.

### Tap destinations
- Current / body tap opens Home.
- Forecast hourly strip opens Forecast.
- Brand tap opens widget configuration.
- Refresh tap starts a WorkManager job through an explicit internal BroadcastReceiver.

### Lifecycle cleanup
- Per-widget preferences are removed when a widget is deleted.
- Widget periodic work is cancelled when no Live Weather widgets remain.
- Application startup restores widget repaint / scheduling for widgets placed before this upgrade.

## Device verification checklist

1. Clean / Rebuild / Run.
2. Add one Current widget and verify the configuration screen appears.
3. Select Follow active weather + Glass.
4. Add a second Current or Forecast widget and select a fixed saved city + Transparent.
5. Switch the app to another city: active widget should follow; fixed widget should not.
6. Tap refresh on each widget and verify only its source refreshes.
7. Resize Compact vertically and confirm secondary metrics can collapse.
8. Resize Forecast and confirm the hourly strip collapses at small sizes and returns when enlarged.
9. Tap Current body → Home.
10. Tap Forecast hourly strip → Forecast.
11. Tap brand → configuration.
12. Turn network off, tap refresh, and confirm Offline / saved state appears without losing cached weather.
13. Change Phase 16 Units and confirm both widgets repaint in the selected units.
14. Remove all widgets and verify no crash / orphan visible state.

## Scope boundary

Phase 17 improves Android home-screen widgets only. It does not redesign the Live Wallpaper visual engine.
