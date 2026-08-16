# Phase 21 — Alerts Pro

Status: SOURCE IMPLEMENTATION COMPLETE — build/device verification pending.

## Product contract

Alerts Pro keeps official warning data and app-derived Smart Risk signals separate at every layer: data state, UI labels, notification eligibility, cache fallback and empty-state wording.

Permanent rules:
- An official warning and a Smart Risk signal are different evidence classes.
- Smart Risk must never be presented as an official IMD warning.
- Forecast probability/model thresholds must never be rewritten as an official warning.
- Saved official alerts may remain visible as fallback, but stale saved data must be labelled stale.
- A failed/unavailable official warning source must never produce an all-clear message.
- `No alert displayed` is not equivalent to `no danger` when official data cannot be verified.
- Expired alerts are filtered before presentation/notification.
- Stale official fallback data must not generate a new official notification.
- User source/severity filters may hide alerts from the UI and suppress their notifications, but they do not rewrite source truth.
- Smart Risk push notifications retain a conservative Orange/Red floor even when lower severities are visible in the Alerts Center.
- Background Smart Risk notifications are never generated from stale cached weather beyond the bounded freshness window.
- Android app-level notification blocking and per-channel blocking are treated as delivery state, not as a successful notification state.

## Step 21.1 — Alerts architecture and truth foundation

Implemented:
- Added `AlertTruthPolicy` as the central source/freshness boundary.
- Added explicit official delivery states: `NETWORK`, `NETWORK_EMPTY`, `CACHE`, `UNAVAILABLE`, and `NOT_APPLICABLE`.
- Centralized the 10-minute official cache reuse rule.
- `AlertUiState` separates current check time from official warning-data timestamp.
- A provider `304 Not Modified` refreshes validation time because the current representation was network-confirmed unchanged.
- A successful live response with zero matched official warnings is represented as `NETWORK_EMPTY`.
- Failed official refresh keeps saved warnings visible as labelled cache fallback.
- Stale official fallback alerts are excluded from new notification candidates.
- Alerts Center status shows official delivery/freshness truth separately from overall check time.
- Replaced unsafe generic all-clear wording with source-aware empty states.
- Stale official rows/Home alert card are marked `SAVED OFFICIAL`.

## Step 21.2 — Alert settings + source/severity filters

Implemented:
- Persistent Official and Smart Risk source visibility toggles.
- Persistent minimum severity selector: `All`, `Yellow+`, `Orange+`, `Red only`.
- Default visibility keeps Official + Smart Risk enabled with minimum `Yellow+`.
- Filters update Alerts Center and Home alert card immediately without a network refresh.
- Hidden-source state does not become a false `no alerts` state.
- Source/severity preferences are shared by UI and notification eligibility.
- Official notification confidence floor remains Yellow+.
- Smart Risk notification confidence floor remains Orange/Red.
- User minimum severity can make delivery stricter but cannot lower the Smart Risk confidence floor.

## Step 21.3 — Notification controls + alert detail UX

Implemented:
- Added persistent per-source notification controls:
  - `Notify Official`,
  - `Notify Smart Risk`.
- Master notification switch remains separate from per-source delivery preferences.
- Notification manager checks:
  - Android 13+ notification permission,
  - app-level notification enablement,
  - Official channel importance/block state,
  - Smart Risk channel importance/block state.
- Alerts Center exposes Android notification settings for OEM/system-level control.
- Notification status explains master-off, app-blocked and per-channel-blocked states.
- Official and Smart Risk notification channels remain separate.
- Notification titles explicitly identify `IMD OFFICIAL` vs `SMART RISK`.
- Smart Risk channel description explicitly states that it is app-derived and not an official warning.
- Alert rows are clickable/focusable and open a detail dialog with:
  - source,
  - severity,
  - area,
  - validity,
  - issue time,
  - alert message,
  - saved/stale fallback disclosure,
  - Smart Risk non-official disclosure.
- Existing notification navigation continues to open the Alerts Center.

## Step 21.4 — Background reliability + stale-data guard

Implemented:
- `WeatherAlertRefreshWorker` exits before weather parsing/network work when:
  - notification master is off,
  - both notification sources are disabled,
  - Android is blocking app notifications.
- Background official fetch runs only when Official display + notification delivery are enabled and the Official channel is usable.
- Background Smart Risk generation runs only when Smart Risk display + notification delivery are enabled and the Smart channel is usable.
- Cached weather older than 90 minutes cannot generate a background Smart Risk notification.
- Official `200` and `304` responses both refresh validation time.
- Failed official refresh can retain saved official warnings as fallback, while `AlertTruthPolicy` prevents stale fallback from becoming a new official push.
- Background and foreground notification paths both use `AlertPreferences.shouldNotify(...)` plus channel-state gating.

## Step 21.5 — Final source integration / static preflight

Checked:
- `AlertPreferences` source/severity/per-source-notification methods match renderer, notification manager and worker callers.
- `AlertNotificationManager` APIs used by the renderer/worker are present.
- Android notification/channel APIs used are compatible with project minSdk 26.
- Android notification settings intent has an application-details fallback.
- Existing `MainActivity` alert notification navigation key remains compatible with `EXTRA_OPEN_WEATHER_ALERTS` value.
- Existing alert scheduler remains network-constrained; disabled delivery exits early in the worker so provider work is not performed.
- Official source freshness and Smart Risk weather freshness are separate concepts.
- No Radar, Live Wallpaper, Weather Intelligence thresholds or current-weather truth logic were changed in Phase 21.

## Provider/source boundary

- IMD publicly exposes warning products and a Latest CAP Alerts surface.
- IMD WIS2 currently advertises `CAP Alerts published by in-imd` under weather advisories/warnings.
- This app's existing CAP ingestion transport was not migrated in Phase 21; transport/source failure is therefore always treated as unavailable/stale rather than as an official all-clear.

## Acceptance gate still required

Source implementation is complete, but Phase 21 still requires device/build verification after pull:
- Gradle Sync / Debug build.
- Android 13+ POST_NOTIFICATIONS permission flow.
- Master notification on/off.
- Official/Smart display filters and persistence after restart.
- Minimum severity persistence.
- Official/Smart per-source notification toggles.
- Android app notification block state.
- Official channel block state.
- Smart Risk channel block state.
- Alert row detail dialog.
- Notification tap → Alerts Center.
- Fresh official response, 304, failed refresh with recent cache, failed refresh with stale cache.
- Verify stale official fallback never produces a new official push.
- Verify cached weather older than 90 minutes never creates a background Smart Risk push.

## Verification boundary

- All Phase 21 source changes are on `main` only.
- No new branch was created.
- Phase 21 has not yet been locally pulled/built after these final changes.
- No real-device Phase 21 acceptance test has been run yet.
- Phase 22 has not started.
