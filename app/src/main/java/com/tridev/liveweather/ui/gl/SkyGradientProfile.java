package com.tridev.liveweather.ui.gl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.domain.scene.SceneState;

/**
 * Weather- and astronomy-aware atmospheric sky palette.
 *
 * ODM-4 adds a restrained lunar night fill: only an actually visible,
 * illuminated Moon can lift the night palette, and cloud cover attenuates that
 * contribution. No decorative Moon/night brightness is invented.
 */
public final class SkyGradientProfile {

    public final float topR;
    public final float topG;
    public final float topB;
    public final float midR;
    public final float midG;
    public final float midB;
    public final float horizonR;
    public final float horizonG;
    public final float horizonB;

    private SkyGradientProfile(float[] top, float[] mid, float[] horizon) {
        topR = top[0];
        topG = top[1];
        topB = top[2];
        midR = mid[0];
        midG = mid[1];
        midB = mid[2];
        horizonR = horizon[0];
        horizonG = horizon[1];
        horizonB = horizon[2];
    }

    @NonNull
    public static SkyGradientProfile resolve(@NonNull SceneState state) {
        float[] top = new float[3];
        float[] mid = new float[3];
        float[] horizon = new float[3];
        baseForStage(state.getSky().getSkyStage(), top, mid, horizon);

        float cloud = clamp01((float) state.getCloudCover());
        float rain = clamp01((float) Math.max(state.getRainIntensity(), state.getDrizzleIntensity()));
        float storm = clamp01((float) state.getStormIntensity());
        float fog = clamp01((float) state.getFogIntensity());
        float haze = clamp01((float) state.getAirHazeIntensity());
        float sunAltitude = (float) state.getSky().getSunAltitude();
        boolean daylight = sunAltitude > -4f;

        float twilightWarmth = (float) AtmosphereLightPolicy.twilightWarmth(sunAltitude, cloud);
        if (twilightWarmth > 0f) {
            blend(mid, rgb(188, 105, 112), twilightWarmth * 0.20f);
            blend(horizon, rgb(246, 157, 88), twilightWarmth * 0.38f);
        }

        if (daylight) {
            if (cloud >= 0.24f) {
                float amount = clamp01((cloud - 0.20f) / 0.55f) * 0.34f;
                blend(top, rgb(54, 111, 158), amount * 0.62f);
                blend(mid, rgb(111, 159, 184), amount * 0.72f);
                blend(horizon, rgb(190, 207, 211), amount);
            }

            if (cloud >= 0.70f) {
                float amount = clamp01((cloud - 0.68f) / 0.32f) * 0.64f;
                blend(top, rgb(66, 91, 111), amount * 0.82f);
                blend(mid, rgb(118, 139, 151), amount);
                blend(horizon, rgb(181, 190, 193), amount);
            }

            if (rain > 0.02f) {
                float amount = 0.22f + rain * 0.46f;
                blend(top, rgb(45, 72, 96), amount * 0.72f);
                blend(mid, rgb(82, 106, 121), amount * 0.88f);
                blend(horizon, rgb(145, 158, 162), amount);
            }

            if (storm > 0.02f) {
                float amount = 0.36f + storm * 0.54f;
                blend(top, rgb(22, 34, 49), amount);
                blend(mid, rgb(54, 67, 78), amount);
                blend(horizon, rgb(111, 120, 124), amount * 0.92f);
            }
        } else {
            float weather = clamp01(cloud * 0.38f + rain * 0.25f + storm * 0.48f);
            if (weather > 0f) {
                blend(top, rgb(5, 10, 20), weather * 0.68f);
                blend(mid, rgb(16, 25, 37), weather * 0.76f);
                blend(horizon, rgb(36, 44, 53), weather * 0.88f);
            }

            float moonIllumination = clamp01((float) (state.getSky().getMoonIlluminationPercent() / 100d));
            float moonVisibility = clamp01((float) state.getMoonVisibility());
            float lunarFill = moonVisibility
                    * moonIllumination
                    * clamp01(1f - cloud * 0.62f)
                    * clamp01(1f - fog * 0.72f)
                    * 0.18f;
            if (lunarFill > 0.002f) {
                blend(top, rgb(12, 23, 42), lunarFill * 0.34f);
                blend(mid, rgb(23, 39, 61), lunarFill * 0.68f);
                blend(horizon, rgb(44, 58, 75), lunarFill);
            }
        }

        float horizonAtmosphere = (float) AtmosphereLightPolicy.horizonDepth(
                fog, haze, state.getVisibilityFactor());
        if (horizonAtmosphere > 0f) {
            float[] veil = daylight ? rgb(194, 199, 196) : rgb(76, 82, 88);
            blend(horizon, veil, horizonAtmosphere * 0.52f);
            blend(mid, veil, horizonAtmosphere * 0.13f);
        }

        float sceneLight = clamp01((float) state.getSceneLight());
        float physicalExposure = (float) AtmosphereLightPolicy.daylightExposure(
                sunAltitude, cloud, fog, haze, storm);
        float exposure = clamp01(physicalExposure * (0.86f + sceneLight * 0.14f));
        scale(top, exposure);
        scale(mid, Math.min(1f, exposure + 0.07f));
        scale(horizon, Math.min(1f, exposure + 0.17f));

        return new SkyGradientProfile(top, mid, horizon);
    }

    private static void baseForStage(
            @Nullable String stage,
            float[] top,
            float[] mid,
            float[] horizon
    ) {
        String value = stage == null ? "" : stage;
        if (value.contains("Daylight")) {
            copy(rgb(31, 104, 181), top);
            copy(rgb(79, 153, 207), mid);
            copy(rgb(169, 205, 222), horizon);
        } else if (value.contains("Golden")) {
            copy(rgb(38, 68, 126), top);
            copy(rgb(134, 98, 115), mid);
            copy(rgb(238, 159, 91), horizon);
        } else if (value.contains("Civil")) {
            copy(rgb(28, 44, 96), top);
            copy(rgb(83, 65, 119), mid);
            copy(rgb(176, 96, 116), horizon);
        } else if (value.contains("Nautical")) {
            copy(rgb(14, 27, 66), top);
            copy(rgb(34, 45, 82), mid);
            copy(rgb(72, 67, 101), horizon);
        } else if (value.contains("Astronomical twilight")) {
            copy(rgb(7, 16, 41), top);
            copy(rgb(18, 28, 56), mid);
            copy(rgb(43, 44, 70), horizon);
        } else {
            copy(rgb(2, 7, 20), top);
            copy(rgb(7, 15, 34), mid);
            copy(rgb(18, 28, 48), horizon);
        }
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[]{r / 255f, g / 255f, b / 255f};
    }

    private static void copy(float[] source, float[] target) {
        target[0] = source[0];
        target[1] = source[1];
        target[2] = source[2];
    }

    private static void blend(float[] target, float[] other, float amount) {
        float t = clamp01(amount);
        target[0] = target[0] + (other[0] - target[0]) * t;
        target[1] = target[1] + (other[1] - target[1]) * t;
        target[2] = target[2] + (other[2] - target[2]) * t;
    }

    private static void scale(float[] color, float factor) {
        color[0] = clamp01(color[0] * factor);
        color[1] = clamp01(color[1] * factor);
        color[2] = clamp01(color[2] * factor);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
