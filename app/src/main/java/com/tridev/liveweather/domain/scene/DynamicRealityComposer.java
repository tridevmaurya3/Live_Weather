package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.AirQualityReality;
import com.tridev.liveweather.domain.LiveConditionResolver;
import com.tridev.liveweather.domain.SkyRealityEngine;
import com.tridev.liveweather.domain.SkyRealityState;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

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

        /*
         * CLOUD REALITY CONTRACT
         *
         * A single current cloud-cover model point can briefly understate a mixed
         * sky. Phase 6 already requests 15-minute cloud cover, so the scene now
         * blends the current value with the nearest 15-minute neighbourhood.
         * WMO codes 1/2/3 provide restrained minimum floors for mainly-clear,
         * partly-cloudy and overcast conditions. This prevents a "partly cloudy"
         * weather state from rendering as an empty blue sky while still avoiding
         * decorative/fake clouds when the source data is genuinely clear.
         */
        double clouds = resolveCloudCover(weather, current, code);

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

        /*
         * FINAL MOON VISIBILITY CONTRACT
         *
         * The texture owns lunar phase geometry. The composer must NOT multiply
         * visibility by phase illumination again, otherwise a real thin crescent
         * gets attenuated twice and disappears. Here we only model whether the
         * lunar surface can be seen through the atmosphere/daylight.
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

    private static double resolveCloudCover(
            @NonNull WeatherResponse weather,
            @Nullable WeatherResponse.CurrentWeather current,
            int weatherCode
    ) {
        Double currentPercent = current == null ? null : current.getCloudCover();
        Double minutelyPercent = nearestMinutelyCloudCover(weather, current);

        double percent;
        if (currentPercent != null && minutelyPercent != null) {
            percent = clamp(currentPercent, 0d, 100d) * 0.45d
                    + clamp(minutelyPercent, 0d, 100d) * 0.55d;
        } else if (minutelyPercent != null) {
            percent = clamp(minutelyPercent, 0d, 100d);
        } else if (currentPercent != null) {
            percent = clamp(currentPercent, 0d, 100d);
        } else {
            percent = 0d;
        }

        // WMO interpretation floors. They correct visual contradictions, not
        // arbitrary decoration: code 2 must not render as a cloudless scene.
        if (weatherCode == 1) {
            percent = Math.max(percent, 14d);
        } else if (weatherCode == 2) {
            percent = Math.max(percent, 38d);
        } else if (weatherCode == 3) {
            percent = Math.max(percent, 82d);
        }

        return clamp(percent / 100d, 0d, 1d);
    }

    @Nullable
    private static Double nearestMinutelyCloudCover(
            @NonNull WeatherResponse weather,
            @Nullable WeatherResponse.CurrentWeather current
    ) {
        WeatherResponse.Minutely15Weather minutely = weather.getMinutely15();
        if (minutely == null || minutely.getCloudCover() == null
                || minutely.getCloudCover().isEmpty()) {
            return null;
        }

        List<Double> covers = minutely.getCloudCover();
        List<String> times = minutely.getTime();
        int center = fallbackCurrentMinutelyIndex(covers.size());

        if (current != null && current.getTime() != null && times != null && !times.isEmpty()) {
            int exact = times.indexOf(current.getTime());
            if (exact >= 0) {
                center = exact;
            } else {
                center = nearestTimeIndex(times, current.getTime(), center);
            }
        }

        // Weighted 45-minute neighbourhood smooths one noisy grid point while
        // still reacting quickly to a mixed/cloudy sky moving through the area.
        double sum = 0d;
        double weightSum = 0d;
        for (int offset = -1; offset <= 1; offset++) {
            int index = center + offset;
            if (index < 0 || index >= covers.size()) continue;
            Double value = covers.get(index);
            if (value == null) continue;
            double weight = offset == 0 ? 2d : 1d;
            sum += clamp(value, 0d, 100d) * weight;
            weightSum += weight;
        }
        return weightSum <= 0d ? null : sum / weightSum;
    }

    private static int nearestTimeIndex(
            @NonNull List<String> times,
            @NonNull String currentTime,
            int fallback
    ) {
        try {
            LocalDateTime target = LocalDateTime.parse(currentTime);
            long bestDistance = Long.MAX_VALUE;
            int bestIndex = fallback;
            for (int index = 0; index < times.size(); index++) {
                String value = times.get(index);
                if (value == null) continue;
                try {
                    LocalDateTime candidate = LocalDateTime.parse(value);
                    long distance = Math.abs(Duration.between(target, candidate).getSeconds());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestIndex = index;
                    }
                } catch (DateTimeParseException ignored) {
                }
            }
            return bestIndex;
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    private static int fallbackCurrentMinutelyIndex(int size) {
        if (size <= 0) return 0;
        // Repository requests four past 15-minute intervals, so index 4 is the
        // best fallback for the current slot when provider timestamps do not match.
        return Math.min(4, size - 1);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
