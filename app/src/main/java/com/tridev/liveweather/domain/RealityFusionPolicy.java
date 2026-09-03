package com.tridev.liveweather.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.RainViewerResponse;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Truth labels shared by UI surfaces; radar metadata is never treated as local rain pixels. */
public final class RealityFusionPolicy {

    private static final double ARRIVAL_SIGNAL_MM = 0.08d;
    private static final long RADAR_FRESH_MILLIS = 15 * 60 * 1000L;

    private RealityFusionPolicy() {}

    @NonNull
    public static RealityState resolve(@Nullable WeatherResponse response) {
        LiveConditionResolver.ResolvedCondition condition = LiveConditionResolver.resolve(response);
        if (response == null || response.getCurrent() == null) {
            return new RealityState(condition, Confidence.UNAVAILABLE, Intensity.NONE, null);
        }
        String source = condition.getSource();
        Confidence confidence = source.startsWith("Current precipitation")
                || source.startsWith("Thunderstorm") ? Confidence.HIGH
                : source.startsWith("Corroborated") ? Confidence.MEDIUM
                : source.contains("unconfirmed") ? Confidence.LOW : Confidence.MEDIUM;
        double wet = condition.getPrecipitationSignalMm();
        Intensity intensity = wet <= 0.02d ? Intensity.NONE
                : wet < 0.4d ? Intensity.LIGHT
                : wet < 1.5d ? Intensity.MODERATE : Intensity.HEAVY;
        return new RealityState(condition, confidence, intensity, nextArrivalMinutes(response));
    }

    @NonNull
    public static RadarEvidence radarEvidence(
            @Nullable RainViewerResponse radar,
            long savedAtMillis,
            long nowMillis
    ) {
        if (radar == null || radar.getPastFrames().isEmpty() || savedAtMillis <= 0L) {
            return RadarEvidence.UNAVAILABLE;
        }
        if (nowMillis < savedAtMillis || nowMillis - savedAtMillis > RADAR_FRESH_MILLIS) {
            return RadarEvidence.STALE_METADATA;
        }
        return RadarEvidence.OBSERVED_METADATA_AVAILABLE;
    }

    @Nullable
    private static Integer nextArrivalMinutes(@NonNull WeatherResponse response) {
        WeatherResponse.CurrentWeather current = response.getCurrent();
        WeatherResponse.Minutely15Weather minutely = response.getMinutely15();
        if (current == null || current.getTime() == null || minutely == null
                || minutely.getTime() == null) return null;
        try {
            LocalDateTime now = LocalDateTime.parse(current.getTime());
            List<String> times = minutely.getTime();
            for (int index = 0; index < times.size(); index++) {
                String time = times.get(index);
                if (time == null) continue;
                long minutes = Duration.between(now, LocalDateTime.parse(time)).toMinutes();
                if (minutes <= 0L || minutes > 180L) continue;
                double wet = maxAt(minutely, index);
                Integer code = WeatherFormatter.valueAt(minutely.getWeatherCode(), index);
                if (wet >= ARRIVAL_SIGNAL_MM || (isWetCode(code) && wet >= 0.05d)) {
                    return (int) minutes;
                }
            }
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private static double maxAt(WeatherResponse.Minutely15Weather data, int index) {
        return Math.max(valueAt(data.getPrecipitation(), index),
                Math.max(valueAt(data.getRain(), index),
                        Math.max(valueAt(data.getShowers(), index), valueAt(data.getSnowfall(), index))));
    }

    private static double valueAt(@Nullable List<Double> values, int index) {
        Double value = WeatherFormatter.valueAt(values, index);
        return value == null ? 0d : Math.max(0d, value);
    }

    private static boolean isWetCode(@Nullable Integer code) {
        return code != null && ((code >= 51 && code <= 86) || code >= 95);
    }

    public enum Confidence { HIGH, MEDIUM, LOW, UNAVAILABLE }
    public enum Intensity { NONE, LIGHT, MODERATE, HEAVY }
    public enum RadarEvidence { OBSERVED_METADATA_AVAILABLE, STALE_METADATA, UNAVAILABLE }

    public static final class RealityState {
        private final LiveConditionResolver.ResolvedCondition condition;
        private final Confidence confidence;
        private final Intensity intensity;
        private final Integer nextPrecipitationMinutes;

        RealityState(LiveConditionResolver.ResolvedCondition condition, Confidence confidence,
                     Intensity intensity, Integer nextPrecipitationMinutes) {
            this.condition = condition;
            this.confidence = confidence;
            this.intensity = intensity;
            this.nextPrecipitationMinutes = nextPrecipitationMinutes;
        }

        @NonNull public LiveConditionResolver.ResolvedCondition getCondition() { return condition; }
        @NonNull public Confidence getConfidence() { return confidence; }
        @NonNull public Intensity getIntensity() { return intensity; }
        @Nullable public Integer getNextPrecipitationMinutes() { return nextPrecipitationMinutes; }
    }
}
