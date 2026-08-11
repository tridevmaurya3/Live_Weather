# Weather Alert Engine

## Purpose

Live Weather keeps authoritative warnings and app-derived weather risks as separate sources.

## Authoritative India Alert Source

The active official source is the India Meteorological Department CAP RSS feed registered by WMO:

`https://cap-sources.s3.amazonaws.com/in-imd-en/rss.xml`

The app consumes the CAP/RSS feed with HTTP ETag support. After the first successful request it sends `If-None-Match`; HTTP 304 retains cached official alerts. This follows the SACHET/NDMA CAP XML integration guidance for efficient consumers.

Direct IMD JSON warning endpoints are not the mobile production dependency because the public API portal documents IP whitelisting and unauthenticated direct requests may return HTTP 401. The codebase can retain an adapter for future approved/whitelisted integrations, but the app must not silently depend on it.

## Location Matching

The active weather coordinates are reverse-geocoded to district/state/country. IMD CAP alerts are filtered against the resolved district/state text. Outside India, the official IMD layer is skipped while Smart Risk remains available.

If district resolution or the CAP feed is temporarily unavailable, cached official alerts may remain visible until expired and Smart Risk continues independently.

## Smart Risk Source

Smart Risk alerts are derived from the app's shared weather state. Examples include:

- current thunderstorm signal
- strong current precipitation
- high wind gusts
- very low visibility
- heavy-rain potential today
- strong gust potential today
- very high UV
- high heat

Smart Risk is not presented as an IMD warning. UI and notifications always label it `SMART RISK`.

## Severity

Normalized app severities are INFO, YELLOW/WATCH, ORANGE/ALERT and RED/WARNING.

CAP severity/urgency is normalized independently from forecast-derived Smart Risk thresholds. Official source always remains visible in the source badge.

## Notifications

Android notification permission is requested contextually from the Weather Alerts Center, not automatically at app launch.

Official non-info alerts can notify when enabled. Smart Risk notifications are limited to ORANGE/RED to reduce noise. Alert fingerprints are persisted to prevent repeated notifications for the same event.

Notification taps open the Weather Alerts Center.

## Background Refresh

WorkManager runs a network-constrained periodic alert check at Android's supported 15-minute minimum periodic interval.

The alert worker reuses the shared WeatherCache for Smart Risk and does not launch a second weather API request. The official CAP feed is refreshed independently with ETag caching.

Background GPS is not requested. The worker uses the last foreground-resolved alert district and cached active weather coordinates.

## Safety and Accuracy Contract

- Official CAP alerts are attributed to IMD.
- Smart Risk is a model-derived app interpretation, never an official warning.
- Absence of an app alert is not proof that hazardous weather is impossible.
- Cached alerts are marked through status text when an official refresh fails.
- Severe weather decisions should prioritize current official local authority guidance.
- The alert engine must not invent an official severity or source.
