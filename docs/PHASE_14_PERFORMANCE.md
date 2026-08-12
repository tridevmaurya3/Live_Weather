# Phase 14 — Performance, Battery & Reliability

Status: IMPLEMENTED — device build / runtime verification pending.

## Scope completed

### Shared adaptive frame policy

In-app OpenGL scenes and the Android system Live Wallpaper now use one `PerformancePolicy` instead of separate battery rules.

Default mode is `AUTO`:
- Normal power state: ~30 FPS (`33 ms` frame interval)
- Battery at or below 20%: ~20 FPS (`50 ms`)
- Android Power Saver: ~15 FPS (`66 ms`)

A persistent `PerformancePreferences` mode contract also exists for future UI exposure:
- `AUTO`
- `SMOOTH`
- `BATTERY`

The existing Wallpaper `Battery adaptive` option remains authoritative. If the user disables that option, the adaptive battery throttling is bypassed unless the explicit Battery performance mode is selected.

### App GL lifecycle

`LiveSkyView` remains visibility-driven:
- Hidden Forecast / Wallpaper pages render zero frames.
- A view whose window or ancestor is not visible does not keep scheduling the frame runnable.
- Reality recomposition remains separate from frame rendering and is refreshed at a much lower cadence.
- Performance state is re-evaluated periodically rather than querying battery / power services every frame.

### System Live Wallpaper lifecycle

- Rendering remains visible-only.
- EGL work stays on the dedicated render thread.
- Weather / AQI network refresh remains outside the animation loop.
- Cache reload remains separate from rendering.
- App and wallpaper now cannot drift to different FPS / power policies.

### Existing reliability protections retained

- Radar WebView is lazy-created instead of during app startup.
- Alert LiveData background-thread writes use safe posting.
- WorkManager refreshes are unique/idempotent.
- Weather widgets consume cache and do not run OpenGL or network work from AppWidgetProvider callbacks.
- Live Wallpaper does not request background GPS.

## Roadmap reconciliation

The original Phase 11–13 Live Wallpaper / Ultra-Live / Dynamic World work was pulled forward and absorbed into the Hero Real Live Nature Engine and subsequent OpenGL/ODM work. It must not be restarted as separate duplicate phases.

After Phase 14 device verification, the next real roadmap stage is:

**Phase 15 — Production Polish**

Planned focus:
- release-readiness audit
- error/empty/offline states
- accessibility and content descriptions
- responsive edge cases
- app/widget/wallpaper consistency checks
- source/attribution review
- backup/data safety review
- release build/minification/resource checks
- final QA checklist and known limitations

## Verification

1. Clean/Rebuild/install.
2. Open Home and confirm live background remains smooth.
3. Switch Home → Forecast → Wallpaper → More repeatedly and confirm no freeze/crash.
4. Put app in background and return; confirm rendering resumes normally.
5. Enable Android Power Saver and confirm app/wallpaper remain functional with lower animation cadence.
6. Disable Power Saver and confirm normal smooth cadence returns.
7. Confirm Weather Widgets still refresh and Live Wallpaper still applies normally.
