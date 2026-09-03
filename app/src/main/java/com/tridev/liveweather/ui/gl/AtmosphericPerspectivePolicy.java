package com.tridev.liveweather.ui.gl;

import androidx.annotation.NonNull;

/**
 * Stage 15 renderer-only atmospheric perspective mapping.
 *
 * The shared reality engine already resolves measured/modelled visibility, humidity and AQI into
 * one smoothed 0..1 visibility factor. This policy converts that factor into distance-dependent
 * transmission without changing fog, haze, precipitation, weather code or any forecast truth.
 *
 * The mapping is deliberately strongest at long range and weakest in the foreground: distant
 * terrain disappears first as real visibility falls, while nearby objects remain readable.
 */
final class AtmosphericPerspectivePolicy {

    private AtmosphericPerspectivePolicy() {
    }

    static void resolveInto(@NonNull Sample out, float visibilityFactor) {
        float visibility = sanitizeVisibility(visibilityFactor);
        float loss = 1f - visibility;

        // Long-range contrast should collapse first. The slight quadratic term prevents a hard
        // linear-looking fade at very low visibility while remaining cheaper than per-frame pow().
        out.farTransmission = clamp(1f - loss * (0.92f + 0.08f * loss), 0.08f, 1f);
        out.midTransmission = clamp(1f - loss * 0.72f, 0.25f, 1f);
        out.nearTransmission = clamp(1f - loss * 0.38f, 0.62f, 1f);
        out.microVisibility = clamp(1f - loss * 0.62f, 0.34f, 1f);
    }

    private static float sanitizeVisibility(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 1f;
        return clamp(value, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Sample {
        float farTransmission = 1f;
        float midTransmission = 1f;
        float nearTransmission = 1f;
        float microVisibility = 1f;
    }
}
