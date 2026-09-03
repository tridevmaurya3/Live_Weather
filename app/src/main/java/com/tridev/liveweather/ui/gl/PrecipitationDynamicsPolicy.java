package com.tridev.liveweather.ui.gl;

/** Allocation-free motion/optics parameters for shared app and wallpaper precipitation. */
public final class PrecipitationDynamicsPolicy {

    private PrecipitationDynamicsPolicy() {}

    public static float fallSpeedScale(float rain, float drizzle, float wind) {
        float liquid = Math.max(clamp01(rain), clamp01(drizzle) * 0.55f);
        return clamp(0.72f + liquid * 0.50f + clamp01(wind) * 0.34f, 0.72f, 1.56f);
    }

    public static float leanScale(float wind, float storm) {
        return clamp(0.018f + clamp01(wind) * 0.20f + clamp01(storm) * 0.035f,
                0.018f, 0.253f);
    }

    public static float turbulence(float wind, float storm) {
        return clamp01(clamp01(wind) * 0.58f + clamp01(storm) * 0.34f);
    }

    public static float nearLayerStrength(float rain, float detailScale) {
        if (detailScale < 0.62f) return 0f;
        return clamp(clamp01(rain) * 0.40f - 0.025f, 0f, 0.46f);
    }

    public static float wetGlassStrength(float rain, float drizzle, float detailScale) {
        if (detailScale <= 0.56f) return 0f;
        float liquid = Math.max(clamp01(rain), clamp01(drizzle) * 0.58f);
        return smoothstep(0.42f, 0.86f, liquid);
    }

    public static float snowTurbulence(float wind, float storm, float snow) {
        return clamp01(clamp01(wind) * 0.54f
                + clamp01(storm) * 0.28f + clamp01(snow) * 0.12f);
    }

    private static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp((value - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float value) { return clamp(value, 0f, 1f); }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
