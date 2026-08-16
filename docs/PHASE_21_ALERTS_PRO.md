# Phase 21 — Alerts Pro

Status: IMPLEMENTATION STARTED — Steps 21.1–21.2 source implementation complete; build/device verification pending.

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
- User source/severity filters may hide alerts from the UI and suppress their notifications, but they must not rewrite the underlying source truth.
- Smart Risk push notifications retain a conservative Orange/Red confidence floor even when lower severities are visible in the Alerts Center.

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

## Step 21.2 — Alert settings + source/severity filters

Implemented:
- Extended `AlertPreferences` with persistent source and severity settings.
- Official alerts default to enabled.
- Smart Risk defaults to enabled.
- Minimum visible severity defaults to `YELLOW` so low-value INFO cards do not dominate the Alerts Center while existing important warnings remain visible.
- Minimum severity cycles through `All`, `Yellow+`, `Orange+`, and `Red only`.
- Added source-aware preference helpers shared by UI and notification delivery.
- Official/Smart source toggles immediately affect:
  - Alerts Center rows,
  - Home alert card selection,
  - notification eligibility.
- Source toggles do not trigger a network refresh; underlying source data remains available so re-enabling a filter is immediate and source freshness can continue to be evaluated truthfully.
- Added compact Alerts Center controls:
  - `Official: On/Hidden`,
  - `Smart Risk: On/Hidden`,
  - `Minimum severity: ...`.
- Selected source controls use the existing weather selected-chip visual language and expose content descriptions for accessibility.
- Added a clear filter summary explaining that UI filters and notification filters are linked.
- If raw alerts exist but all are hidden by user filters, the empty state now says alerts are hidden by filters instead of falsely implying that no alerts exist.
- The Home alert card now chooses the highest alert only from the currently enabled source/severity filters.
- `AlertNotificationManager` now reads the same persistent source/severity preferences before marking a fingerprint as notified.
- Notification confidence floors are intentionally preserved:
  - official notifications require Yellow+,
  - Smart Risk notifications require Orange+,
  - the user's minimum severity can make either source stricter but cannot make low-confidence Smart Risk start generating push notifications.
- Background `WeatherAlertRefreshWorker` now uses `AlertTruthPolicy.notificationCandidates(...)` before notification delivery.
- Background successful `200` and `304 Not Modified` official checks both refresh the official validation timestamp.
- Background failed official refresh uses saved alerts only as cache fallback and does not let stale official cache create a new official push.
- No Smart Risk threshold calculation, current-weather truth rule, CAP provider endpoint or Radar behavior was changed in this step.

## Existing architecture confirmed during audit

- Official CAP-style warning ingestion exists separately from `SmartAlertEngine`.
- Smart Risk uses the Weather Intelligence/current-condition truth boundary for rain/thunderstorm-now logic.
- Official and Smart alerts are merged only for ordered presentation; their source identity remains on every `WeatherAlert`.
- Notification channels remain separate for official warnings and Smart Risk.
- The alert background worker is network-constrained and continues to use the same central notification manager.

## Next planned step

### Step 21.3 — Notification Controls + Alert Detail/Navigation Polish

Planned scope:
- clearer master-notification state including Android permission state,
- per-source notification explanation/status,
- alert row/detail interaction,
- notification-to-alert navigation polish,
- source/severity/validity detail presentation,
- no provider or weather-truth threshold change in the same step.

## Verification boundary

- Changes are on `main` only.
- Gradle Sync had passed before Phase 21 started, but Steps 21.1–21.2 have not yet been locally pulled/built.
- No real-device alert filter, notification-source, stale-cache or background-worker validation has been run for Phase 21 yet.
- Phase 21 is not complete.
