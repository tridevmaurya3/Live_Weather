# Stage 16 — Final Reality Audit, Performance & Device QA

Stage 16 is the closing verification stage for the live-weather realism roadmap. It deliberately avoids changing production weather/rendering behavior unless the audit finds a verified defect.

## Audit result

The Stage 1–15 shared reality path remains intact:

- current weather truth stays authoritative;
- future minutely data remains presentation-only and cannot fabricate current rain/snow/storm/lightning;
- App Hero and Live Wallpaper continue to share the same GL reality pipeline;
- fixed-city widget isolation remains unchanged;
- snowpack remains separate from falling-snow truth;
- meltwater remains separate from atmospheric precipitation;
- measured visibility remains distance perspective, not fake fog;
- solar irradiance calibrates illumination only;
- no new network request, database, preference, cache schema or renderer pass is introduced in Stage 16.

## Ground-state audit clarification

The Stage 12 thaw path uses separate `GroundWetnessController` objects in the pipeline and world renderer, but production controllers intentionally share one process-local `SharedState`. Meltwater written after a frame is therefore read by the world controller on the following frame. This behavior is already protected by `heroAndWallpaperInstancesSharePhysicalGroundState()` and is not changed in Stage 16.

## Performance guard regression coverage

`AdaptiveFrameTimeGuardTest` now locks the mobile performance contract:

- sustained frame pressure can reduce only secondary detail;
- stable frames restore the selected quality gradually;
- ECO detail never falls below the readable floor;
- changing the base profile clears stale adaptive penalty;
- invalid timing samples cannot alter quality.

Weather intensity, condition identity and frame cadence remain outside this adaptive detail guard.

## Shader contract coverage

`HeroGlWorldShaderContractTest` validates the Stage 15 measured-visibility shader wiring without needing an Android device:

- far/mid/near/micro visibility uniforms remain declared and consumed;
- corresponding Java uniform fields remain present;
- vertex/fragment shader braces remain structurally balanced;
- foreground transmission floors remain present so low visibility cannot black out the near scene.

This is a source-contract test, not a substitute for real GLES driver compilation.

## Real EGL device smoke test

`HeroGlPipelineDeviceSmokeTest` is an Android instrumentation test. On a physical Android device or emulator it:

1. creates a real EGL OpenGL ES 2.0 pbuffer context;
2. creates the shared `HeroGlPipeline`;
3. calls `onSurfaceCreated()` so every active renderer compiles/links its shaders;
4. performs a tiny 64×64 smoke draw;
5. requires `rendererFaults=none` and `GL_NO_ERROR`;
6. verifies that the existing diagnostics report exposes GPU and GL identity;
7. releases the pipeline and EGL resources.

It requires no location, network, weather payload or runtime permission.

## Release Gate upgrade

The release workflow now builds `assembleDebugAndroidTest` in addition to the existing Debug/unit-test/lint/Release/R8 gate. The Android-test APK is required in output verification and uploaded with release verification artifacts.

The hosted Release Gate verifies that the real-device smoke harness compiles and packages correctly. It does **not** claim that GitHub's hosted runner has executed the instrumentation test on a physical mobile GPU.

## Physical-device completion criterion

For an actual phone GPU matrix, install the debug + androidTest APK and run the instrumentation test with Android instrumentation tooling. A passing device run must report no isolated renderer fault and no GLES error. Device-specific failures are diagnosable through the existing `LiveWeatherGL` diagnostics, which include GL vendor, renderer and version.

## Final roadmap state

After Stage 16 Release Gate success, the repository-side realism roadmap is complete. Physical-device validation remains a deployment/QA execution step rather than fabricated CI evidence.
