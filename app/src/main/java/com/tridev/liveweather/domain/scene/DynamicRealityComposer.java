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

        int code = condition.getWeatherCode() == null ? 0 : condition.getWeatherCode();

        double visibilityMeters = current == null || current.getVisibility() == null
                ? 16000d
                : Math.max(0d, current.getVisibility());
        double visibilityFactor = clamp(visibilityMeters / 16000d, 0.08d, 1d);
        double airHaze = AirQualityReality.hazeIntensity(airQuality);
        visibilityFactor = clamp(visibilityFactor * (1d - airHaze * 0.48d), 0.05d, 1d);

        double windSpeed = current == null || current.getWindSpeed10m() == null
                ? 0d
                : Math.max(0d, current.getWindSpeed10m());
        double windGust = current == null || current.getWindGusts10m() == null
                ? windSpeed
                : Math.max(windSpeed, current.getWindGusts10m());
        double windDirection = current == null || current.getWindDirection10m() == null
                ? 0d
                : current.getWindDirection10m();

        /*
         * Phase 20A high-gust motion contract:
         * sustained wind owns the base drift; verified current gusts may add
         * bounded turbulence but may never invent storm/rain state. The mapping
         * deliberately reacts to both gust excess and gust-to-sustained ratio so
         * a real squall is visually stronger without making normal wind frantic.
         */
        double sustainedMotion = clamp(windSpeed / 58d, 0d, 1d);
        double gustExcess = Math.max(0d, windGust - windSpeed);
        double gustMotion = clamp(gustExcess / 34d, 0d, 1d);
        double gustRatio = windSpeed <= 1d
                ? clamp(windGust / 38d, 0d, 1d)
                : clamp(((windGust / windSpeed) - 1d) / 1.15d, 0d, 1d);
        double verifiedGust = clamp(Math.max(gustMotion, gustRatio * 0.72d), 0d, 1d);
        double windStrength = clamp(
                sustainedMotion * 0.78d + verifiedGust * 0.42d,
                0d,
                1d
        );

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

        /*
         * Confidence-aware precipitation contract:
         * raw model trace values do not independently start visual rain. The
         * LiveConditionResolver must first resolve the current state to a rain,
         * shower or thunderstorm code. Raw amounts then refine intensity only.
         */
        double rain = rainCode
                ? clamp(
                        0.24d + precipSignal * 0.62d + currentRain * 0.38d + currentShowers * 0.48d,
                        0.18d,
                        1d
                )
                : 0d;
        if (drizzleCode && rain < 0.4d) {
            rain = 0d;
        }

        double snow = snowCode
                ? clamp(0.28d + Math.max(currentSnow, precipSignal) * 0.30d, 0.20d, 1d)
                : 0d;
        double fog = fogCode
                ? clamp(0.55d + (1d - visibilityFactor) * 0.45d, 0.45d, 1d)
                : clamp((1d - visibilityFactor) * 0.78d, 0d, 0.85d);
        double storm = stormCode
                ? clamp(0.55d + rain * 0.45d, 0.55d, 1d)
                : 0d;

        CloudPresenceState cloudPresence = CloudPresenceResolver.resolve(
                weather,
                condition,
                rain,
                drizzle,
                snow,
                fog,
                storm,
                airHaze,
                visibilityFactor
        );
        double clouds = cloudPresence.getCloudAmount();

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

        /*
         * FINAL MOON VISIBILITY CONTRACT
         *
         * The texture/shader owns lunar phase geometry. The composer must NOT
         * multiply visibility by phase illumination again, otherwise a real thin
         * crescent gets attenuated twice and disappears. Here we only model
         * whether the lunar surface can be seen through atmosphere/daylight.
         */
        double moonIllumination = clamp(sky.getMoonIlluminationPercent() / 100d, 0d, 1d);
        double moonVisibility = 0d;

        if (sky.getMoonAltitude() > -4d) {
            double atmosphericVisibility = clamp(
                    weatherTransparency * visibilityFactor,
                    0d,
                    1d
            );

            if (sky.getSunAltitude() > -6d) {
                double daylightStrength = clamp((sky.getSunAltitude() + 6d) / 46d, 0d, 1d);

                if (moonIllumination < 0.006d && sky.getSunAltitude() > -2d) {
                    moonVisibility = 0d;
                } else {
                    double daylightContrast = 1d - daylightStrength * 0.34d;
                    double crescentReadability = moonIllumination >= 0.012d
                            ? 0.62d
                            : 0.42d;

                    moonVisibility = clamp(
                            atmosphericVisibility
                                    * Math.max(daylightContrast, crescentReadability),
                            0d,
                            1d
                    );
                }
            } else {
                moonVisibility = atmosphericVisibility;
            }
        }

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
                cloudPresence,
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
