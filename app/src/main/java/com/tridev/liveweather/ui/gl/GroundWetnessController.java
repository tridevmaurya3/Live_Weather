package com.tridev.liveweather.ui.gl;

/**
 * Allocation-free physical moisture memory for the shared Hero / Live Wallpaper world.
 *
 * <p>This controller never changes resolved weather truth. It models what recent observed
 * rain/drizzle has already done to the rendered ground: surface wetness, soil saturation and
 * standing water. R8.3 separates those reservoirs so a long downpour can accumulate deeper,
 * broader puddles while a short shower mostly darkens the surface.</p>
 *
 * <p>The state is advanced with monotonic frame delta time. Large lifecycle gaps are clamped so
 * returning to the app or wallpaper cannot cause a one-frame flood or instant dry-out.</p>
 */
public final class GroundWetnessController {

    private static final float MAX_DELTA_SECONDS = 2f;
    private static final float PRECIPITATION_ACTIVE_THRESHOLD = 0.015f;
    private static final float MAX_RAIN_EXPOSURE_SECONDS = 1_800f;

    /** Visible dampness used by terrain/road/vegetation materials. */
    private float wetness;

    /** Visible low-point standing-water coverage used by the world shader. */
    private float puddleCoverage;

    /** Slower underground reservoir; keeps the world damp after surface water drains. */
    private float soilSaturation;

    /** Normalized amount of water currently available to collect in low points. */
    private float surfaceWater;

    /** Bounded recent liquid-precipitation exposure used to distinguish showers from downpours. */
    private float rainExposureSeconds;

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
        float targetPuddles = basinWater
                * saturatedBase
                * clamp01(0.42f + liquidLoad * 0.58f);
        float puddleResponse = 0.010f + liquidLoad * 0.052f;

        // Standing water builds gradually; no one-frame puddle pop when rain begins.
        if (targetPuddles > puddleCoverage) {
            puddleCoverage = approach(
                    puddleCoverage,
                    targetPuddles,
                    puddleResponse * dt
            );
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

        float retainedPuddleTarget = smoothstep(0.06f, 0.78f, surfaceWater)
                * smoothstep(0.34f, 0.90f, soilSaturation);
        if (retainedPuddleTarget < puddleCoverage) {
            float puddleDrainRate = 0.00028f + warm * 0.00058f + wind * 0.00048f;
            puddleCoverage = Math.max(
                    retainedPuddleTarget,
                    puddleCoverage - puddleDrainRate * dt
            );
        }
    }

    /** Seeds lifecycle-restored state without allocating a snapshot object. */
    public void reset(float wetness, float puddleCoverage) {
        this.wetness = clamp01(wetness);
        this.puddleCoverage = Math.min(clamp01(puddleCoverage), this.wetness);

        // Reconstruct conservative physical reservoirs from the public visual state. This keeps
        // reset/test behavior compatible while giving R8.3 enough information to dry naturally.
        soilSaturation = this.wetness;
        surfaceWater = clamp01(
                this.puddleCoverage * 0.85f
                        + Math.max(0f, this.wetness - 0.75f) * 0.25f
        );
        rainExposureSeconds = this.puddleCoverage * 120f;
        normalizeState();
    }

    public float getWetness() {
        return wetness;
    }

    public float getPuddleCoverage() {
        return puddleCoverage;
    }

    /** Package-private diagnostics/tests: not a new weather truth field. */
    float getSoilSaturation() {
        return soilSaturation;
    }

    /** Package-private diagnostics/tests: normalized low-point water reservoir. */
    float getSurfaceWater() {
        return surfaceWater;
    }

    private void normalizeState() {
        wetness = clamp01(wetness);
        soilSaturation = clamp01(soilSaturation);
        surfaceWater = clamp01(surfaceWater);
        rainExposureSeconds = clamp(
                rainExposureSeconds,
                0f,
                MAX_RAIN_EXPOSURE_SECONDS
        );

        // Visible puddles can never exceed the overall wet surface state.
        puddleCoverage = Math.min(clamp01(puddleCoverage), wetness);
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
}
