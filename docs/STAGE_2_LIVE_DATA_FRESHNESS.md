# Stage 2 — Live Data Freshness Engine

Status: COMPLETE IN SOURCE — final CI gate required.

## Delivered

- A lifecycle-aware foreground ticker runs only while `MainActivity` is started.
- It checks freshness once per minute without performing a network call every minute.
- Rain, snow and storm conditions use a five-minute refresh window.
- Clear, fog and cloud conditions use a ten-minute refresh window.
- An in-flight automatic refresh is never cancelled and restarted by the ticker.
- Manual refresh retains its existing immediate/replace behaviour.
- All Retrofit providers share one bounded OkHttp client.
- Connect, read, write and whole-call timeouts are explicit.
- Transport failures distinguish timeout, offline/DNS and refused connection.
- Live weather with an informational message is no longer incorrectly labelled saved weather.

## Preserved

- Existing provider URLs and request fields.
- Current/15-minute rain truth rules.
- Saved-city and current-location behaviour.
- Weather, AQI, Radar, Alerts, widget and wallpaper cache identities.
- Radar observed/model boundaries.
- All navigation, layouts and OpenGL rendering behaviour.

## Next stage

Stage 3 adds location continuity: location age, meaningful movement and safe
refresh of the active device location without changing fixed saved cities.
