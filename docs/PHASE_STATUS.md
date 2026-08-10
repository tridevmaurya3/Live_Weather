# Live Weather Development Status

## Phase 0 — Foundation
Status: COMPLETE

- Android Studio Java + XML project
- GitHub main branch workflow
- Modular architecture contract
- Lifecycle, WorkManager, Retrofit and Gson foundation
- Atmospheric Material 3 design tokens
- Responsive five-destination navigation shell
- Stable edge-to-edge system inset handling

## Phase 1 — Professional Weather UI System
Status: COMPLETE

- Full-width compact bottom navigation
- Home weather dashboard shell
- Current-weather hero card
- Compact humidity, wind and rain cards
- Hourly forecast strip
- 10-day, radar and air-quality quick surfaces
- Live Wallpaper entry card
- Forecast, Radar, Wallpaper and More shells
- Responsive reusable glass cards, chips, buttons, typography and spacing
- No fake live weather values

## Phase 2 — Real Weather Data Engine
Status: COMPLETE

- Open-Meteo current, hourly and 10-day forecast integration
- Device foreground location engine
- Real current weather UI sync
- Dynamic Home six-hour forecast
- Dynamic Forecast next-24-hours strip
- Dynamic 10-day forecast list
- Shared WeatherViewModel state
- Persistent per-location weather cache
- Retry, loading and offline fallback states
- Home, Forecast and Wallpaper preview share the same weather response

## Phase 3 — Location & City Engine
Status: COMPLETE

- Friendly current-place name resolution with coordinate fallback
- Global city / postal-code search
- Multiple saved cities
- Persistent active city selection
- Current-location and searched-city switching
- Per-location weather cache
- Professional Locations & Cities manager
- Home, Forecast and Wallpaper preview stay synchronized with active location

## Phase 4 — Main Weather Dashboard Intelligence & Interaction
Status: COMPLETE

- Rich current conditions including visibility and dew point
- Weather intelligence and interpretation layer
- Sunrise, sunset, daylight, UV, pressure, cloud, gust and rain details
- Weather-reactive Home hero modes
- Interactive Home quick actions and explicit refresh
- Compact professional dashboard layout

## Phase 5 — Advanced Forecast System & Charts
Status: COMPLETE

- Smart next-24-hour forecast summary
- Native Canvas temperature trend chart
- Native Canvas rain-probability chart
- Rich hourly strip
- Expandable 10-day rows
- Precipitation timing, sunshine/daylight, UV and wind details
- Astronomy Engine 2.1.19 integrated
- Shared SkyRealityState / SkyRealityEngine
- Observer-relative Sun and Moon positions
- Moon phase and illumination
- Weather-aware star visibility estimate
- Ambient scene-light factor
- Dynamic app atmosphere overlay
- Permanent shared Weather + Astronomy reality-state contract

## Phase 6 — Advanced Weather Details, Accuracy & Celestial Timeline
Status: COMPLETE

### Step 6.1 — Higher-Freshness Current Location
Status: COMPLETE

- Fine-location permission now prefers a fresh high-accuracy Fused Location Provider request
- Approximate/coarse permission remains supported with balanced-power accuracy
- Fine fix maximum accepted age reduced to one minute
- Location request duration capped to avoid an endless GPS wait
- Manual Refresh in current-location mode gets a new GPS fix before forcing the weather network refresh
- GPS horizontal accuracy is surfaced in the data-quality panel when available

### Step 6.2 — 15-Minute Precipitation Cross-Check
Status: COMPLETE

- Open-Meteo minutely_15 request added
- Current + nearest 15-minute precipitation are available together
- Rain, showers, snowfall and WMO weather-code signals are modelled
- Short past and near-future 15-minute windows are requested for nearest-time matching
- UI identifies the 15-minute path as a model cross-check that may be interpolated depending on region

### Step 6.3 — Precipitation-First Live Condition Resolver
Status: COMPLETE

