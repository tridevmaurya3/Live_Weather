package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Stage 14 near-now continuity policy.
 *
 * <p>This policy consumes only provider minutely_15 interval values. It may expose subtle
 * approach/exit envelopes for cloud depth, but it never changes current weather code, rain,
 * snowfall, storm intensity, lightning, alerts or precipitation truth.</p>
 *
 * <p>Thunderstorm intervals are intentionally excluded here because Stage 8
 * {@link SevereWeatherVisualPolicy} already owns severe cloud approach/exit cues.</p>
 */
public final class NearNowWeatherEvolutionPolicy {

    private static final State NEUTRAL = new State(0d, 0d, 0d);

    private NearNowWeatherEvolutionPolicy() {
    }

    @NonNull
    public static State resolve(@NonNull WeatherResponse weather) {
        WeatherResponse.Minutely15Weather minutely = weather.getMinutely15();
        if (minutely == null) return NEUTRAL;

        int size = seriesSize(minutely);
        if (size <= 0) return NEUTRAL;

        WeatherResponse.CurrentWeather current = weather.getCurrent();
        if (isCurrentPrecipitating(current)) {
            // Current truth already owns the active precipitation visuals.
            return NEUTRAL;
        }

        int center = resolveCenter(
                minutely.getTime(),
                current == null ? null : current.getTime(),
                size
        );

        double futurePrecipitation = weightedFuturePrecipitation(minutely, center);
        double recentPrecipitation = weightedRecentPrecipitation(minutely, center);
        double cloudBuild = futureCloudBuild(minutely, current, center);
        double visibilityDrop = futureVisibilityDrop(minutely, current, center);

        double approach = clamp01(Math.max(
                futurePrecipitation,
                cloudBuild * 0.65d
        ));

        // A visibility trend is only allowed to influence the horizon when another provider
        // signal supports an approaching weather band. It can never create current fog truth.
        double supportedVisibilityVeil = (futurePrecipitation > 0.12d || cloudBuild > 0.20d)
                ? visibilityDrop * 0.55d
                : 0d;
        double horizonVeil = clamp01(Math.max(
                futurePrecipitation * 0.65d,
                Math.max(cloudBuild * 0.35d, supportedVisibilityVeil)
        ));

        return new State(approach, recentPrecipitation, horizonVeil);
    }

    private static double weightedFuturePrecipitation(
            @NonNull WeatherResponse.Minutely15Weather minutely,
            int center
    ) {
        double envelope = 0d;
        double[] weights = {1d, 0.72d, 0.48d};
        for (int offset = 1; offset <= 3; offset++) {
            int index = center + offset;
            Integer code = valueAt(minutely.getWeatherCode(), index);
            if (SevereWeatherVisualPolicy.isThunderstorm(code)) continue;
            envelope = Math.max(
                    envelope,
                    precipitationSignalAt(minutely, index, code) * weights[offset - 1]
            );
        }
        return clamp01(envelope);
    }

    private static double weightedRecentPrecipitation(
            @NonNull WeatherResponse.Minutely15Weather minutely,
            int center
    ) {
        double envelope = 0d;
        double[] weights = {0.70d, 0.40d};
        for (int offset = 1; offset <= 2; offset++) {
            int index = center - offset;
            Integer code = valueAt(minutely.getWeatherCode(), index);
            if (SevereWeatherVisualPolicy.isThunderstorm(code)) continue;
            envelope = Math.max(
                    envelope,
                    precipitationSignalAt(minutely, index, code) * weights[offset - 1]
            );
        }
        return clamp01(envelope);
    }

    private static double precipitationSignalAt(
            @NonNull WeatherResponse.Minutely15Weather minutely,
            int index,
            @Nullable Integer code
    ) {
        double liquid = maxNonNegative(
                valueAt(minutely.getPrecipitation(), index),
                valueAt(minutely.getRain(), index),
                valueAt(minutely.getShowers(), index)
        );
        Double snowValue = valueAt(minutely.getSnowfall(), index);
        double snow = snowValue == null ? 0d : Math.max(0d, snowValue);

        double signal = Math.max(
                smoothstep(0.03d, 1.20d, liquid),
                smoothstep(0.02d, 0.55d, snow)
        );
        if (isNonStormPrecipitationCode(code)) {
            signal = Math.max(signal, 0.20d);
        }
        return clamp01(signal);
    }

    private static double futureCloudBuild(
            @NonNull WeatherResponse.Minutely15Weather minutely,
            @Nullable WeatherResponse.CurrentWeather current,
            int center
    ) {
        Double baselineValue = current == null ? null : current.getCloudCover();
        if (baselineValue == null) baselineValue = valueAt(minutely.getCloudCover(), center);
        if (baselineValue == null) return 0d;

        double baseline = clamp(baselineValue, 0d, 100d);
        double envelope = 0d;
        double[] weights = {1d, 0.72d, 0.48d};
        for (int offset = 1; offset <= 3; offset++) {
            Double future = valueAt(minutely.getCloudCover(), center + offset);
            if (future == null) continue;
            double increase = clamp(future, 0d, 100d) - baseline;
            if (increase <= 0d) continue;
            envelope = Math.max(
                    envelope,
                    smoothstep(10d, 55d, increase) * weights[offset - 1]
            );
        }
        return clamp01(envelope);
    }

