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
        SkyGradientProfile skyProfile = SkyGradientProfile.resolve(state);

        float parallax = clamp(parallaxOffset, 0f, 1f);
        float sunX = celestialX(sky.getSunAzimuth(), parallax);
        float sunY = celestialY(sky.getSunAltitude());
        float moonX = celestialX(sky.getMoonAzimuth(), parallax);
        float moonY = celestialY(sky.getMoonAltitude());

        return new GlSceneSnapshot(
                skyProfile.topR,
                skyProfile.topG,
                skyProfile.topB,
                skyProfile.midR,
                skyProfile.midG,
                skyProfile.midB,
                skyProfile.horizonR,
                skyProfile.horizonG,
                skyProfile.horizonB,
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
