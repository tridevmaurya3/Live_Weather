package com.tridev.liveweather.ui.gl;

/**
 * Allocation-free shared visual wind sampler for Hero and Live Wallpaper renderers.
 *
 * <p>The resolved weather engine remains authoritative for base wind strength and direction.
 * This controller adds only deterministic presentation-scale gusts and a very small directional
 * sway so rain, snow, fog and scenery can eventually respond to one coherent air mass instead of
 * each shader inventing an unrelated gust rhythm.</p>
 *
 * <p>Callers provide a shared monotonic time value. The controller stores its latest sample in
 * primitive fields and allocates nothing on the render hot path.</p>
 */
final class UnifiedWindController {

    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private float baseStrength;
    private float effectiveStrength;
    private float directionRadians;
    private float side;
    private float forward;
    private float gustFactor = 1f;
    private float turbulence;

    /**
     * Updates the coherent visual wind sample.
     *
     * @param resolvedStrength normalized weather-truth wind strength, 0..1
     * @param resolvedDirectionRadians weather-truth wind direction in radians
     * @param stormIntensity normalized resolved storm intensity, 0..1
     * @param monotonicSeconds shared monotonic render time in seconds
     */
    void sample(
            float resolvedStrength,
            float resolvedDirectionRadians,
            float stormIntensity,
            float monotonicSeconds
    ) {
        baseStrength = clamp01(resolvedStrength);
        float storm = clamp01(stormIntensity);
        float time = Math.max(0f, monotonicSeconds);
        float baseDirection = wrapPositiveRadians(resolvedDirectionRadians);

        if (baseStrength <= 0.0001f) {
            effectiveStrength = 0f;
            directionRadians = baseDirection;
            side = (float) Math.sin(directionRadians);
            forward = (float) Math.cos(directionRadians);
            gustFactor = 1f;
            turbulence = 0f;
            return;
        }

        // Three bounded frequencies create natural-looking gust clusters without random state,
        // allocations or renderer-specific clocks.
        float slowPhase = time * (0.22f + baseStrength * 0.30f) + baseDirection * 0.71f;
        float primary = unitSine(slowPhase);
        float secondary = unitSine(slowPhase * 2.17f + 1.73f);
        float micro = unitSine(time * (1.10f + baseStrength * 0.62f) + 2.31f);
        float gustShape = clamp01(primary * 0.56f + secondary * 0.29f + micro * 0.15f);

        float gustAmplitude = clamp(
                0.06f + baseStrength * 0.22f + storm * 0.16f,
                0.06f,
                0.42f
        );
        gustFactor = 1f + (gustShape - 0.5f) * 2f * gustAmplitude;
        effectiveStrength = clamp01(baseStrength * gustFactor);

        // Direction sway is intentionally subtle and never replaces the resolved direction.
        float swayWave = (float) Math.sin(time * (0.16f + baseStrength * 0.18f) + 0.83f);
        float swayRadians = swayWave * (0.018f + baseStrength * 0.045f + storm * 0.025f);
        directionRadians = wrapPositiveRadians(baseDirection + swayRadians);

        side = (float) Math.sin(directionRadians);
        forward = (float) Math.cos(directionRadians);

        turbulence = clamp01(
                baseStrength * 0.50f
                        + storm * 0.32f
                        + Math.abs(gustFactor - 1f) * 0.55f
        );
    }

    float getBaseStrength() {
        return baseStrength;
    }

    float getEffectiveStrength() {
        return effectiveStrength;
    }

    float getDirectionRadians() {
        return directionRadians;
    }

    float getSide() {
        return side;
    }

    float getForward() {
        return forward;
    }

    float getGustFactor() {
        return gustFactor;
    }

    float getTurbulence() {
        return turbulence;
    }

    private static float unitSine(float radians) {
        return 0.5f + 0.5f * (float) Math.sin(radians);
    }

    private static float wrapPositiveRadians(float value) {
        value %= TWO_PI;
        return value < 0f ? value + TWO_PI : value;
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