    private static double futureVisibilityDrop(
            @NonNull WeatherResponse.Minutely15Weather minutely,
            @Nullable WeatherResponse.CurrentWeather current,
            int center
    ) {
        Double baselineValue = current == null ? null : current.getVisibility();
        if (baselineValue == null) baselineValue = valueAt(minutely.getVisibility(), center);
        if (baselineValue == null || baselineValue <= 0d) return 0d;

        double baseline = Math.max(1_000d, baselineValue);
        double envelope = 0d;
        double[] weights = {1d, 0.72d, 0.48d};
        for (int offset = 1; offset <= 3; offset++) {
            Double futureValue = valueAt(minutely.getVisibility(), center + offset);
            if (futureValue == null) continue;
            double future = Math.max(0d, futureValue);
            double dropFraction = clamp((baseline - future) / baseline, 0d, 1d);
            envelope = Math.max(
                    envelope,
                    smoothstep(0.15d, 0.70d, dropFraction) * weights[offset - 1]
            );
        }
        return clamp01(envelope);
    }

    private static boolean isCurrentPrecipitating(@Nullable WeatherResponse.CurrentWeather current) {
        if (current == null) return false;
        if (isNonStormPrecipitationCode(current.getWeatherCode())
                || SevereWeatherVisualPolicy.isThunderstorm(current.getWeatherCode())) {
            return true;
        }
        return positive(current.getPrecipitation())
                || positive(current.getRain())
                || positive(current.getShowers())
                || positive(current.getSnowfall());
    }

    private static boolean isNonStormPrecipitationCode(@Nullable Integer code) {
        if (code == null) return false;
        int value = code;
        return (value >= 51 && value <= 57)
                || (value >= 61 && value <= 67)
                || (value >= 71 && value <= 77)
                || (value >= 80 && value <= 86);
    }

    private static int seriesSize(@NonNull WeatherResponse.Minutely15Weather minutely) {
        int size = sizeOf(minutely.getTime());
        size = Math.max(size, sizeOf(minutely.getPrecipitation()));
        size = Math.max(size, sizeOf(minutely.getRain()));
        size = Math.max(size, sizeOf(minutely.getShowers()));
        size = Math.max(size, sizeOf(minutely.getSnowfall()));
        size = Math.max(size, sizeOf(minutely.getWeatherCode()));
        size = Math.max(size, sizeOf(minutely.getCloudCover()));
        size = Math.max(size, sizeOf(minutely.getVisibility()));
        return size;
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
            long bestDistance = Long.MAX_VALUE;
            int bestIndex = fallback;
            for (int index = 0; index < times.size(); index++) {
                String value = times.get(index);
                if (value == null) continue;
                try {
                    long distance = Math.abs(
                            Duration.between(target, LocalDateTime.parse(value)).getSeconds()
                    );
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

    private static boolean positive(@Nullable Double value) {
        return value != null && value > 0.01d;
    }

    private static int sizeOf(@Nullable List<?> values) {
        return values == null ? 0 : values.size();
    }

    @Nullable
    private static <T> T valueAt(@Nullable List<T> values, int index) {
        return values == null || index < 0 || index >= values.size() ? null : values.get(index);
    }

    private static double maxNonNegative(@Nullable Double a, @Nullable Double b, @Nullable Double c) {
        return Math.max(
                a == null ? 0d : Math.max(0d, a),
                Math.max(
                        b == null ? 0d : Math.max(0d, b),
                        c == null ? 0d : Math.max(0d, c)
                )
        );
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        if (edge1 <= edge0) return value >= edge1 ? 1d : 0d;
        double t = clamp((value - edge0) / (edge1 - edge0), 0d, 1d);
        return t * t * (3d - 2d * t);
    }

    private static double clamp01(double value) {
        return clamp(value, 0d, 1d);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class State {
        private final double approachEnvelope;
        private final double recentExitEnvelope;
        private final double horizonVeil;

        State(double approachEnvelope, double recentExitEnvelope, double horizonVeil) {
            this.approachEnvelope = clamp01(approachEnvelope);
            this.recentExitEnvelope = clamp01(recentExitEnvelope);
            this.horizonVeil = clamp01(horizonVeil);
        }

        public double getApproachEnvelope() {
            return approachEnvelope;
        }

        public double getRecentExitEnvelope() {
            return recentExitEnvelope;
        }

        public double getHorizonVeil() {
            return horizonVeil;
        }
    }
}
