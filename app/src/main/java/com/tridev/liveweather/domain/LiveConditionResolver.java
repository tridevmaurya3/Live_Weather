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
 * Resolves the most useful "right now" weather condition from multiple model
 * signals without treating every trace precipitation value as confirmed rain.
 *
 * Phase 18 accuracy rule: adjacent previous/next 15-minute slots may corroborate
 * the current/nearest interval, but an adjacent slot alone must never be promoted
 * into a claim that precipitation or a thunderstorm is happening right now.
 */
public final class LiveConditionResolver {

    private static final double TRACE_MM = 0.02d;
    private static final double PERSISTENT_SLOT_MM = 0.05d;
    private static final double CORROBORATED_MM = 0.08d;
    private static final double STRONG_ISOLATED_MM = 0.30d;

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
        SignalWindow window = minutelyWindow(response);

        // Current WMO thunderstorm is authoritative. The nearest 15-minute slot
        // may also support a current classification, but previous/next slots are
        // future/past context only and cannot independently become "now".
        Integer severeCode = severeCode(currentCode, window);
        if (severeCode != null) {
            return build(
                    severeCode,
                    isDay,
                    "Thunderstorm weather code",
                    Math.max(currentSignal.maxWet(), window.center.maxWet())
            );
        }

        // If the provider's CURRENT WMO state itself says precipitation, keep it.
        if (isPrecipitationCode(currentCode)) {
            return build(
                    currentCode,
                    isDay,
                    "Current precipitation weather code",
                    Math.max(currentSignal.maxWet(), window.center.maxWet())
            );
        }

        if (confirmed(
                currentSignal.snowfall,
                window.previous.snowfall,
                window.center.snowfall,
                window.next.snowfall,
                isSnowCode(window.center.code)
        )) {
            double snow = Math.max(currentSignal.snowfall, window.center.snowfall);
            int code = snow >= 0.8d ? 75 : snow >= 0.25d ? 73 : 71;
            return build(code, isDay, "Corroborated snow signal", snow);
        }

        if (confirmed(
                currentSignal.showers,
                window.previous.showers,
                window.center.showers,
                window.next.showers,
                isShowerCode(window.center.code)
        )) {
            double showers = Math.max(currentSignal.showers, window.center.showers);
            int code = showers >= 1.5d ? 82 : showers >= 0.4d ? 81 : 80;
            return build(code, isDay, "Corroborated shower signal", showers);
        }

        double currentRainWet = Math.max(currentSignal.rain, currentSignal.precipitation);
        double previousRainWet = Math.max(window.previous.rain, window.previous.precipitation);
        double centerRainWet = Math.max(window.center.rain, window.center.precipitation);
        double nextRainWet = Math.max(window.next.rain, window.next.precipitation);

        if (confirmed(
                currentRainWet,
                previousRainWet,
                centerRainWet,
                nextRainWet,
                isRainOrDrizzleCode(window.center.code)
        )) {
            double wet = Math.max(currentRainWet, centerRainWet);
            int code;
            if (isRainOrDrizzleCode(window.center.code)) {
                code = window.center.code;
            } else {
                code = wet >= 1.5d ? 65 : wet >= 0.4d ? 63 : 61;
            }
            return build(code, isDay, "Corroborated short-term rain signal", wet);
        }

        // Weak/adjacent model values remain context only. They must not switch
        // the current condition or Live Wallpaper into a rain state.
        double rawWet = Math.max(currentSignal.maxWet(), window.maxWet());
        String source = rawWet > TRACE_MM
                ? "Current weather model · weak precipitation signal unconfirmed"
                : "Current weather model";
        return build(currentCode, isDay, source, 0d);
    }

    private static boolean confirmed(
            double current,
            double previous,
            double center,
            double next,
            boolean centerWeatherCodeSupportsPrecipitation
    ) {
        // A strong amount is allowed to establish NOW only when it belongs to
        // current or the nearest 15-minute interval. A future/past slot alone
        // cannot establish current precipitation.
        if (Math.max(current, center) >= STRONG_ISOLATED_MM) {
            return true;
        }

        boolean centerPersistent = center >= PERSISTENT_SLOT_MM;
        boolean adjacentPersistent = previous >= PERSISTENT_SLOT_MM
                || next >= PERSISTENT_SLOT_MM;
        double relevantStrongest = Math.max(current, center);

        if (centerPersistent && adjacentPersistent
                && Math.max(relevantStrongest, Math.max(previous, next)) >= CORROBORATED_MM) {
            return true;
        }

        if (current >= CORROBORATED_MM
                && (centerPersistent || adjacentPersistent)) {
            return true;
        }

        return centerWeatherCodeSupportsPrecipitation
                && center >= PERSISTENT_SLOT_MM;
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
    private static SignalWindow minutelyWindow(@NonNull WeatherResponse response) {
        WeatherResponse.Minutely15Weather minutely = response.getMinutely15();
        WeatherResponse.CurrentWeather current = response.getCurrent();
        if (minutely == null || minutely.getTime() == null || minutely.getTime().isEmpty()
                || current == null || current.getTime() == null) {
            return SignalWindow.EMPTY;
        }

        int centerIndex = nearestIndex(current.getTime(), minutely.getTime());
        return new SignalWindow(
                signalAt(minutely, centerIndex - 1),
                signalAt(minutely, centerIndex),
                signalAt(minutely, centerIndex + 1)
        );
    }

    @NonNull
    private static Signal signalAt(
            @NonNull WeatherResponse.Minutely15Weather minutely,
            int index
    ) {
        if (index < 0 || minutely.getTime() == null || index >= minutely.getTime().size()) {
            return Signal.EMPTY;
        }
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
                if (candidateText == null) continue;
                try {
                    LocalDateTime candidate = LocalDateTime.parse(candidateText);
                    long distance = Math.abs(Duration.between(current, candidate).toMinutes());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestIndex = index;
                    }
                } catch (DateTimeParseException ignored) {
                }
            }
            return bestIndex;
        } catch (DateTimeParseException ignored) {
            return 0;
        }
    }

    @Nullable
    private static Integer severeCode(@Nullable Integer currentCode, @NonNull SignalWindow window) {
        if (currentCode != null && currentCode >= 95) return currentCode;
        if (window.center.code != null && window.center.code >= 95) return window.center.code;
        return null;
    }

    private static boolean isPrecipitationCode(@Nullable Integer code) {
        return isRainOrDrizzleCode(code)
                || isSnowCode(code)
                || isShowerCode(code)
                || (code != null && code >= 95);
    }

    private static boolean isRainOrDrizzleCode(@Nullable Integer code) {
        return code != null && code >= 51 && code <= 67;
    }

    private static boolean isSnowCode(@Nullable Integer code) {
        return code != null && ((code >= 71 && code <= 77) || code == 85 || code == 86);
    }

    private static boolean isShowerCode(@Nullable Integer code) {
        return code != null && code >= 80 && code <= 82;
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

        double maxWet() {
            return Math.max(Math.max(precipitation, rain), Math.max(showers, snowfall));
        }
    }

    private static final class SignalWindow {
        static final SignalWindow EMPTY = new SignalWindow(Signal.EMPTY, Signal.EMPTY, Signal.EMPTY);

        final Signal previous;
        final Signal center;
        final Signal next;

        SignalWindow(Signal previous, Signal center, Signal next) {
            this.previous = previous;
            this.center = center;
            this.next = next;
        }

        double maxWet() {
            return Math.max(previous.maxWet(), Math.max(center.maxWet(), next.maxWet()));
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
