# Stage 14 — Near-Now Weather Evolution & Transition Intelligence

## Goal
Make the scene feel like weather is approaching and departing continuously instead of snapping only when the provider's current interval changes, while keeping current weather truth authoritative.

## Inputs
Stage 14 reuses the existing Open-Meteo `minutely_15` payload already requested by the app:

- precipitation
- rain
- showers
- snowfall
- weather_code
- cloud_cover
- visibility

No new provider call or refresh cadence is added.

## Near-now policy
`NearNowWeatherEvolutionPolicy` resolves three bounded presentation-only signals:

- `approachEnvelope` — non-severe precipitation/cloud build in the next 15–45 minutes
- `recentExitEnvelope` — non-severe precipitation that occurred in the previous 15–30 minutes
- `horizonVeil` — a supported near-future visibility/cloud/precipitation depth cue

The nearest current interval is resolved from provider timestamps. The existing four past minutely intervals remain compatible with the fallback center index.

## Current truth stays authoritative
Near-now values never alter:

- current weather code
- rain intensity
- drizzle intensity
- snow intensity
- storm intensity
- lightning scheduling
- alerts
- fog truth
- AUTO scenery precipitation/storm mode

If current precipitation is already active, Stage 14 returns neutral near-now cues because the existing live precipitation system owns the scene.

## Severe-weather separation
Stage 8 already owns thunderstorm approach/exit cloud cues through `SevereWeatherVisualPolicy`. Stage 14 therefore ignores minutely thunderstorm intervals (WMO 95–99) so severe cues are not double-counted.

## Spatial evolution
`CloudPresenceResolver` applies Stage 14 only to cloud presentation:

- approaching ordinary rain/snow builds mainly in the far cloud field first
- mid clouds receive a smaller approach contribution
- recently departed precipitation can retain a small near-cloud tail
- supported visibility deterioration contributes only to far/horizon cloud depth
- cloud brightness receives a small bounded reduction during an approaching/just-departed band

`CloudPresenceState.Mode` remains based only on current cloud amount and current precipitation/storm intensities. Forecast-adjacent cues cannot declare `PRECIPITATION` or `STORM`.

## No fake fog
A future visibility drop by itself is ignored. It can influence the horizon only when future precipitation or a meaningful cloud build independently supports the same approaching weather band.

## App Hero + Live Wallpaper
Both surfaces already consume the same `CloudPresenceState` through the shared reality pipeline, so Stage 14 automatically stays unified without adding a renderer pass or separate wallpaper logic.

## Performance
The policy runs only when a new weather snapshot is composed. It scans at most a few neighboring 15-minute entries and allocates no per-frame renderer objects.

## Regression coverage
- future ordinary rain creates a bounded approach cue
- recent rain creates a bounded exit tail
- future thunderstorm remains exclusively owned by Stage 8 severe policy
- active current precipitation disables forecast-adjacent cues
- unsupported visibility deterioration cannot invent fog-like depth
- integration test verifies future rain deepens far/mid clouds but does not set current precipitation/storm mode or storm ceiling