- LiveConditionResolver added as the shared right-now condition authority
- Current precipitation, rain, showers and snowfall are evaluated
- Nearest 15-minute precipitation, rain, showers, snowfall and weather code are evaluated
- Active rain/shower/snow can override a conflicting clear/cloud-only raw weather code
- Thunderstorm severity retains priority
- Home condition label, Home hero state, Forecast current summary and Wallpaper preview use the resolved condition
- Sky Reality consumes the same resolved precipitation signal so stars/light cannot remain clear while a stronger rain signal is active

### Step 6.4 — Weather Freshness Policy
Status: COMPLETE

- Foreground live-response reuse window reduced from five minutes to two minutes
- Manual Refresh always bypasses reuse
- Cached weather remains available for offline/failure fallback
- Refresh and cached/live status remain visible to the user

### Step 6.5 — Live Sun / Moon Sky View
Status: COMPLETE FOR APP PREVIEW

- Lightweight native LiveSkyView added
- Forecast screen contains a live sky preview
- Wallpaper screen preview uses the same LiveSkyView
- Sun altitude/azimuth is recalculated from active location + current clock
- Moon altitude/azimuth is recalculated from active location + current clock
- Sky astronomy is recalculated every 30 seconds while the preview is attached
- The 30-second celestial tick does not trigger weather-network requests
- Sun and Moon move through the preview as astronomical position changes
- Below-horizon celestial bodies are not shown as overhead objects
- Clouds, precipitation, star visibility and scene light react to the shared current weather state

### Step 6.6 — Daily Moon Phase Progression
Status: COMPLETE

- CelestialDayState model added
- CelestialForecastEngine added
- 10-day Moon phase progression is calculated per active location
- Each day shows phase name and illuminated percentage
- Waxing / waning progression is tracked from lunar phase angle
- Moon phase chips are shown horizontally on Forecast

### Step 6.7 — Sun / Moon Rise & Set Timeline
Status: COMPLETE

- Astronomy Engine rise/set search is used for Sun and Moon
- Today summary shows local sunrise and sunset
- Today summary shows local moonrise and moonset
- Each Moon-phase day includes its moonrise and moonset where an event occurs that local date
- Rise/set times use the weather location timezone with device-zone fallback

### Step 6.8 — Advanced Weather Details Surface
Status: COMPLETE

- Resolved current-condition source is visible
- Current precipitation / rain / showers / snowfall breakdown is visible
- Wind speed, direction and gusts are grouped
- Sea-level pressure, surface pressure, clouds and visibility are grouped
- Feels-like temperature, humidity and dew point are grouped
- Data-quality text shows selected-city coordinates or GPS accuracy, update time and cached/live state

### Step 6.9 — Weather Accuracy Contract
Status: COMPLETE

- docs/WEATHER_ACCURACY.md added
- The app does not claim impossible 100% hyperlocal observation accuracy from a forecast model
- Current-condition logic reconciles multiple available signals instead of blindly trusting one WMO code
- The architecture reserves observed-radar verification for the later Radar phase where provider coverage/licensing allow it
- The shared Reality State remains the single condition source for App, Widgets and Live Wallpaper

## Phase 6 Result

The app now has a significantly stronger right-now weather path and a live celestial timeline.

For current-location weather it obtains a fresher/high-accuracy location when permission allows, cross-checks current precipitation against the nearest 15-minute model signal, and gives active precipitation priority over a conflicting clear-sky presentation.

The Forecast and Wallpaper preview now include a live Sun/Moon sky whose astronomical position follows the active location and clock, plus a 10-day Moon phase/illumination progression and local Sun/Moon rise-set events.

The final Android home-screen WallpaperService is still assigned to the later Live Wallpaper phases. Phase 6 delivers the shared live celestial/weather renderer foundation and preview; it does not falsely mark the home-screen wallpaper service complete.

## Next
Phase 7 — AQI + Sun / Moon Intelligence

Planned scope: real air-quality provider integration, AQI/pollutants, dedicated Sun/Moon detail experience, lunar calendar/next phases, daylight/UV intelligence and deeper sky visibility information while preserving the shared Reality State.
