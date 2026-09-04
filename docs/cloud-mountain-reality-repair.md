# Cloud + Mountain Reality Repair

This post-roadmap repair addresses two visual defects reported after the Stage 1-16 realism roadmap:

1. cloud masses looked stretched/triangular and appeared to swing like a pendulum;
2. the Sun and Moon could remain clearly visible through the three procedural mountain layers.

The repair deliberately preserves weather truth, data freshness, location continuity, App/Wallpaper unification, atmospheric visibility, precipitation state, scenery selection and performance policies.

## Stage 1 — Cloud shape reconstruction

`HeroGlTextureCloudRenderer` now uses more compact far/mid/near atlas masses with softer edge falloff and bounded alpha breakup. The renderer still consumes the same provider-resolved cloud cover, density and vertical layers; it does not manufacture cloud truth.

## Stage 2 — Continuous wind motion

Cloud-center `cross`, `lift` and `breathe` oscillations were removed. Position now follows one-way wind advection. Slow UV deformation evolves the interior of the cloud sprite without moving its center back and forth.

This is intentionally different from turbulence: turbulence may change internal texture/detail or instantaneous speed slightly, but it does not reverse the cloud's travel direction like a pendulum.

## Stage 3 — Layer depth and parallax

Far, mid and near cloud layers now use clearly separated advection/parallax scales. Far clouds move slowest, mid clouds faster and near clouds fastest. The overcast sheet pattern also travels in the same resolved horizontal wind direction instead of using opposing time phases.

## Stage 4 — Sun terrain occlusion

The world renderer keeps Stage 15 atmospheric perspective for normal mountain pixels. Near the direct Sun disc and immediate halo, however, an overlapping mountain silhouette raises the world alpha so the celestial pass underneath cannot shine through the terrain body.

## Stage 5 — Moon terrain occlusion

The same physical terrain mask now covers the Moon disc and immediate halo. A smooth `celestialTerrainMask` keeps ridge-edge reveal gradual while preventing a clearly visible full disc inside the mountain body. `OPEN_SKY` remains free of terrain occlusion.

## Stage 6 — Regression and release verification

Regression contracts verify that:

- pendulum position variables do not return;
- far/mid/near clouds retain continuous one-way advection and distinct speeds;
- compact cloud-mass dimensions do not regress to the old wide strips;
- internal cloud evolution remains available without center reversal;
- Stage 15 distance transmission remains intact;
- both Sun and Moon terrain-occlusion contracts remain present;
- shader sources remain structurally valid for the existing Stage 16 device smoke harness.

The final Release Gate remains authoritative for Java compilation, unit tests, Android test APK packaging, Release lint, Debug/Release APK, AAB and R8 verification. The Stage 16 EGL instrumentation smoke test can additionally be run on a physical Android device/emulator to compile/link the shared renderers on that GPU.

## Invariants

This repair does **not**:

- change Open-Meteo requests or weather codes;
- synthesize rain, snow, fog, storm or lightning;
- change active-location or fixed-city widget semantics;
- alter database/cache/sync connections;
- remove measured-visibility atmospheric perspective;
- change manual scenery choices;
- add a new framebuffer or renderer pass;
- add per-frame Java object allocation.
