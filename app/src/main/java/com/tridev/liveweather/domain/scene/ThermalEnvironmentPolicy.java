package com.tridev.liveweather.domain.scene;

import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;

/**
 * Stage 9 renderer-facing thermal/microclimate policy.
 *
 * The result is a bounded -1..1 material signal only. It never changes the
 * resolved weather condition, temperature text, alert state or precipitation.
 * Measured temperature anchors the signal; apparent temperature, humidity and
 * dew point refine how warm/cold the outdoor scene should feel.
 */
public final class ThermalEnvironmentPolicy {

    private ThermalEnvironmentPolicy() {
    }

    public static float resolve(@Nullable WeatherResponse.CurrentWeather current) {
        if (current == null) return 0f;
        return (float) resolve(
                current.getTemperature2m(),
                current.getApparentTemperature(),
                current.getRelativeHumidity2m(),
                current.getDewPoint2m()
        );
    }

    static double resolve(
            @Nullable Double measuredTemperatureC,
            @Nullable Double apparentTemperatureC,
            @Nullable Double relativeHumidityPercent,
            @Nullable Double dewPointC
    ) {
        if (measuredTemperatureC == null && apparentTemperatureC == null) {
            return 0d;
        }

        double measured = measuredTemperatureC != null
                ? measuredTemperatureC
                : apparentTemperatureC;
        double apparent = apparentTemperatureC != null
                ? apparentTemperatureC
                : measured;

        // Keep observed temperature authoritative while allowing feels-like data to
        // influence material response without letting one derived value dominate.
        double perceived = measured * 0.34d + apparent * 0.66d;
        double warm = smooth01((perceived - 24d) / 18d);
        double cold = smooth01((15d - perceived) / 22d);

        double humidity = relativeHumidityPercent == null
                ? 0.50d
                : clamp(relativeHumidityPercent / 100d, 0d, 1d);
        double dewPoint = dewPointC == null ? 10d : dewPointC;

        double humidHeat = warm
                * smooth01((humidity - 0.62d) / 0.32d)
                * smooth01((dewPoint - 17d) / 10d);
        double dryHeat = warm * smooth01((0.38d - humidity) / 0.28d);

        double apparentDelta = apparent - measured;
        double heatDelta = smooth01(apparentDelta / 7d) * warm;
        double coldDelta = smooth01((-apparentDelta) / 9d) * cold;

        double warmSignal = warm + humidHeat * 0.24d + dryHeat * 0.08d + heatDelta * 0.16d;
        double coldSignal = cold + coldDelta * 0.20d;

        return clamp(warmSignal - coldSignal, -1d, 1d);
    }

    private static double smooth01(double value) {
        double x = clamp(value, 0d, 1d);
        return x * x * (3d - 2d * x);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
