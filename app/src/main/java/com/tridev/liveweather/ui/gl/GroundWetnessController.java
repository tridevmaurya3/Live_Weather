package com.tridev.liveweather.ui.gl;

/**
 * Allocation-free physical moisture memory for the shared Hero / Live Wallpaper world.
 *
 * <p>This controller never changes resolved weather truth. It models what recent observed
 * rain/drizzle has already done to the rendered ground: surface wetness, soil saturation and
 * standing water. R8.4 adds separate puddle depth and puddle spread reservoirs so the existing
 * irregular low-point shader masks can fill, broaden and recede more naturally instead of
 * behaving like one uniform glossy layer.</p>
 *
 * <p>R8.5 makes the default controller process-shared so the app Hero, wallpaper preview and
 * Android Live Wallpaper do not each start with a freshly dry world when their GL surfaces are
 * recreated. The hot path remains allocation-free and does not read SharedPreferences, storage,
 * network data or the wall clock. Multiple active renderers are time-gated with monotonic
 * {@link System#nanoTime()} so two surfaces cannot make the physical simulation run twice as
 * fast.</p>
 */
public final class GroundWetnessController {

    private static final float MAX_DELTA_SECONDS = 2f;
    private static final float PRECIPITATION_ACTIVE_THRESHOLD = 0.015f;
    private static final float MAX_RAIN_EXPOSURE_SECONDS = 1_800f;

    private static final Object SHARED_LOCK = new Object();
    private static final SharedState SHARED_STATE = new SharedState();
    private static long sharedLastAdvanceNanos;

    private final boolean sharedMode;

    /** Visible dampness used by terrain/road/vegetation materials. */
    private float wetness;

    /** Visible low-point standing-water coverage sent to the world shader. */
    private float puddleCoverage;

    /** Slower underground reservoir; keeps the world damp after surface water drains. */
    private float soilSaturation;

    /** Normalized amount of water currently available to collect in low points. */
    private float surfaceWater;

    /** Bounded recent liquid-precipitation exposure used to distinguish showers from downpours. */
    private float rainExposureSeconds;

    /** Fast-filling local basin depth: reacts to runoff before the puddle footprint fully spreads. */
    private float puddleDepth;

    /** Slow spatial spread memory: broadens irregular low-point puddles during prolonged rain. */
    private float puddleSpread;

    /**
     * Production constructor. All default instances share one process-local physical ground state.
     */
    public GroundWetnessController() {
        sharedMode = true;
        synchronized (SHARED_LOCK) {
            loadSharedState();
        }
    }

    private GroundWetnessController(boolean sharedMode) {
        this.sharedMode = sharedMode;
        if (sharedMode) {
            synchronized (SHARED_LOCK) {
                loadSharedState();
            }
        }
    }

    /**
     * Creates an independent controller for deterministic unit tests. Production renderers should
     * use the public constructor so Hero and Live Wallpaper keep one physical moisture history.
     */
    static GroundWetnessController isolatedForTest() {
        return new GroundWetnessController(false);
    }

    /** Test-only shared-state cleanup. */
    static void resetSharedForTest() {
        synchronized (SHARED_LOCK) {
            SHARED_STATE.clear();
            sharedLastAdvanceNanos = 0L;
        }
    }

    /**
     * Advances retained ground moisture.
     *
     * @param rainIntensity normalized resolved rain intensity, 0..1
     * @param drizzleIntensity normalized resolved drizzle intensity, 0..1
     * @param stormIntensity normalized resolved storm intensity, 0..1
     * @param thermalBias apparent-temperature presentation signal, -1..1
     * @param windStrength normalized renderer wind strength, 0..1
     * @param deltaSeconds monotonic elapsed render time in seconds
     */
    public void advance(
            float rainIntensity,
            float drizzleIntensity,
            float stormIntensity,
            float thermalBias,
            float windStrength,
            float deltaSeconds
    ) {
        if (sharedMode) {
            synchronized (SHARED_LOCK) {
                loadSharedState();
                float sharedDelta = resolveSharedDelta(deltaSeconds);
                advanceInternal(
                        rainIntensity,
                        drizzleIntensity,
                        stormIntensity,
                        thermalBias,
                        windStrength,
                        sharedDelta
                );
                saveSharedState();
            }
            return;
        }

        advanceInternal(
                rainIntensity,
                drizzleIntensity,
                stormIntensity,
                thermalBias,
                windStrength,
                deltaSeconds
        );
    }

