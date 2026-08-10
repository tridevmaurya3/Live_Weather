# Live Weather — Accuracy Strategy

## Goal

The application should present the most current and internally consistent weather state available for the active location while being transparent about forecast-model limits.

No weather provider can guarantee that a hyperlocal shower at a user's exact position will always be detected instantly. The app therefore combines multiple signals and gives active precipitation priority over a conflicting clear-sky code.

## Current-location accuracy

For current-device weather:

- Fine location permission uses a fresh high-accuracy Fused Location Provider request.
- Approximate/coarse permission remains supported with balanced-power accuracy.
- Manual Refresh obtains a new current-location fix before forcing the weather network refresh.
- The horizontal location accuracy radius is surfaced in the Phase 6 data-quality panel when GPS mode is active.
- Saved/searched cities use their known geocoding coordinates and are labelled accordingly.

## Weather freshness

- Foreground live weather reuse is limited to two minutes.
- Manual Refresh bypasses the reuse window.
- Successful responses remain cached per location for offline/failure fallback.
- Cached data is clearly distinguishable from a new live response.

## Precipitation-first current condition

The displayed current condition is not taken blindly from one WMO weather-code field.

`LiveConditionResolver` combines:

1. Current precipitation.
2. Current rain.
3. Current showers.
4. Current snowfall.
5. Current WMO weather code.
6. The nearest 15-minute precipitation/rain/showers/snowfall signal.
7. The nearest 15-minute WMO weather code.

If rain/showers/snow are active, that precipitation signal can override a conflicting clear/cloud-only current code.

Thunderstorm and snow severity retain higher priority where applicable.

## 15-minute data limitation

Open-Meteo provides 15-minute model data. In regions without native high-resolution 15-minute model coverage, those values can be interpolated from hourly model data.

Therefore the 15-minute path is a useful consistency and timing cross-check, but it is not equivalent to a rain gauge or a radar observation at the user's exact position.

The UI data-quality text intentionally describes it as a model cross-check and notes that it may be interpolated by region.

## Sky / wallpaper consistency

The same precipitation-first condition feeds the Sky Reality layer.

This means a rain signal can:

- change the Home hero to rain mode;
- change the current condition label;
- change the Wallpaper preview condition;
- draw precipitation in the live sky preview;
- suppress star visibility;
- reduce ambient scene light.

A raw clear-sky code is not allowed to keep a clear visual scene when a stronger active precipitation signal is available.

## Future observed-radar enhancement

The later Radar phase may add an observed-radar verification source where coverage and licensing allow it. That should remain a separate observation input to the same shared Reality State rather than creating a second unrelated weather system.

The permanent rule is: improve accuracy by reconciling trustworthy signals, never by fabricating a condition.
