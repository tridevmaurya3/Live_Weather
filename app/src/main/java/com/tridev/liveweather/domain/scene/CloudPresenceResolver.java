package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Shared cloud-presence resolver. Stage 8 keeps total-cloud compatibility while
 * using provider low/mid/high layers whenever the active payload contains them.
 */
public final class CloudPresenceResolver {

    private CloudPresenceResolver() {
    }

    @NonNull
    public static CloudPresenceState resolve(
            @NonNull WeatherResponse weather,
            @NonNull LiveConditionResolver.ResolvedCondition condition,
            double rainIntensity,
            double drizzleIntensity,
            double snowIntensity,
            double fogIntensity,
            double stormIntensity,
            double airHazeIntensity,
            double visibilityFactor
    ) {
        WeatherResponse.CurrentWeather current = weather.getCurrent();
        int code = condition.getWeatherCode() == null ? 0 : condition.getWeatherCode();

        double amount = baseCloudAmount(weather, current);
        if (code == 1) {
            amount = Math.max(amount, 0.14d);
        } else if (code == 2) {
            amount = Math.max(amount, 0.38d);
        } else if (code == 3) {
            amount = Math.max(amount, 0.82d);
        }

        double precipitation = Math.max(
                Math.max(rainIntensity, drizzleIntensity),
                snowIntensity
        );
        if (precipitation > 0d) {
            amount = Math.max(amount, 0.64d + precipitation * 0.28d);
        }
        if (stormIntensity > 0d) {
            amount = Math.max(amount, 0.86d + stormIntensity * 0.12d);
        }
        amount = clamp01(amount);

        CloudLayerProfile layers = CloudLayerProfile.resolve(
                current,
                amount,
                precipitation,
                stormIntensity
        );
        double severeEnvelope = SevereWeatherVisualPolicy.cloudTransitionEnvelope(weather);

        double verticalMass = Math.max(
                layers.getLow(),
                Math.max(layers.getMid() * 0.92d, layers.getHigh() * 0.78d)
        );
        double density = clamp01(
                amount * 0.56d
                        + verticalMass * 0.26d
                        + precipitation * 0.16d
                        + stormIntensity * 0.24d
                        + severeEnvelope * 0.08d
                        + (1d - clamp01(visibilityFactor)) * 0.06d
        );

        // Existing renderer depth contract: far=high, mid=mid-level, near=low-level.
        double farLayer = amount < 0.04d
                ? 0d
                : clamp01(layers.getHigh() * 0.92d + amount * 0.10d);
        double midLayer = amount < 0.07d
                ? 0d
                : clamp01(
                        layers.getMid() * 0.94d
                                + amount * 0.08d
                                + precipitation * 0.08d
                );
        double nearLayer = amount < 0.10d
                ? 0d
                : clamp01(
                        layers.getLow() * 0.96d
                                + amount * 0.05d
                                + precipitation * 0.14d
                                + stormIntensity * 0.12d
                );

        // The transition envelope affects cloud depth/darkness only. Current storm
        // truth still exclusively owns stormIntensity and lightning scheduling.
        double stormCeiling = clamp01(
                stormIntensity * 0.86d
                        + severeEnvelope * 0.38d
                        + Math.max(0d, layers.getLow() - 0.68d) * 0.34d
                        + Math.max(0d, layers.getMid() - 0.72d) * 0.24d
                        + precipitation * 0.14d
        );

        double brightness = clamp(
                1d
                        - stormIntensity * 0.58d
                        - severeEnvelope * 0.18d
                        - precipitation * 0.20d
                        - airHazeIntensity * 0.10d
                        - fogIntensity * 0.06d
                        - (1d - clamp01(visibilityFactor)) * 0.08d,
                0.18d,
                1d
        );

        CloudPresenceState.Mode mode;
        if (stormIntensity > 0.08d) {
            mode = CloudPresenceState.Mode.STORM;
        } else if (precipitation > 0.06d) {
            mode = CloudPresenceState.Mode.PRECIPITATION;
        } else if (amount >= 0.82d) {
            mode = CloudPresenceState.Mode.OVERCAST;
        } else if (amount >= 0.58d) {
            mode = CloudPresenceState.Mode.BROKEN;
        } else if (amount >= 0.25d) {
            mode = CloudPresenceState.Mode.SCATTERED;
        } else if (amount >= 0.06d) {
            mode = CloudPresenceState.Mode.WISPS;
        } else {
            mode = CloudPresenceState.Mode.CLEAR;
        }

        return new CloudPresenceState(
                mode,
                amount,
                density,
                farLayer,
                midLayer,
                nearLayer,
                stormCeiling,
                brightness
        );
    }

    private static double baseCloudAmount(
            @NonNull WeatherResponse weather,
            @Nullable WeatherResponse.CurrentWeather current
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
        return clamp01(percent / 100d);
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
            center = exact >= 0
                    ? exact
                    : nearestTimeIndex(times, current.getTime(), center);
        }

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
        return Math.min(4, size - 1);
    }

    private static double clamp01(double value) {
        return clamp(value, 0d, 1d);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
