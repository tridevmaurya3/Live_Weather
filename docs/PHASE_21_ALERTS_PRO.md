# Phase 21 — Alerts Pro

Status: IMPLEMENTATION STARTED — Step 21.1 source implementation complete; build/device verification pending.

## Product contract

Alerts Pro must separate official warning data from app-derived Smart Risk signals at every layer: data state, UI labels, notification eligibility, cache fallback and empty-state wording.

Permanent rules:
- An official warning and a Smart Risk signal are different evidence classes.
- Smart Risk must never be presented as an official IMD warning.
- Forecast probability/model thresholds must never be rewritten as an official warning.
- Saved official alerts may remain visible as fallback, but stale saved data must be labelled stale.
- A failed/unavailable official warning source must never produce an all-clear message.
- `No alert displayed` is not equivalent to `no danger` when official data cannot be verified.
- Expired alerts are filtered before presentation/notification.
- Stale official fallback data must not generate a new official notification.

## Step 21.1 — Alerts architecture and truth foundation

Implemented:
- Added `AlertTruthPolicy` as the central source/freshness boundary for Alerts Pro.
- Added explicit official delivery states: `NETWORK`, `NETWORK_EMPTY`, `CACHE`, `UNAVAILABLE`, and `NOT_APPLICABLE`.
- Centralized the 10-minute official cache reuse rule so ViewModel/UI/notification filtering use the same freshness definition.
- `AlertUiState` now separates the current check time from the timestamp of official warning data.
- `AlertUiState` exposes official delivery state, freshness, stale state, source availability, source label, official-alert presence and Smart-Risk presence.
- Kept the previous `AlertUiState` constructor for source compatibility while Phase 21 is rolled out.
- Fixed the old loading-state timestamp behavior that could make saved official data look freshly updated merely because a new refresh started.
- A provider `304 Not Modified` now refreshes the validation timestamp because the current representation was network-confirmed unchanged.
- A live successful response with zero matched official warnings is represented separately as `NETWORK_EMPTY` instead of generic unavailable data.
- A failed official refresh keeps saved official warnings visible when available, but labels them as cache/fallback and determines stale state from their saved timestamp.
- Notification candidates now exclude stale official fallback alerts while continuing to allow valid Smart Risk candidates.
- Alerts Center status now shows official delivery/freshness truth separately from the overall check time.
- Replaced the unsafe generic empty message (`No active weather alert is detected`) with state-aware wording:
  - fresh network/recent saved check can say no active official warning matched;
  - stale cache says no current all-clear can be confirmed;
  - unavailable official source explicitly says the absence of a displayed alert is not an official all-clear;
  - outside India, official IMD scope is marked not applicable while Smart Risk remains separate.
- Stale official rows and the Home alert card are explicitly marked `SAVED OFFICIAL` rather than looking live.
- No Smart Risk thresholds, weather-current truth, provider endpoints or severity thresholds were changed in this step.

## Existing architecture confirmed during audit

- Official CAP-style warning ingestion already exists separately from `SmartAlertEngine`.
- Smart Risk already uses the Weather Intelligence/current-condition truth boundary for rain/thunderstorm-now logic.
- Official and Smart alerts are merged only for ordered presentation; their source identity remains on every `WeatherAlert`.
- Notification channels are already separate for official warnings and Smart Risk.

## Next planned step

### Step 21.2 — Alert Settings + Source/Severity Filters

Planned scope:
- dedicated alert settings surface,
- official-warning vs Smart-Risk toggles,
- severity-level preferences,
- per-source notification filtering,
- settings summaries and safe defaults,
- no change to weather truth thresholds in the same step.

## Verification boundary

- Changes are on `main` only.
- Gradle Sync had passed before Phase 21 started, but these new Phase 21.1 changes have not yet been locally pulled/built.
- No real-device alert-source/freshness/offline/notification validation has been run for Step 21.1 yet.
- Phase 21 is not complete.
