package com.tridev.liveweather.ui.gl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.SkyRealityState;
import com.tridev.liveweather.domain.scene.DynamicRealityComposer;
import com.tridev.liveweather.domain.scene.SceneState;

/**
 * Converts the existing shared reality engine into GPU uniforms.
 *
 * Important: Moon phase and star visibility are NOT re-invented here. The same
 * DynamicRealityComposer/SkyRealityEngine used by the app remains the source of
 * truth for astronomical position, lunar illumination and atmospheric
 * obstruction.
 */
public final class GlRealityAdapter {

    private GlRealityAdapter() {
    }

    @NonNull
    public static GlSceneSnapshot compose(
            @NonNull WeatherResponse weather,
            @Nullable AirQualityResponse airQuality,
            double latitude,
            double longitude,
            long epochMillis,
            float parallaxOffset
    ) {
        SceneState state = DynamicRealityComposer.compose(
                weather,
                airQuality,
                latitude,
                longitude,
                epochMillis
        );
        SkyRealityState sky = state.getSky();

        float[] top = new float[3];
        float[] mid = new float[3];
        float[] horizon = new float[3];
        skyPalette(sky.getSkyStage(), top, mid, horizon);

        float weatherDarkening = clamp01((float) (
                state.getCloudCover() * 0.28d
                        + state.getStormIntensity() * 0.46d
                        + state.getFogIntensity() * 0.11d
                        + state.getRainIntensity() * 0.09d
                        + state.getAirHazeIntensity() * 0.08d
        ));
        float lightFactor = Math.max(0.28f, 1f - weatherDarkening);
        scale(top, lightFactor);
        scale(mid, Math.min(1f, lightFactor + 0.05f));
        scale(horizon, Math.min(1f, lightFactor + 0.16f));

        float parallax = clamp(parallaxOffset, 0f, 1f);
        float sunX = celestialX(sky.getSunAzimuth(), parallax);
        float sunY = celestialY(sky.getSunAltitude());
        float moonX = celestialX(sky.getMoonAzimuth(), parallax);
        float moonY = celestialY(sky.getMoonAltitude());

        return new GlSceneSnapshot(
                top[0], top[1], top[2],
                mid[0], mid[1], mid[2],
                horizon[0], horizon[1], horizon[2],
                sunX,
                sunY,
                clamp01((float) state.getSunVisibility()),
                (float) sky.getSunAltitude(),
                moonX,
                moonY,
                clamp01((float) state.getMoonVisibility()),
                clamp01((float) (sky.getMoonIlluminationPercent() / 100d)),
                (float) Math.toRadians(normalizeDegrees(sky.getMoonPhaseAngleDegrees())),
                (float) sky.getMoonAltitude(),
                clamp01((float) state.getStarVisibility()),
                clamp01((float) state.getCloudCover()),
                clamp01((float) state.getRainIntensity()),
                clamp01((float) state.getDrizzleIntensity()),
                clamp01((float) state.getSnowIntensity()),
                clamp01((float) state.getFogIntensity()),
                clamp01((float) state.getStormIntensity()),
                clamp01((float) state.getAirHazeIntensity()),
                clamp01((float) state.getWindStrength()),
                (float) Math.toRadians(state.getWindDirectionDegrees()),
                clamp01((float) state.getSceneLight()),
                clamp01((float) state.getVisibilityFactor()),
                parallax
        );
    }

    private static float celestialX(double azimuth, float parallaxOffset) {
        double normalized = normalizeDegrees(azimuth);
        float x = (float) (normalized / 360d);
        x += (parallaxOffset - 0.5f) * 0.026f;
        x %= 1f;
        return x < 0f ? x + 1f : x;
    }

    private static float celestialY(double altitude) {
        // Matches the existing Canvas renderer's horizon mapping, converted to
        // normalized top-origin coordinates for the fragment shader.
        double normalized = clamp(altitude, -7d, 90d);
        return (float) (0.86d - ((normalized + 7d) / 97d) * 0.77d);
    }

    private static void skyPalette(
            @Nullable String stage,
            float[] top,
            float[] mid,
            float[] horizon
    ) {
        String value = stage == null ? "" : stage;
        if (value.contains("Daylight")) {
            rgb(top, 45, 115, 183);
            rgb(mid, 91, 157, 207);
            rgb(horizon, 174, 204, 219);
        } else if (value.contains("Golden")) {
            rgb(top, 47, 73, 127);
            rgb(mid, 150, 100, 105);
            rgb(horizon, 238, 151, 82);
        } else if (value.contains("Civil")) {
            rgb(top, 35, 48, 100);
            rgb(mid, 91, 66, 121);
            rgb(horizon, 175, 92, 110);
        } else if (value.contains("Nautical")) {
            rgb(top, 17, 31, 70);
            rgb(mid, 36, 46, 82);
            rgb(horizon, 69, 64, 100);
        } else if (value.contains("Astronomical twilight")) {
            rgb(top, 8, 18, 45);
            rgb(mid, 19, 29, 59);
            rgb(horizon, 42, 43, 72);
        } else {
            rgb(top, 3, 9, 24);
            rgb(mid, 8, 17, 37);
            rgb(horizon, 15, 27, 50);
        }
    }

    private static void rgb(float[] target, int r, int g, int b) {
        target[0] = r / 255f;
        target[1] = g / 255f;
        target[2] = b / 255f;
    }

    private static void scale(float[] color, float factor) {
        color[0] = clamp01(color[0] * factor);
        color[1] = clamp01(color[1] * factor);
        color[2] = clamp01(color[2] * factor);
    }

    private static double normalizeDegrees(double degrees) {
        double value = degrees % 360d;
        return value < 0d ? value + 360d : value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
