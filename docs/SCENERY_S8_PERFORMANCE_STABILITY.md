# Scenery S8 — Performance & Visual Stability Pass

Status: **SOURCE IMPLEMENTATION COMPLETE — automated Debug/tests/Release lint/R8 APK+AAB gate passed; real-device visual stability remains the device checkpoint**

## Goal

S8 reduces scenery shimmer, moire and unnecessary GPU micro-detail cost without changing weather truth, scenery identity, time-of-day response, S7 weather interaction or the frozen cloud renderer.

## Shared performance detail

`HeroGlPipeline` now routes the existing shared performance detail scale into `HeroGlAnalyticWorldRenderer`.

This keeps one governor for the whole shared App Hero / Live Wallpaper pipeline instead of introducing a second scenery-specific performance system.

Only secondary scenery detail changes with the tier. Base terrain, buildings, water body, weather wetness, fog response, snow response and current-weather truth remain present.

## Stability changes

- high-frequency terrain micro-texture frequency scales down with reduced detail,
- urban roof material rhythm becomes lower-frequency at reduced detail,
- Farm fine rows and crop-head texture reduce frequency/contrast by detail tier,
- River/Lake micro-waves and rain dimples reduce secondary detail cost,
- reeds and grass use lower-frequency masks when detail is reduced,
- flower dots no longer use hard `floor`/`step` cell masks; they use continuous soft masks to reduce crawl/flicker during motion and parallax,
- wet micro-glints reduce in lower detail modes,
- visibility/storm suppression remains authoritative and works in addition to performance scaling.

## Weather invariants

S8 does not create, suppress or reinterpret weather.

- current rain/drizzle still controls wet response,
- current snow still controls snow/cold surface response,
- current fog/haze still controls visibility,
- current wind/wind direction still controls vegetation movement,
- Sun/Moon truth still controls time-of-day material response,
- performance tier never changes current weather intensity or condition.

## Performance invariants

- no network access in the GL frame loop,
- no SharedPreferences/disk read in the GL frame loop,
- no new bitmap/texture asset,
- no new per-frame Java allocation,
- no shader loop added,
- existing CINEMATIC/BALANCED/ECO shared detail signal is reused.

## Cloud freeze boundary

S8 does not modify `HeroGlTextureCloudRenderer.java`.

Verified active cloud blob:

`dc3b5db66c92cdf4520b0210857426e4bca853d8`

## Automated verification

Authoritative source gate:

- final source commit: `04caa359eb33af79cc325b6ccd22d41922ba430a`
- world stability commit: `71495158ad8d8eae0bfdb33057bf88532d668b55`
- GitHub Actions run: `32001064701`

Passed:

- Gradle wrapper verification,
- Debug build,
- unit tests,
- Release lint,
- minified/R8 Release APK,
- Release AAB,
- release output verification,
- release verification artifact upload.

## Real-device acceptance checklist

1. Pull `main` and run on the real phone.
2. Check Flowers/Greenery while the phone/wallpaper parallax moves; flower dots and grass should not sparkle or crawl strongly.
3. Check Farm rows/crop heads for moire or flicker.
4. Check River/Lake waves and rain micro-detail for stable motion rather than high-frequency shimmer.
5. Check Urban roof edges and Village details while changing scenery/variation.
6. Confirm the existing 1.8 second scene/variation transition remains smooth.
7. Compare CINEMATIC/BALANCED/ECO if the app exposes those modes: lower modes may show less micro-detail, but base scenery and weather truth must stay intact.
8. Confirm Home Hero and Android Live Wallpaper remain aligned.
9. Confirm cloud shape/motion remains unchanged.
10. Check `LiveWeatherGL` logs for world-shader compile/link errors.

## Next scenery step

After real-device S8 acceptance, the next step can focus on **S9 — Scenery UX Polish & Auto Scene Foundation**: clearer scene/variation presentation and an optional Auto Scene policy that changes only scenery choice while never changing weather truth.