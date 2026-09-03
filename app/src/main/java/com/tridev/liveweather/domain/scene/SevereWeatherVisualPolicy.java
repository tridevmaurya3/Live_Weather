package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Bounded cloud-only transition cue around verified thunderstorm intervals.
 *
 * This policy may darken/deepen clouds shortly before or after a provider storm
 * interval, but it never changes SceneState.stormIntensity and therefore cannot
 * invent lightning, rain, alerts or a thunderstorm condition.
 */
public final class SevereWeatherVisualPolicy {

    private SevereWeatherVisualPolicy() {
    }

    public static double cloudTransitionEnvelope(@NonNull WeatherResponse weather) {
        WeatherResponse.CurrentWeather current = weather.getCurrent();
        int currentCode = current == null || current.getWeatherCode() == null
                ? 0
                : current.getWeatherCode();
        if (isThunderstorm(currentCode)) return 1d;

        WeatherResponse.Minutely15Weather minutely = weather.getMinutely15();
        if (minutely == null || minutely.getWeatherCode() == null
                || minutely.getWeatherCode().isEmpty()) return 0d;

        List<Integer> codes = minutely.getWeatherCode();
        int center = resolveCenter(minutely.getTime(), current == null ? null : current.getTime(), codes.size());
        double envelope = 0d;

        if (isThunderstorm(valueAt(codes, center + 1))) envelope = Math.max(envelope, 0.48d);
        if (isThunderstorm(valueAt(codes, center + 2))) envelope = Math.max(envelope, 0.30d);
        if (isThunderstorm(valueAt(codes, center - 1))) envelope = Math.max(envelope, 0.36d);
        if (isThunderstorm(valueAt(codes, center - 2))) envelope = Math.max(envelope, 0.18d);
        return envelope;
    }

    public static boolean isThunderstorm(@Nullable Integer weatherCode) {
        return weatherCode != null && weatherCode >= 95 && weatherCode <= 99;
    }

    private static int resolveCenter(
            @Nullable List<String> times,
            @Nullable String currentTime,
            int size
    ) {
        int fallback = size <= 0 ? 0 : Math.min(4, size - 1);
        if (times == null || times.isEmpty() || currentTime == null) return fallback;
        int exact = times.indexOf(currentTime);
        if (exact >= 0) return exact;
        try {
            LocalDateTime target = LocalDateTime.parse(currentTime);
            long best = Long.MAX_VALUE;
            int bestIndex = fallback;
            for (int i = 0; i < times.size(); i++) {
                String value = times.get(i);
                if (value == null) continue;
                try {
                    long distance = Math.abs(Duration.between(target, LocalDateTime.parse(value)).getSeconds());
                    if (distance < best) {
                        best = distance;
                        bestIndex = i;
                    }
                } catch (DateTimeParseException ignored) {
                }
            }
            return bestIndex;
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    @Nullable
    private static Integer valueAt(@NonNull List<Integer> values, int index) {
        return index < 0 || index >= values.size() ? null : values.get(index);
    }
}
