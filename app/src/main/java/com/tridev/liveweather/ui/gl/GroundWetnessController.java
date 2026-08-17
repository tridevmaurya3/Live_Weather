package com.tridev.liveweather.ui.gl;

/**
 * Allocation-free physical moisture memory for the shared Hero / Live Wallpaper world.
 *
 * <p>This controller intentionally does not change resolved weather truth. It only remembers
 * how the already-observed rain/drizzle has affected the rendered ground so the world does not
 * become visually dry the instant precipitation stops.</p>
 *
 * <p>The state is advanced with monotonic frame delta time by the GL pipeline. Large lifecycle
 * gaps are clamped so returning to the app or wallpaper cannot cause a one-frame jump.</p>
 */
public final class GroundWetnessController {

    private static final float MAX_DELTA_SECONDS = 2f;
    private static final float PRECIPITATION_ACTIVE_THRESHOLD = 0.015f;

    private float wetness;
    private float puddleCoverage;

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

        // Drizzle wets surfaces efficiently but creates substantially less standing water.
        float precipitation = Math.max(rain, drizzle * 0.60f);
        float stormBoost = precipitation > PRECIPITATION_ACTIVE_THRESHOLD
                ? storm * 0.16f
                : 0f;
        float wettingDrive = clamp01(precipitation + stormBoost);

        if (wettingDrive > PRECIPITATION_ACTIVE_THRESHOLD) {
            float targetWetness = clamp01(0.18f + wettingDrive * 0.82f);
            float wettingResponse = 0.014f + wettingDrive * 0.058f;

            // Existing moisture never falls while precipitation is still active.
            float approachedWetness = approach(wetness, targetWetness, wettingResponse * dt);
            wetness = Math.max(wetness, approachedWetness);

            float saturation = smoothstep(0.42f, 0.92f, wetness);
            float standingWaterDrive = clamp01(rain * 0.82f + storm * rain * 0.22f);
            float targetPuddles = saturation * standingWaterDrive;
            float puddleResponse = 0.010f + standingWaterDrive * 0.050f;

            if (targetPuddles > puddleCoverage) {
                puddleCoverage = approach(
                        puddleCoverage,
                        targetPuddles,
                        puddleResponse * dt
                );
            }
        } else {
            // Ground dries slowly. Warm air and wind accelerate evaporation without erasing
            // the recent-rain memory in a few frames.
            float groundDryRate = 0.00018f + warm * 0.00050f + wind * 0.00028f;
            float puddleDrainRate = 0.00032f + warm * 0.00062f + wind * 0.00050f;

            wetness = Math.max(0f, wetness - groundDryRate * dt);
            puddleCoverage = Math.max(0f, puddleCoverage - puddleDrainRate * dt);
        }

        // Puddles cannot physically outlive the amount of retained surface moisture.
        puddleCoverage = Math.min(puddleCoverage, smoothstep(0.30f, 0.96f, wetness));
        wetness = clamp01(wetness);
        puddleCoverage = clamp01(puddleCoverage);
    }

    /** Seeds lifecycle-restored state without allocating a snapshot object. */
    public void reset(float wetness, float puddleCoverage) {
        this.wetness = clamp01(wetness);
        this.puddleCoverage = Math.min(
                clamp01(puddleCoverage),
                smoothstep(0.30f, 0.96f, this.wetness)
        );
    }

    public float getWetness() {
        return wetness;
    }

    public float getPuddleCoverage() {
        return puddleCoverage;
    }

    private static float approach(float current, float target, float amount) {
        float t = clamp01(amount);
        return current + (target - current) * t;
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
