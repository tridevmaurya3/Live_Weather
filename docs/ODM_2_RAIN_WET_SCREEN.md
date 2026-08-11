# ODM-2 — Rain + Wet Screen Rebuild

Status: COMPLETE — SYSTEM LIVE WALLPAPER GPU PATH

## Goal

ODM-2 replaces the old single-pass rain look with a dedicated transparent GPU rain pass drawn after the ODM-1 sky/cloud atmosphere. The target is continuous depth, wind response and wet-screen atmosphere rather than uniform straight streaks.

## Render Architecture

The system Live Wallpaper now uses two GPU passes in one EGL frame:

1. `HeroGlCloudSceneRenderer` draws sky, clouds, Sun, Moon, stars and storm atmosphere with its legacy rain disabled.
2. `HeroGlRainOverlayRenderer` draws confidence-resolved drizzle/rain, wet-glass optics, heavy-rain curtain, lower-screen film and splashes.
3. EGL swaps the composed frame once.

No network or weather query occurs per frame.

## Rain Reality Contract

Rain/drizzle intensity comes from `DynamicRealityComposer`, which follows the confidence-aware `LiveConditionResolver`. An isolated weak precipitation model point does not independently start visual rain.

The Rain preference controls the entire ODM-2 pass. Lightning remains separately controllable. If Lightning is disabled, storm rain remains visible but the rain pass does not brighten with an electrical flash.

## Visual Layers

### Drizzle

- fine short streaks,
- lower speed,
- lower density,
- restrained wind angle,
- no heavy-rain curtain.

### Normal rain

- fine/far/mid/near depth groups,
- different cell spacing, speed, streak length and thickness,
- wind-driven slope,
- temporal breakup so the pattern does not read as a rigid grid.

### Heavy rain

- denser multi-depth streak field,
- animated atmospheric curtain with two noise scales,
- lower-screen wet film,
- subtle splash field near the bottom of the scene.

### Wet screen

- sparse fixed droplets,
- larger sliding droplets,
- vertical water trails behind sliding drops,
- bright rim plus darker water-body approximation,
- density rises with confirmed rain intensity.

This is a procedural optical approximation; it does not claim camera/video photorealism or true scene refraction.

## Performance Contract

- OpenGL ES 2.0 only,
- no bitmap generation per frame,
- no network/location work in render functions,
- transparent alpha-composited second pass,
- frame cadence remains controlled by the existing adaptive wallpaper thread.

## Acceptance Checkpoint

ODM-2 should be tested only after the full step is pulled and rebuilt. Check drizzle, normal rain and heavy/storm rain when those real conditions are available. Rain should not appear for an unconfirmed weak trace signal.
