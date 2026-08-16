# Phase 20B — Radar Pro

Status: IMPLEMENTATION STARTED — Steps 20B.1–20B.2 complete; visual/device verification pending.

## Product contract

Radar Pro must look professional and realistic without inventing meteorological truth.

Permanent rules:
- RainViewer past frames are observed radar imagery.
- Open-Meteo cloud / wind / temperature points are atmospheric model context, not observed radar pixels.
- Model cloud points must never be labeled or treated as radar observations.
- Forecast probability must never create a current or future radar frame.
- Radar metadata host/path/timestamp input must be validated before reaching the WebView.
- Cached observations may remain visible when useful, but delayed/stale metadata must be disclosed.
- Map animation must reuse provider frames; no synthetic square/block precipitation layer is allowed.

## Step 20B.1 — observed radar truth + data pipeline foundation

Implemented:
- Audited the existing Phase 9 Radar architecture: RainViewer past radar tiles, Open-Meteo 5x5 current model field, Leaflet/WebView map and Java timeline controls.
- Confirmed the existing Rain layer uses provider radar tiles, while the existing Clouds layer is generated from sampled Open-Meteo points and therefore is not radar imagery.
- Added `RadarObservedDataPolicy` as the code-level truth boundary for observed radar metadata.
- RainViewer tile host must be HTTPS and remain on the RainViewer domain before it can enter the WebView payload.
- Radar frame paths must be versioned radar paths, reject traversal/query/fragment injection and contain a valid epoch component.
- Past frames are sanitized, deduplicated by timestamp and sorted oldest to newest.
- Frames materially in the future are rejected; a small local-clock tolerance is retained.
- Repository caches only RainViewer responses that contain a usable observed timeline; malformed successful responses no longer become radar state.
- Open-Meteo errors/status wording now calls the 5x5 data an atmospheric model field rather than cloud radar.
- `RadarUiState` exposes validated `observedFrames`, `safeRadarHost` and a delayed-metadata flag separately from model-field points.
- `Phase9Renderer` sends only the safe host and sanitized observed frames to the map payload.
- Radar source/status text explicitly distinguishes observed radar from Open-Meteo model context and states that no future radar nowcast is fabricated.

## Step 20B.2 — continuous model-cloud surface

Implemented:
- Removed the old cloud presentation based on multiple blurred Leaflet circles around each model sample.
- Open-Meteo 5x5 cloud-cover points are now interpolated into one continuous bounded atmospheric field using inverse-distance weighting.
- The model surface is rendered once into a small 144x144 transparent offscreen canvas and delivered to Leaflet as one georeferenced image overlay.
- Individual sample circles, SVG cloud blobs and per-point cloud DOM geometry are no longer used.
- Surface edges use an alpha feather so the finite sampled field does not reveal a hard rectangular boundary.
- Cloud tone/opacity vary continuously with interpolated cloud-cover density while keeping the base map readable.
- Longitude samples are unwrapped around the active location before interpolation to avoid a false large span near the antimeridian; pathological spans are rejected.
- Cloud-surface generation is lazy. The default Rain layer pays no interpolation cost unless the user actually selects Clouds.
- New field data invalidates the old cloud surface; normal map pan/zoom reuses Leaflet's single image overlay instead of recomputing the field during gestures.
- RainViewer observed radar rendering and observed-frame timeline are unchanged.
- The layer remains explicitly an Open-Meteo model visualization, not radar or satellite imagery.

## Performance contract for the cloud surface

The cloud field is intentionally a low-frequency contextual layer. Its interpolation runs only when a new model field needs a Cloud-layer image. It is not an animation loop, does not trigger Android page refreshes, does not make additional network calls and does not rebuild while the user pans or zooms the map.

This keeps the Radar page aligned with the Real Live Weather smoothness rule: realistic presentation without trading away responsiveness.

## Verification boundary

- Source changes are on `main`.
- No local Android Studio / Gradle build was run in these Radar Pro steps.
- No real-device WebView/radar playback/cloud-surface validation was run yet.
- Phase 20B is not complete.
