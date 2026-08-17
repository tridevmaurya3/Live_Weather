package com.tridev.liveweather.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;

/**
 * R10 atmospheric-depth reality model.
 *
 * Converts observed/modelled visibility plus current humidity/dew-point saturation and
 * already-resolved AQI haze into a bounded renderer-facing transmittance value.
 * Humidity is deliberately a mild distance-softening signal only: it can strengthen
 * atmospheric depth, but it cannot independently fabricate dense fog or precipitation.
 */
public final class AtmosphericDepthReality {

    private static final double REFERENCE_VISIBILITY_METERS = 16_000d;

    private AtmosphericDepthReality() {
    }

    @NonNull
    public static DepthState resolve(
            @Nullable WeatherResponse.CurrentWeather current,
            double airHazeIntensity
    ) {
        if (current == null) {
            return resolve(null, null, null, null, airHazeIntensity);
        }
        return resolve(
                current.getVisibility(),
                current.getRelativeHumidity2m(),
                current.getTemperature2m(),
                current.getDewPoint2m(),
                airHazeIntensity
        );
    }

    @NonNull
    static DepthState resolve(
            @Nullable Double visibilityMeters,
            @Nullable Double relativeHumidityPercent,
            @Nullable Double temperatureC,
            @Nullable Double dewPointC,
            double airHazeIntensity
    ) {
        double observedVisibility = visibilityMeters == null
                ? REFERENCE_VISIBILITY_METERS
                : Math.max(0d, visibilityMeters);
        double observedFactor = clamp(
                observedVisibility / REFERENCE_VISIBILITY_METERS,
                0.08d,
                1d
        );

        double humidity = relativeHumidityPercent == null
                ? 0d
                : clamp(relativeHumidityPercent / 100d, 0d, 1d);
        double humidityExcess = clamp((humidity - 0.70d) / 0.30d, 0d, 1d);

        double dewSaturation = 0d;
        if (temperatureC != null && dewPointC != null) {
            double spread = Math.max(0d, temperatureC - dewPointC);
            dewSaturation = 1d - clamp(spread / 8d, 0d, 1d);
        }

        double moistureDepth = clamp(
                Math.max(humidityExcess * 0.72d, dewSaturation * humidity * 0.92d),
                0d,
                1d
        );
        double haze = clamp(airHazeIntensity, 0d, 1d);

        // AQI can strongly reduce long-distance clarity; humidity remains intentionally mild.
        double aerosolTransmission = 1d - haze * 0.48d;
        double moistureTransmission = 1d - moistureDepth * 0.14d;
        double visibilityFactor = clamp(
                observedFactor * aerosolTransmission * moistureTransmission,
                0.05d,
                1d
        );

        return new DepthState(
                visibilityFactor,
                observedFactor,
                moistureDepth,
                haze
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class DepthState {
        private final double visibilityFactor;
        private final double observedVisibilityFactor;
        private final double moistureDepth;
        private final double airHazeIntensity;

        private DepthState(
                double visibilityFactor,
                double observedVisibilityFactor,
                double moistureDepth,
                double airHazeIntensity
        ) {
            this.visibilityFactor = visibilityFactor;
            this.observedVisibilityFactor = observedVisibilityFactor;
            this.moistureDepth = moistureDepth;
            this.airHazeIntensity = airHazeIntensity;
        }

        public double getVisibilityFactor() {
            return visibilityFactor;
        }

        public double getObservedVisibilityFactor() {
            return observedVisibilityFactor;
        }

        public double getMoistureDepth() {
            return moistureDepth;
        }

        public double getAirHazeIntensity() {
            return airHazeIntensity;
        }
    }
}
