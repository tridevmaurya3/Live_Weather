# Phase 18 — Weather Intelligence 2.0

Status: IMPLEMENTED — device verification pending.

## Purpose

Phase 18 makes weather wording consistent and conservative. The app must not turn a forecast probability or an adjacent future model slot into a claim that rain is physically falling at the user's exact location now.

## Current vs forecast precipitation contract

Current precipitation can be classified as NOW only from:
- the provider's current precipitation/thunderstorm WMO state, or
- a current/nearest 15-minute signal that is sufficiently corroborated.

Previous/next 15-minute slots may corroborate the current/nearest interval, but an adjacent slot alone cannot become a current rain/thunderstorm claim.

Hourly precipitation probability is forecast information only. It drives:
- Rain likely soon
- Rain possible soon
- Rain likely later
- Rain possible later

It never becomes Raining now by itself.

Weak isolated precipitation values remain explicitly unconfirmed.

## WeatherIntelligence2 report

The centralized report provides:
- precipitation timing/state
- high-priority Home insight
- comfort / feels-like interpretation
- humidity/dew-point moisture interpretation
- sustained wind vs gust interpretation
- visibility/fog interpretation with cause uncertainty
- short pressure trend interpretation
- model-consistency/confidence wording
- next rain-risk timing and probability

Raw thresholds remain metric. Phase 16 WeatherFormatter converts only displayed units.

## UI integration

### Home
- Weather insight uses the centralized Phase 18 headline.
- Resolved current condition remains precipitation-first but confidence-aware.
- A weak/future rain signal does not switch the Home condition or Hero scene into rain.

### Forecast
- Advanced details explain current precipitation evidence, wind/gust character, comfort, visibility, pressure trend, confidence, location quality and cache freshness.
- Next-24h wording says forecast rain-risk window instead of implying rain is physically occurring.

### Smart Risk
- Heavy-rain-now alert requires confirmed current rain/showers plus a strong current/corroborated signal.
- Snowfall cannot trigger a heavy-rain alert.
- Heavy-rain potential today explicitly remains a forecast risk when current rain is not confirmed.
- Visibility messages do not claim an exact cause when the model only reports reduced visibility.

### Live Wallpaper / app scene
- LiveConditionResolver accuracy changes flow into the shared reality composer, so adjacent future precipitation cannot independently force a current rain/storm scene.

## Confidence wording

The app uses model-consistency language rather than claiming sensor truth:
- Higher model consistency: corroborated/current precipitation classification with short-term support.
- Standard model confidence: normal current weather model state.
- Limited confidence: weak precipitation signal remains unconfirmed.

The app does not claim hyperlocal physical observation from forecast/model data.

## Device verification checklist

1. Clean / Rebuild / Run.
2. Test a dry current condition with high rain probability later: Home must say not confirmed raining now and identify later timing.
3. Test a weak trace precipitation signal: Home/Details must say unconfirmed rather than Rain now.
4. Test confirmed rain/showers: Home condition, insight and scene should agree on current precipitation.
5. Check Forecast Advanced Details for comfort, wind/gust, visibility, pressure and confidence explanations.
6. Confirm Forecast 24h summary uses forecast rain-risk wording.
7. Confirm Phase 16 unit changes are respected inside all Phase 18 summaries.
8. Turn network off and verify Data Quality marks saved/offline plus freshness/age wording.
9. If a snow test location is available, confirm snow does not produce Heavy rain now wording.
10. Check Smart Risk daily heavy-rain potential: when dry now it must say forecast risk, not rain falling now.

## Scope boundary

Phase 18 improves weather interpretation and accuracy wording. Rich interactive forecast UI is Phase 19.
