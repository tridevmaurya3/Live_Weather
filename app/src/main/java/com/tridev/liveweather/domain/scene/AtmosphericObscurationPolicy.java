package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;

/**
 * Separates meteorological fog/mist from aerosol haze.
 *
 * AQI haze remains an independent scene channel. Dense fog requires either the
 * current WMO fog code or moisture evidence plus genuinely reduced visibility;
 * poor air quality alone can no longer create a white fog layer.
 */
public final class AtmosphericObscurationPolicy {

    private AtmosphericObscurationPolicy() {
    }

    @NonNull
    public static State resolve(
            @Nullable WeatherResponse.CurrentWeather current,
            int weatherCode,
            double airHazeIntensity,
            double visibilityFactor
    ) {
        double haze = clamp01(airHazeIntensity);
        double visibilityLoss = clamp01(1d - visibilityFactor);
        boolean explicitFog = weatherCode == 45 || weatherCode == 48;

        double humidity = current == null || current.getRelativeHumidity2m() == null
                ? 0d
                : clamp(current.getRelativeHumidity2m() / 100d, 0d, 1d);
        double dewEvidence = 0d;
        if (current != null && current.getTemperature2m() != null && current.getDewPoint2m() != null) {
            double spread = Math.max(0d, current.getTemperature2m() - current.getDewPoint2m());
            dewEvidence = 1d - clamp(spread / 4d, 0d, 1d);
        }
        double moistureEvidence = clamp01(
                Math.max((humidity - 0.82d) / 0.18d, dewEvidence * humidity)
        );

        double observedVisibilityLoss = 0d;
        if (current != null && current.getVisibility() != null) {
            observedVisibilityLoss = 1d - clamp(current.getVisibility() / 10_000d, 0d, 1d);
        }
        double meteorologicalLoss = Math.max(observedVisibilityLoss, visibilityLoss * 0.72d);

        double fog;
        if (explicitFog) {
            fog = clamp(0.58d + meteorologicalLoss * 0.34d + moistureEvidence * 0.16d, 0.52d, 1d);
        } else {
            // Haze dominance suppresses accidental fog classification unless moisture is strong.
            double aerosolSuppression = 1d - haze * (1d - moistureEvidence) * 0.72d;
            fog = clamp(
                    moistureEvidence * meteorologicalLoss * 0.88d * aerosolSuppression,
                    0d,
                    0.72d
            );
            if (moistureEvidence < 0.38d || meteorologicalLoss < 0.18d) {
                fog = 0d;
            }
        }

        double mist = explicitFog
                ? clamp01(Math.max(0d, fog - 0.34d))
                : clamp01(moistureEvidence * meteorologicalLoss * 0.62d);

        return new State(fog, mist, haze, moistureEvidence);
    }

    public static final class State {
        private final double fogIntensity;
        private final double mistIntensity;
        private final double hazeIntensity;
        private final double moistureEvidence;

        private State(double fogIntensity, double mistIntensity, double hazeIntensity, double moistureEvidence) {
            this.fogIntensity = clamp01(fogIntensity);
            this.mistIntensity = clamp01(mistIntensity);
            this.hazeIntensity = clamp01(hazeIntensity);
            this.moistureEvidence = clamp01(moistureEvidence);
        }

        public double getFogIntensity() { return fogIntensity; }
        public double getMistIntensity() { return mistIntensity; }
        public double getHazeIntensity() { return hazeIntensity; }
        public double getMoistureEvidence() { return moistureEvidence; }
    }

    private static double clamp01(double value) {
        return clamp(value, 0d, 1d);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