    private void advanceInternal(
            float rainIntensity,
            float drizzleIntensity,
            float stormIntensity,
            float thermalBias,
            float windStrength,
            float deltaSeconds
    ) {
        float dt = clamp(deltaSeconds, 0f, MAX_DELTA_SECONDS);
        if (dt <= 0f) return;

        float rain = clamp01(rainIntensity);
        float drizzle = clamp01(drizzleIntensity);
        float storm = clamp01(stormIntensity);
        float wind = clamp01(windStrength);
        float warm = clamp01(Math.max(0f, thermalBias));

        // Drizzle wets efficiently, but contributes much less liquid volume to standing water.
        float precipitation = Math.max(rain, drizzle * 0.60f);
        float liquidLoad = Math.max(rain, drizzle * 0.28f);
        float stormBoost = precipitation > PRECIPITATION_ACTIVE_THRESHOLD
                ? storm * 0.14f
                : 0f;
        float wettingDrive = clamp01(precipitation + stormBoost);

        if (wettingDrive > PRECIPITATION_ACTIVE_THRESHOLD) {
            advanceRainAccumulation(
                    rain,
                    liquidLoad,
                    storm,
                    precipitation,
                    wettingDrive,
                    dt
            );
        } else {
            advanceNaturalDrying(warm, wind, dt);
        }

        normalizeState();
        updateVisiblePuddleCoverage();
    }

    private void advanceRainAccumulation(
            float rain,
            float liquidLoad,
            float storm,
            float precipitation,
            float wettingDrive,
            float dt
    ) {
        rainExposureSeconds = Math.min(
                MAX_RAIN_EXPOSURE_SECONDS,
                rainExposureSeconds + dt * (0.25f + wettingDrive * 0.75f)
        );

        // Dry ground initially absorbs more water. As the soil saturates, runoff/ponding grows.
        float targetSoilSaturation = clamp01(0.16f + wettingDrive * 0.84f);
        float soilResponse = 0.012f + wettingDrive * 0.052f;
        soilSaturation = Math.max(
                soilSaturation,
                approach(soilSaturation, targetSoilSaturation, soilResponse * dt)
        );

        float infiltrationCapacity = mix(0.65f, 0.16f, soilSaturation);
        float runoffDrive = clamp01(
                liquidLoad - infiltrationCapacity + storm * rain * 0.18f
        );

        // Duration matters: a prolonged downpour can fill low points even when early rain was
        // mostly absorbed by dry soil. This is bounded and deterministic, not forecast-driven.
        float durationLoad = smoothstep(25f, 240f, rainExposureSeconds)
                * liquidLoad
                * (0.22f + soilSaturation * 0.58f);
        float accumulationRate = runoffDrive * 0.010f + durationLoad * 0.0035f;
        surfaceWater = clamp01(surfaceWater + accumulationRate * dt);

        float targetWetness = clamp01(
                0.14f
                        + soilSaturation * 0.64f
                        + surfaceWater * 0.24f
                        + precipitation * 0.12f
        );
        float wettingResponse = 0.018f + wettingDrive * 0.060f;
        wetness = Math.max(
                wetness,
                approach(wetness, targetWetness, wettingResponse * dt)
        );

        float basinWater = smoothstep(0.06f, 0.78f, surfaceWater);
        float saturatedBase = smoothstep(0.34f, 0.90f, soilSaturation);
        float durationSpread = smoothstep(20f, 300f, rainExposureSeconds);

        // Basin depth fills first. Spatial spread is intentionally slower, so a short shower
        // produces smaller/deeper low-point patches while sustained rain broadens their footprint.
        float targetDepth = basinWater
                * saturatedBase
                * clamp01(0.48f + liquidLoad * 0.52f);
        float targetSpread = basinWater
                * saturatedBase
                * clamp01(0.24f + durationSpread * 0.56f + liquidLoad * 0.20f);

        float depthResponse = 0.014f + liquidLoad * 0.058f;
        float spreadResponse = 0.005f + liquidLoad * 0.026f;

        if (targetDepth > puddleDepth) {
            puddleDepth = approach(puddleDepth, targetDepth, depthResponse * dt);
        }
        if (targetSpread > puddleSpread) {
            puddleSpread = approach(puddleSpread, targetSpread, spreadResponse * dt);
        }
    }

