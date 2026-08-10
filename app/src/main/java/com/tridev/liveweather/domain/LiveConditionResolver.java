package com.tridev.liveweather.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Resolves the most useful "right now" weather condition from multiple signals.
 *
 * Open-Meteo current conditions are model-derived. A WMO clear-sky code can
 * occasionally lag a local precipitation signal, so active precipitation is
 * deliberately allowed to override a clear/cloud-only code. This resolver is
 * shared by the app UI and future wallpaper renderer.
 */
public final class LiveConditionResolver {

    private static final double TRACE_PRECIP_MM = 0.02d;

    private LiveConditionResolver() {
    }

    @NonNull
    public static ResolvedCondition resolve(@Nullable WeatherResponse response) {
        if (response == null || response.getCurrent() == null) {
            return new ResolvedCondition(null, null, "Weather unavailable", "No live signal", 0d);
        }

        WeatherResponse.CurrentWeather current = response.getCurrent();
        Integer currentCode = current.getWeatherCode();
        Integer isDay = current.getIsDay();

        Signal currentSignal = new Signal(
                value(current.getPrecipitation()),
                value(current.getRain()),
                value(current.getShowers()),
                value(current.getSnowfall()),
                currentCode
        );

        Signal minutelySignal = nearestMinutelySignal(response);

        // Thunderstorm/severe WMO state remains authoritative when present.
        Integer severeCode = moreSeverePrecipitationCode(currentCode, minutelySignal.code);
        if (severeCode != null && severeCode >= 95) {
            return build(severeCode, isDay, "Thunderstorm signal", Math.max(currentSignal.precipitation, minutelySignal.precipitation));
        }

        // Snow has priority over rain when snow accumulation is present.
        double snow = Math.max(currentSignal.snowfall, minutelySignal.snowfall);
        if (snow > TRACE_PRECIP_MM) {
            int code = snow >= 0.8d ? 75 : snow >= 0.25d ? 73 : 71;
            return build(code, isDay, "Live precipitation signal", snow);
        }

        double showers = Math.max(currentSignal.showers, minutelySignal.showers);
        if (showers > TRACE_PRECIP_MM) {
            int code = showers >= 1.5d ? 82 : showers >= 0.4d ? 81 : 80;
            return build(code, isDay, "15-minute shower signal", showers);
        }

        double rain = Math.max(currentSignal.rain, minutelySignal.rain);
        double precipitation = Math.max(currentSignal.precipitation, minutelySignal.precipitation);
        double wetSignal = Math.max(rain, precipitation);
        if (wetSignal > TRACE_PRECIP_MM) {
            int code = wetSignal >= 1.5d ? 65 : wetSignal >= 0.4d ? 63 : 61;
            return build(code, isDay, "Live rain signal", wetSignal);
        }

        // If the nearest 15-minute WMO code itself says precipitation, prefer it.
        if (isPrecipitationCode(minutelySignal.code)) {
            return build(minutelySignal.code, isDay, "15-minute weather code", minutelySignal.precipitation);
        }

        // Otherwise use the current WMO state.
        return build(currentCode, isDay, "Current weather model", currentSignal.precipitation);
    }

    @NonNull
    private static ResolvedCondition build(
            @Nullable Integer code,
            @Nullable Integer isDay,
            @NonNull String source,
            double precipitation
    ) {
        return new ResolvedCondition(
                code,
                isDay,
                WeatherFormatter.condition(code),
                source,
                precipitation
        );
    }

    @NonNull
    private static Signal nearestMinutelySignal(@NonNull WeatherResponse response) {
        WeatherResponse.Minutely15Weather minutely = response.getMinutely15();
        WeatherResponse.CurrentWeather current = response.getCurrent();
        if (minutely == null || minutely.getTime() == null || minutely.getTime().isEmpty()
                || current == null || current.getTime() == null) {
            return Signal.EMPTY;
        }

        int index = nearestIndex(current.getTime(), minutely.getTime());
        return new Signal(
                valueAt(minutely.getPrecipitation(), index),
                valueAt(minutely.getRain(), index),
                valueAt(minutely.getShowers(), index),
                valueAt(minutely.getSnowfall(), index),
                WeatherFormatter.valueAt(minutely.getWeatherCode(), index)
        );
    }

    private static int nearestIndex(@NonNull String currentTime, @NonNull List<String> times) {
        try {
            LocalDateTime current = LocalDateTime.parse(currentTime);
            long bestDistance = Long.MAX_VALUE;
            int bestIndex = 0;
            for (int index = 0; index < times.size(); index++) {
                String candidateText = times.get(index);
                if (candidateText == null) {
                    continue;
                }
                LocalDateTime candidate = LocalDateTime.parse(candidateText);
                long distance = Math.abs(Duration.between(current, candidate).toMinutes());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = index;
                }
            }
            return bestIndex;
        } catch (DateTimeParseException ignored) {
            return 0;
        }
    }

    @Nullable
    private static Integer moreSeverePrecipitationCode(
            @Nullable Integer first,
            @Nullable Integer second
    ) {
        if (first != null && first >= 95) {
            return first;
        }
        if (second != null && second >= 95) {
            return second;
        }
        return null;
    }

    private static boolean isPrecipitationCode(@Nullable Integer code) {
        if (code == null) {
            return false;
        }
        return (code >= 51 && code <= 77)
                || (code >= 80 && code <= 86)
                || code >= 95;
    }

    private static double value(@Nullable Double value) {
        return value == null ? 0d : Math.max(0d, value);
    }

    private static double valueAt(@Nullable List<Double> values, int index) {
        Double value = WeatherFormatter.valueAt(values, index);
        return value(value);
    }

    private static final class Signal {
        static final Signal EMPTY = new Signal(0d, 0d, 0d, 0d, null);

        final double precipitation;
        final double rain;
        final double showers;
        final double snowfall;
        final Integer code;

        Signal(double precipitation, double rain, double showers, double snowfall, Integer code) {
            this.precipitation = precipitation;
            this.rain = rain;
            this.showers = showers;
            this.snowfall = snowfall;
            this.code = code;
        }
    }

    public static final class ResolvedCondition {
        private final Integer weatherCode;
        private final Integer isDay;
        private final String label;
        private final String source;
        private final double precipitationSignalMm;

        ResolvedCondition(
                Integer weatherCode,
                Integer isDay,
                String label,
                String source,
                double precipitationSignalMm
        ) {
            this.weatherCode = weatherCode;
            this.isDay = isDay;
            this.label = label;
            this.source = source;
            this.precipitationSignalMm = precipitationSignalMm;
        }

        @Nullable
        public Integer getWeatherCode() {
            return weatherCode;
        }

        @Nullable
        public Integer getIsDay() {
            return isDay;
        }

        @NonNull
        public String getLabel() {
            return label;
        }

        @NonNull
        public String getSource() {
            return source;
        }

        public double getPrecipitationSignalMm() {
            return precipitationSignalMm;
        }
    }
}
