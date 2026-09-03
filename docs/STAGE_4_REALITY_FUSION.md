# Stage 4 — Reality Fusion

Stage 4 establishes one truthful current-condition decision for the app, widgets, live scene and wallpaper.

## Delivered

- Current WMO condition remains authoritative for weather happening now.
- Nearest 15-minute values require corroboration before changing a dry current state to precipitation.
- Adjacent future/past precipitation alone never turns the live scene into rain or storm.
- Confidence is classified as high, medium, low or unavailable.
- Confirmed precipitation is classified as light, moderate or heavy.
- A persistent future signal can report precipitation arrival within three hours without changing the current condition.
- Home condition, symbol and hero scene now use the same `LiveConditionResolver` already used by widgets, sky, alerts and wallpaper.
- RainViewer metadata is classified only by availability/freshness. Metadata proves that observed frames exist; it does not expose local pixel intensity and therefore is never misrepresented as local rain confirmation.
- Missing radar remains a safe fallback to current plus 15-minute weather evidence.

## Preserved

- Existing weather, AQI, radar and alert APIs.
- RainViewer map tiles and Open-Meteo radar-field separation.
- Widget and wallpaper cache contracts.
- Saved-city and current-location behavior.

## Next stage

Stage 5 will improve atmosphere and sky realism using the fused condition without changing its weather truth.
