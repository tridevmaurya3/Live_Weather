# Scenery S6 — Time-of-day Scenery Lighting & Material Response

Status: **SOURCE IMPLEMENTATION COMPLETE — automated Debug/tests/Release lint/R8 APK+AAB gate passed; real-device GPU/visual acceptance remains the device checkpoint**

## Goal

S6 makes the scenery materials respond to authoritative time-of-day inputs without inventing a fake clock state or changing weather truth.

The response is deliberately restrained. It adds believable dawn/golden-hour/day/dusk/night material shifts while keeping the sky/weather engine visually dominant.

## Authoritative inputs

S6 derives its lighting response only from existing shared scene truth:

- Sun altitude,
- Sun screen position,
- Sun visibility,
- Moon visibility,
- Moon illumination,
- scene light,
- current cloud cover,
- current fog/haze,
- current storm intensity.

No device clock shortcut or random day/night cycling is introduced inside the renderer.

## Lighting model

The world shader now separates two concepts:

### Ambient time-of-day response

Sun altitude produces continuous low-frequency bands for:

- daylight,
- horizon twilight,
- blue-hour/cool transition,
- deep night.

This ambient component can remain subtly visible even if the Sun is partially obscured, matching the way the landscape still changes near sunrise/sunset under cloud.

### Direct material response

Warm direct response additionally requires authoritative Sun visibility and is attenuated by cloud and fog.

Moon material response requires:

- deep-night state,
- Moon visibility,
- Moon illumination,
- current cloud/fog attenuation.

This prevents scenery from showing strong fake golden or moon highlights when the relevant celestial source is not actually visible.

## Scene material response

### Open Sky

Open Sky remains intentionally minimal. No new foreground object is added and the scenery grade is heavily suppressed so the live sky remains dominant.

### Natural Hills

- subtle warm horizon/ridge response near golden hour,
- restrained cool blue-hour/night response,
- direct Sun-facing terrain edge highlight,
- faint Moon-facing terrain edge response at night.

### Village

- facade and roof material warms near low visible Sun,
- road and hedge tones receive restrained warm-hour response,
- buildings cool gently into night,
- night window behavior remains controlled by the existing night/visibility truth.

### Farm / Crops

- field base and crop rows warm near golden hour,
- crop-head material catches restrained warm response,
- night/Moon response shifts fields cooler,
- crop movement remains driven only by current wind.

### River / Lake

- water body receives restrained low-Sun warm material response,
- Moon-visible night water shifts subtly cooler,
- water glint color responds to golden-hour Sun or Moon visibility,
- existing current-condition wave/rain response remains unchanged.

### Flowers / Greenery

- meadow, grass and flower materials warm subtly near golden hour,
- flowers desaturate/cool gently in deep night,
- motion remains driven only by current wind.

### Urban / Buildings

- facade and roof-edge materials receive restrained low-Sun warm response,
- deep-night/Moon response becomes cooler,
- existing night-window distribution remains unchanged and visibility-aware.

## Weather-truth invariants

S6 does not create or suppress weather.

- current rain/drizzle still alone control wetness,
- current fog/haze still control visibility loss,
- current storm still controls storm darkness,
- current Sun/Moon state controls direct material highlights,
- current wind remains the only movement driver for crops/reeds/greenery,
- forecast probability is never converted into current rain/storm visuals.

## Performance invariants

- no new texture or bitmap asset,
- no network request,
- no SharedPreferences/disk read in the frame hot path,
- no new per-frame Java object allocation,
- no shader loop added,
- time-of-day response is analytic math inside the existing world pass.

## Cloud freeze boundary

S6 does not modify `HeroGlTextureCloudRenderer.java`.

Verified active cloud blob:

`dc3b5db66c92cdf4520b0210857426e4bca853d8`

## Automated verification

Authoritative source gate:

- source commit: `cee0b383d8d3623c2c44060a10ae2a3a55cfc69a`
- GitHub Actions run: `31999571509`

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
2. Check the current daytime scene and confirm S6 does not create an artificial orange cast at normal midday Sun altitude.
3. Around low Sun/golden hour, check Natural Hills, Village, Farm, River/Lake, Flowers and Urban for restrained warm material response.
4. Confirm direct warm highlights reduce naturally if current cloud/fog obscures the Sun.
5. At twilight, confirm materials transition continuously rather than snapping between day and night.
6. At night, confirm scenery becomes subtly cooler rather than simply black.
7. If the Moon is visible, confirm River/Lake and terrain receive only restrained cool lunar response.
8. Confirm Open Sky stays visually clean and the sky remains dominant.
9. Cycle scenery variations 1-4 and confirm time-of-day response stays stable through the existing 1.8 second scene morph.
10. Confirm rain/fog/storm truth is unchanged when changing scenery or variation.
11. Confirm cloud shape/motion is unchanged.
12. Check `LiveWeatherGL` logs for world-shader compile/link errors.

## Next scenery step

After real-device S6 acceptance, the next scenery step can focus on **S7 — Scene-aware Weather Interaction Polish**: restrained rain/fog/snow/wind interaction with each scenery material while preserving the same authoritative current-weather truth and the frozen cloud renderer.