    private void advanceNaturalDrying(float warm, float wind, float dt) {
        // Exposure decays slowly. It does not directly render anything, but prevents the next
        // few dry frames from pretending a long rain event never happened.
        rainExposureSeconds = Math.max(0f, rainExposureSeconds - dt * 0.12f);

        // Standing water drains/evaporates first. Warm air and wind accelerate this process;
        // unsaturated soil also accepts a little remaining surface water.
        float surfaceDrainRate = 0.00040f
                + warm * 0.00062f
                + wind * 0.00046f
                + (1f - soilSaturation) * 0.00010f;
        surfaceWater = Math.max(0f, surfaceWater - surfaceDrainRate * dt);

        // Soil remains damp much longer than puddles, which prevents instant visual dry-out.
        float soilDryRate = 0.00008f + warm * 0.00026f + wind * 0.00016f;
        soilSaturation = Math.max(0f, soilSaturation - soilDryRate * dt);

        float retainedWetness = clamp01(soilSaturation * 0.78f + surfaceWater * 0.34f);
        float groundDryRate = 0.00012f + warm * 0.00044f + wind * 0.00024f;
        wetness = Math.max(retainedWetness, wetness - groundDryRate * dt);

        float basinWater = smoothstep(0.06f, 0.78f, surfaceWater);
        float saturatedBase = smoothstep(0.34f, 0.90f, soilSaturation);
        float retainedDepthTarget = basinWater * saturatedBase;
        float retainedSpreadTarget = retainedDepthTarget * smoothstep(0.20f, 0.82f, wetness);

        // Depth drains faster than footprint. This makes puddles become shallower before their
        // damp edge footprint fully disappears, which reads more naturally in the existing shader.
        float depthDrainRate = 0.00042f + warm * 0.00070f + wind * 0.00054f;
        float spreadDrainRate = 0.00020f + warm * 0.00032f + wind * 0.00025f;

        if (retainedDepthTarget < puddleDepth) {
            puddleDepth = Math.max(
                    retainedDepthTarget,
                    puddleDepth - depthDrainRate * dt
            );
        } else {
            puddleDepth = Math.max(0f, puddleDepth - depthDrainRate * dt);
        }

        if (retainedSpreadTarget < puddleSpread) {
            puddleSpread = Math.max(
                    retainedSpreadTarget,
                    puddleSpread - spreadDrainRate * dt
            );
        } else {
            puddleSpread = Math.max(0f, puddleSpread - spreadDrainRate * dt);
        }
    }

    /** Seeds lifecycle-restored state without allocating a snapshot object. */
    public void reset(float wetness, float puddleCoverage) {
        if (sharedMode) {
            synchronized (SHARED_LOCK) {
                loadSharedState();
                resetInternal(wetness, puddleCoverage);
                saveSharedState();
                sharedLastAdvanceNanos = System.nanoTime();
            }
            return;
        }

        resetInternal(wetness, puddleCoverage);
    }

    private void resetInternal(float wetness, float puddleCoverage) {
        this.wetness = clamp01(wetness);
        this.puddleCoverage = Math.min(clamp01(puddleCoverage), this.wetness);

        // Reconstruct conservative physical reservoirs from the public visual state.
        soilSaturation = this.wetness;
        surfaceWater = clamp01(
                this.puddleCoverage * 0.85f
                        + Math.max(0f, this.wetness - 0.75f) * 0.25f
        );
        rainExposureSeconds = this.puddleCoverage * 120f;

        // Treat restored coverage as mostly basin depth, with a slightly smaller edge spread.
        puddleDepth = this.puddleCoverage;
        puddleSpread = Math.min(
                this.puddleCoverage,
                smoothstep(0.20f, 0.90f, this.wetness)
                        * (0.55f + this.puddleCoverage * 0.45f)
        );

        normalizeState();
        updateVisiblePuddleCoverage();
    }

    public float getWetness() {
        if (sharedMode) {
            synchronized (SHARED_LOCK) {
                return SHARED_STATE.wetness;
            }
        }
        return wetness;
    }

    public float getPuddleCoverage() {
        if (sharedMode) {
            synchronized (SHARED_LOCK) {
                return SHARED_STATE.puddleCoverage;
            }
        }
        return puddleCoverage;
    }

