package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.AirQualityReality;
import com.tridev.liveweather.domain.LiveConditionResolver;
import com.tridev.liveweather.domain.SkyRealityEngine;
import com.tridev.liveweather.domain.SkyRealityState;

/**
 * Converts live weather + AQI haze + local astronomy into animation intensities.
 */
public final class DynamicRealityComposer {

    private DynamicRealityComposer() {
    }

    @NonNull
    public static SceneState compose(
            @NonNull WeatherResponse weather,
            double latitude,
            double longitude,
            long epochMillis
    ) {
        return compose(weather, null, latitude, longitude, epochMillis);
    }

    @NonNull
    public static SceneState compose(
            @NonNull WeatherResponse weather,
            @Nullable AirQualityResponse airQuality,
            double latitude,
            double longitude,
            long epochMillis
    ) {
        SkyRealityState sky = SkyRealityEngine.calculate(
                weather,
                latitude,
                longitude,
                epochMillis
        );
        LiveConditionResolver.ResolvedCondition condition = LiveConditionResolver.resolve(weather);
        WeatherResponse.CurrentWeather current = weather.getCurrent();

        double clouds = normalized(current == null ? null : current.getCloudCover(), 100d);
        double visibilityMeters = current == null || current.getVisibility() == null
                ? 16000d
                : Math.max(0d, current.getVisibility());
        double visibilityFactor = clamp(visibilityMeters / 16000d, 0.08d, 1d);
        double airHaze = AirQualityReality.hazeIntensity(airQuality);
        visibilityFactor = clamp(visibilityFactor * (1d - airHaze * 0.48d), 0.05d, 1d);

        double windSpeed = current == null || current.getWindSpeed10m() == null
                ? 0d
                : Math.max(0d, current.getWindSpeed10m());
        double windDirection = current == null || current.getWindDirection10m() == null
                ? 0d
                : current.getWindDirection10m();
        double windStrength = clamp(windSpeed / 65d, 0d, 1d);

        int code = condition.getWeatherCode() == null ? 0 : condition.getWeatherCode();
        double precipSignal = Math.max(0d, condition.getPrecipitationSignalMm());
        double currentRain = current == null || current.getRain() == null ? 0d : current.getRain();
        double currentShowers = current == null || current.getShowers() == null ? 0d : current.getShowers();
        double currentSnow = current == null || current.getSnowfall() == null ? 0d : current.getSnowfall();

        boolean drizzleCode = code >= 51 && code <= 57;
        boolean rainCode = (code >= 61 && code <= 67) || (code >= 80 && code <= 82) || code >= 95;
        boolean snowCode = (code >= 71 && code <= 77) || code == 85 || code == 86;
        boolean fogCode = code == 45 || code == 48;
        boolean stormCode = code >= 95;

        double drizzle = drizzleCode
                ? clamp(0.25d + precipSignal * 0.45d, 0.18d, 0.72d)
                : 0d;
        double rain = rainCode || currentRain > 0d || currentShowers > 0d || precipSignal > 0.06d
                ? clamp(
                        0.24d + precipSignal * 0.62d + currentRain * 0.38d + currentShowers * 0.48d,
                        0.18d,
                        1d
                )
                : 0d;
        if (drizzleCode && rain < 0.4d) {
            rain = 0d;
        }
        double snow = snowCode || currentSnow > 0d
                ? clamp(0.28d + currentSnow * 0.30d, 0.20d, 1d)
                : 0d;
        double fog = fogCode
                ? clamp(0.55d + (1d - visibilityFactor) * 0.45d, 0.45d, 1d)
                : clamp((1d - visibilityFactor) * 0.78d, 0d, 0.85d);
        double storm = stormCode
                ? clamp(0.55d + rain * 0.45d, 0.55d, 1d)
                : 0d;

        if (rain > 0d || drizzle > 0d || snow > 0d || storm > 0d) {
            clouds = Math.max(
                    clouds,
                    0.68d + 0.30d * Math.max(rain, Math.max(snow, storm))
            );
        }
        clouds = clamp(clouds, 0d, 1d);

        double weatherTransparency = clamp(
                1d
                        - clouds * 0.82d
                        - fog * 0.70d
                        - rain * 0.30d
                        - snow * 0.20d
                        - airHaze * 0.38d,
                0.025d,
                1d
        );
        double sunVisibility = sky.getSunAltitude() > -4d
                ? clamp(weatherTransparency * (0.72d + 0.28d * visibilityFactor), 0d, 1d)
                : 0d;
        double moonVisibility = sky.getMoonAltitude() > -4d
                ? clamp(weatherTransparency * visibilityFactor, 0d, 1d)
                : 0d;
        double starVisibility = clamp(
                sky.getStarVisibilityPercent() / 100d
                        * weatherTransparency
                        * (1d - airHaze * 0.45d),
                0d,
                1d
        );
        double sceneLight = clamp(
                sky.getAmbientLightPercent() / 100d
                        * (1d - clouds * 0.22d - storm * 0.20d - airHaze * 0.08d),
                0.01d,
                1d
        );

        return new SceneState(
                sky,
                condition,
                clouds,
                rain,
                drizzle,
                snow,
                fog,
                storm,
                airHaze,
                windSpeed,
                windDirection,
                windStrength,
                visibilityFactor,
                sunVisibility,
                moonVisibility,
                starVisibility,
                sceneLight
        );
    }

    private static double normalized(Double value, double divisor) {
        if (value == null) {
            return 0d;
        }
        return clamp(value / divisor, 0d, 1d);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
