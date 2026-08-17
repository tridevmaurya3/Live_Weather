package com.tridev.liveweather.ui.gl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.SkyRealityState;
import com.tridev.liveweather.domain.scene.CloudPresenceState;
import com.tridev.liveweather.domain.scene.DynamicRealityComposer;
import com.tridev.liveweather.domain.scene.SceneState;

/**
 * Converts the existing shared reality engine into GPU uniforms.
 *
 * Sun/Moon positions, lunar phase and astronomical star visibility remain
 * authoritative outputs of the shared reality engines. The GPU adapter only
 * converts them to normalized/perceptual display uniforms. For the cinematic
 * reality pass it also supplies observer latitude and local sidereal angle so
 * the star renderer can rotate the celestial sphere instead of pinning stars to
 * screen coordinates.
 */
public final class GlRealityAdapter {

    private static final double TWO_PI = Math.PI * 2d;

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
        CloudPresenceState clouds = state.getCloudPresence();
        SkyGradientProfile skyProfile = SkyGradientProfile.resolve(state);

        float parallax = clamp(parallaxOffset, 0f, 1f);
        float sunX = celestialX(sky.getSunAzimuth(), parallax);
        float sunY = celestialY(sky.getSunAltitude());
        float moonX = celestialX(sky.getMoonAzimuth(), parallax);
        float moonY = celestialY(sky.getMoonAltitude());

        double resolvedStarVisibility = clamp(
                (sky.getStarVisibilityPercent() / 100d)
                        * (1d - state.getAirHazeIntensity() * 0.45d),
                0d,
                1d
        );
        float gpuStarVisibility = resolvedStarVisibility <= 0d
                ? 0f
                : clamp01((float) Math.pow(resolvedStarVisibility, 0.38d));

        float observerLatitudeRadians = (float) Math.toRadians(clamp(latitude, -89.9d, 89.9d));
        float localSiderealRadians = (float) resolveLocalSiderealRadians(epochMillis, longitude);
        float thermalBias = resolveThermalBias(weather.getCurrent());
        float windDirectionRadians = (float) Math.toRadians(state.getWindDirectionDegrees());

        /*
         * R11 world-light contract: weather truth stays unchanged. Only renderer-facing
         * scene illumination receives bounded sun/cloud modulation so terrain, vegetation,
         * water and wet-ground materials react to the same real sky. Broken-cloud variation
         * is time-based and slow; full overcast and clear sky remain stable.
         */
        float worldSceneLight = clamp01((float) WorldLightingController.resolveSceneLight(
                state.getSceneLight(),
                sky.getSunAltitude(),
                state.getSunVisibility(),
                clouds.getCloudAmount(),
                clouds.getDensity(),
                clouds.getMidLayer(),
                clouds.getNearLayer(),
                clouds.getStormCeiling(),
                clouds.getBrightness(),
                state.getStormIntensity(),
                state.getFogIntensity(),
                state.getAirHazeIntensity(),
                state.getWindStrength(),
                windDirectionRadians,
                epochMillis
        ));

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
                gpuStarVisibility,
                observerLatitudeRadians,
                localSiderealRadians,
                clamp01((float) clouds.getCloudAmount()),
                clamp01((float) clouds.getDensity()),
                clamp01((float) clouds.getFarLayer()),
                clamp01((float) clouds.getMidLayer()),
                clamp01((float) clouds.getNearLayer()),
                clamp01((float) clouds.getStormCeiling()),
                clamp01((float) clouds.getBrightness()),
                clamp01((float) state.getRainIntensity()),
                clamp01((float) state.getDrizzleIntensity()),
                clamp01((float) state.getSnowIntensity()),
                clamp01((float) state.getFogIntensity()),
                clamp01((float) state.getStormIntensity()),
                clamp01((float) state.getAirHazeIntensity()),
                clamp01((float) state.getWindStrength()),
                windDirectionRadians,
                worldSceneLight,
                thermalBias,
                clamp01((float) state.getVisibilityFactor()),
                parallax
        );
    }

    /** Greenwich mean sidereal time + observer longitude, normalized to 0..2π. */
    private static double resolveLocalSiderealRadians(long epochMillis, double longitudeDegrees) {
        double julianDate = epochMillis / 86_400_000d + 2_440_587.5d;
        double daysSinceJ2000 = julianDate - 2_451_545.0d;
        double gmstDegrees = 280.46061837d + 360.98564736629d * daysSinceJ2000;
        double localDegrees = normalizeDegrees(gmstDegrees + longitudeDegrees);
        double radians = Math.toRadians(localDegrees) % TWO_PI;
        return radians < 0d ? radians + TWO_PI : radians;
    }

    private static float resolveThermalBias(@Nullable WeatherResponse.CurrentWeather current) {
        if (current == null) return 0f;
        Double apparent = current.getApparentTemperature();
        Double measured = current.getTemperature2m();
        if (apparent == null && measured == null) return 0f;
        double celsius = apparent != null ? apparent : measured;

        double warm = clamp((celsius - 27d) / 18d, 0d, 1d);
        double cold = clamp((16d - celsius) / 18d, 0d, 1d);
        return (float) clamp(warm - cold, -1d, 1d);
    }

    private static float celestialX(double azimuth, float parallaxOffset) {
        double normalized = normalizeDegrees(azimuth);
        float x = (float) (normalized / 360d);
        x += (parallaxOffset - 0.5f) * 0.026f;
        x %= 1f;
        return x < 0f ? x + 1f : x;
    }

    private static float celestialY(double altitude) {
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
