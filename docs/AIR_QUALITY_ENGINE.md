# Live Weather — Air Quality Engine Contract

## Purpose

Phase 7 adds a location-synchronised air-quality layer without pretending that a forecast model is a street-level sensor.

## Source

Air-quality data is requested from the Open-Meteo Air Quality API, which exposes Copernicus Atmosphere Monitoring Service (CAMS) model data.

The application must preserve attribution to CAMS and Open-Meteo wherever production licensing/attribution requires it.

## Indices

The app currently surfaces:

- United States AQI as the primary consolidated AQI label.
- European AQI as a secondary comparison.
- Pollutant-specific U.S. AQI components when available.

The application must not label these values as India CPCB AQI unless a future CPCB-compatible data source and calculation are deliberately integrated.

## Pollutants and atmosphere

The shared state includes:

- PM2.5
- PM10
- Ozone (O3)
- Nitrogen dioxide (NO2)
- Sulphur dioxide (SO2)
- Carbon monoxide (CO)
- Aerosol optical depth
- Dust
- UV index
- Clear-sky UV index

Aerosol optical depth, PM2.5 and dust are combined into a renderer-facing haze estimate. This estimate can reduce sky clarity in the app and Live Wallpaper, but it is an environmental visual interpretation, not a claim of direct camera observation.

## Location consistency

AQI uses the same active location coordinates as the weather state. Per-location caching prevents a saved city's AQI from being treated as the current device location.

The Live Wallpaper reloads AQI using the exact weather-cache location key before applying atmospheric haze.

## Refresh separation

AQI network requests never run per animation frame.

- Foreground app refresh: location-aware and cached.
- Background wallpaper refresh: WorkManager, network constrained.
- Animation: cached weather/AQI/astronomy only.

## Accuracy rule

CAMS is a gridded atmospheric model. Values can differ from a nearby physical monitoring station, especially for highly local pollution sources. UI language should therefore say model estimate/model AQI rather than local sensor reading.

## Reality integration

AQI does not replace meteorological visibility. The final atmospheric presentation combines:

1. Weather visibility and fog.
2. Clouds and precipitation.
3. Aerosol/dust/PM haze estimate.
4. Astronomical Sun/Moon/star state.

This keeps one coherent visual reality across app screens and the Android Live Wallpaper.
