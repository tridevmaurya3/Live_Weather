# Phase 16 — Settings & Units Pro

Status: IMPLEMENTED — device build/runtime verification pending.

## Functional settings

More → Units is now an active settings card.

Available presets:
- Metric: °C, km/h, hPa, mm, km
- Imperial: °F, mph, inHg, in, mi

Custom unit choices:
- Temperature: Celsius / Fahrenheit
- Wind: km/h / mph / m/s / knots
- Pressure: hPa / mbar / inHg
- Precipitation: mm / inch
- Visibility / distance: km / miles

Preferences are persistent and provider/cache values remain metric. Conversion occurs only at the presentation boundary, so weather intelligence thresholds and cached source values do not change when display units change.

## Synchronized surfaces

The active UnitPreferences are applied to:
- Home current weather and detail cards
- Forecast current/hourly/daily text
- Forecast temperature chart values
- Phase 6 advanced condition/details surfaces
- Compact and Forecast home-screen widgets
- Radar temperature and wind overlay labels
- Smart Risk alert messages

Changing units immediately repaints widgets and recreates the Activity so all visible app surfaces use the same unit set.

## Performance selector

More → Performance is now an active selector:
- Auto
- Smooth
- Battery

The current mode is shown directly on the card. The setting is persisted through PerformancePreferences and consumed by the shared PerformancePolicy used by in-app live scenes and the Android Live Wallpaper.

## Architecture contract

- Open-Meteo/provider DTOs and WeatherCache remain metric.
- Risk thresholds remain metric internally.
- Unit conversion is presentation-only through WeatherFormatter.
- LiveWeatherApplication configures the active units before Activities/widgets/background components render.
- SettingsCardBinder attaches behavior to the existing More-page cards without a large MainActivity/layout rewrite.

## Verification checklist

1. Clean/Rebuild/install.
2. More → Units → Metric; confirm Home/Forecast/Details/Widgets/Radar display metric units.
3. More → Units → Imperial; confirm °F, mph, inHg, inches and miles.
4. Custom: choose m/s, knots and mbar one at a time and confirm display changes.
5. Confirm forecast temperature chart scale changes between °C and °F.
6. Confirm placed widgets repaint after a unit change.
7. Confirm Radar Temp/Wind overlays use the selected labels.
8. If a Smart Risk numeric message is active, confirm it uses the selected units while severity stays unchanged.
9. More → Performance → Auto/Smooth/Battery; confirm the card summary updates and app remains stable.
