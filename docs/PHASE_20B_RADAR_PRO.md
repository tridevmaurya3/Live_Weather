# Phase 20B — Radar Pro

Status: IMPLEMENTATION STARTED — Step 20B.1 observed-data foundation complete; visual/device verification pending.

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

## Existing cloud-layer limitation discovered in the audit

The current Clouds layer in `radar_map.html` uses blurred Leaflet circles derived from a sparse 5x5 Open-Meteo cloud-cover field. It is a model visualization, not a real cloud raster/satellite layer. This is the main architectural reason the cloud layer can look like separated blobs/tiles instead of a continuous professional weather map.

Step 20B.1 intentionally does not redesign that visual layer. The next Radar Pro step should replace the sparse-circle presentation with a continuous, bounded model-field surface (or another properly sourced raster product if adopted) while preserving explicit source semantics.

## Verification boundary

- Source changes are on `main`.
- No local Android Studio / Gradle build was run in this step.
- No real-device WebView/radar playback validation was run in this step.
- Phase 20B is not complete.