    /** Package-private diagnostics/tests: not a new weather truth field. */
    float getSoilSaturation() {
        if (sharedMode) {
            synchronized (SHARED_LOCK) {
                return SHARED_STATE.soilSaturation;
            }
        }
        return soilSaturation;
    }

    /** Package-private diagnostics/tests: normalized low-point water reservoir. */
    float getSurfaceWater() {
        if (sharedMode) {
            synchronized (SHARED_LOCK) {
                return SHARED_STATE.surfaceWater;
            }
        }
        return surfaceWater;
    }

    /** Package-private diagnostics/tests: fast-filling low-point basin depth. */
    float getPuddleDepth() {
        if (sharedMode) {
            synchronized (SHARED_LOCK) {
                return SHARED_STATE.puddleDepth;
            }
        }
        return puddleDepth;
    }

    /** Package-private diagnostics/tests: slower irregular puddle footprint spread. */
    float getPuddleSpread() {
        if (sharedMode) {
            synchronized (SHARED_LOCK) {
                return SHARED_STATE.puddleSpread;
            }
        }
        return puddleSpread;
    }

    private float resolveSharedDelta(float requestedDeltaSeconds) {
        long nowNanos = System.nanoTime();
        float requested = clamp(requestedDeltaSeconds, 0f, MAX_DELTA_SECONDS);

        if (sharedLastAdvanceNanos == 0L) {
            sharedLastAdvanceNanos = nowNanos;
            return requested;
        }

        float actualElapsed = clamp(
                (nowNanos - sharedLastAdvanceNanos) / 1_000_000_000f,
                0f,
                MAX_DELTA_SECONDS
        );
        sharedLastAdvanceNanos = nowNanos;

        // Very small inter-surface calls are valid: their sum still follows real monotonic time.
        // If a device reports no measurable elapsed time, avoid inventing extra simulation time.
        return actualElapsed;
    }

    private void loadSharedState() {
        wetness = SHARED_STATE.wetness;
        puddleCoverage = SHARED_STATE.puddleCoverage;
        soilSaturation = SHARED_STATE.soilSaturation;
        surfaceWater = SHARED_STATE.surfaceWater;
        rainExposureSeconds = SHARED_STATE.rainExposureSeconds;
        puddleDepth = SHARED_STATE.puddleDepth;
        puddleSpread = SHARED_STATE.puddleSpread;
    }

    private void saveSharedState() {
        SHARED_STATE.wetness = wetness;
        SHARED_STATE.puddleCoverage = puddleCoverage;
        SHARED_STATE.soilSaturation = soilSaturation;
        SHARED_STATE.surfaceWater = surfaceWater;
        SHARED_STATE.rainExposureSeconds = rainExposureSeconds;
        SHARED_STATE.puddleDepth = puddleDepth;
        SHARED_STATE.puddleSpread = puddleSpread;
    }

    private void updateVisiblePuddleCoverage() {
        // Existing world shader already owns the irregular puddle mask and rain-ring pattern.
        // This blended signal controls how strongly that low-point pattern fills and broadens.
        puddleCoverage = Math.min(
                wetness,
                clamp01(puddleDepth * 0.62f + puddleSpread * 0.48f)
        );
    }

    private void normalizeState() {
        wetness = clamp01(wetness);
        soilSaturation = clamp01(soilSaturation);
        surfaceWater = clamp01(surfaceWater);
        puddleDepth = Math.min(clamp01(puddleDepth), wetness);
        puddleSpread = Math.min(clamp01(puddleSpread), wetness);
        rainExposureSeconds = clamp(
                rainExposureSeconds,
                0f,
                MAX_RAIN_EXPOSURE_SECONDS
        );
    }

    private static float approach(float current, float target, float amount) {
        float t = clamp01(amount);
        return current + (target - current) * t;
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * clamp01(t);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        if (edge1 <= edge0) return value >= edge1 ? 1f : 0f;
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Fixed primitive holder; never allocated from the GL frame path. */
    private static final class SharedState {
        float wetness;
        float puddleCoverage;
        float soilSaturation;
        float surfaceWater;
        float rainExposureSeconds;
        float puddleDepth;
        float puddleSpread;

        void clear() {
            wetness = 0f;
            puddleCoverage = 0f;
            soilSaturation = 0f;
            surfaceWater = 0f;
            rainExposureSeconds = 0f;
            puddleDepth = 0f;
            puddleSpread = 0f;
        }
    }
}
