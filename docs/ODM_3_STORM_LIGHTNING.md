# ODM-3 — Storm + Lightning Rebuild

Status: COMPLETE IN CODE — REAL-DEVICE VISUAL CHECKPOINT PENDING

## Goal

Replace the legacy electrical effect with one authoritative GPU storm pass that preserves weather-driven storm darkness while producing restrained, localized illumination and short branched lightning instead of a full-height glowing line.

## Active Render Order

1. Sky / clouds / Sun / Moon / stars base pass (`HeroGlCloudSceneRenderer`)
2. Storm atmosphere + electrical pass (`HeroGlStormOverlayRenderer`)
3. Rain + wet-screen pass (`HeroGlRainOverlayRenderer`)
4. One EGL buffer swap

The base pass receives zero legacy electrical storm intensity. This permanently prevents the old lightning implementation from drawing together with ODM-3.

## Storm Atmosphere

The storm pass receives the real resolved storm state even if the user disables the Lightning visual option. This preserves weather-correct storm darkness and cloud mass. The Lightning preference gates only electrical flashes and bolts.

Storm atmosphere uses:

- resolved storm intensity,
- cloud density,
- storm ceiling,
- near-cloud presence,
- rain intensity,
- wind strength and direction.

## Electrical Timing

ODM-3 uses a deterministic multi-pulse electrical event clock:

- primary short flash,
- weaker second pulse,
- faint third after-pulse.

The rain renderer uses the same timing values, so rain streaks brighten during the same exposure event rather than flashing independently.

## Lightning Geometry

Visible strikes are intentionally bounded to a partial vertical section of the sky. They use:

- irregular segmented center path,
- narrow bright core,
- restrained glow,
- two optional short branches,
- deterministic position/drift per event cycle.

The renderer does not create a continuous top-to-bottom cable-like line.

## Illumination

Electrical events affect more than the bolt itself:

- localized cloud-bank illumination around the strike area,
- restrained whole-scene exposure lift,
- rain exposure response,
- subtle electrical glow.

The scene does not use a sustained white full-screen flash.

## Reality Contract

Only thunderstorm intensity already resolved by `DynamicRealityComposer` can activate the storm pass. The renderer performs no network requests and makes no independent weather classification decisions.

Moon phase, Moon position, star visibility and astronomical reality remain owned by the existing shared sky engine and are unchanged by ODM-3.

## Acceptance Checkpoint

After build and wallpaper re-apply, verify storm footage/screenshots for:

- no vertical cable lightning,
- short irregular branched strike,
- cloud-localized illumination,
- brief multi-pulse exposure,
- rain and lightning exposure in sync,
- storm darkness still present when Lightning visual option is disabled.
