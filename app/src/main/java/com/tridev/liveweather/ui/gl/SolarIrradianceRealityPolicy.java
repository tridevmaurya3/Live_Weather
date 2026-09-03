package com.tridev.liveweather.ui.gl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;

/**
 * Stage 13 renderer-facing solar irradiance calibration.
 *
 * Open-Meteo current solar values are model observations/means in W/m². This policy converts
 * them into bounded perceptual factors without changing weather codes, clouds or astronomy.
 * Missing values remain neutral so older cached payloads keep the Stage 12 behavior.
 */
public final class SolarIrradianceRealityPolicy {

    private static final double REFERENCE_DNI_W_M2 = 900d;

    private SolarIrradianceRealityPolicy() {
    }

    @NonNull
    public static State resolve(
            @Nullable WeatherResponse.CurrentWeather current,
            double sunAltitudeDegrees
    ) {
        if (current == null) return State.neutral();

        Double shortwave = sanitize(current.getShortwaveRadiation());
        Double directHorizontal = sanitize(current.getDirectRadiation());
        Double diffuse = sanitize(current.getDiffuseRadiation());
        Double directNormal = sanitize(current.getDirectNormalIrradiance());

        boolean hasObservation = shortwave != null
                || directHorizontal != null
                || diffuse != null
                || directNormal != null;
        if (!hasObservation) return State.neutral();

        if (sunAltitudeDegrees <= -1d) {
            return new State(true, 0.62d, 0d, 0d, 1d);
        }

        double sinAltitude = Math.max(0d, Math.sin(Math.toRadians(sunAltitudeDegrees)));
        double expectedHorizontal = 1000d * Math.pow(Math.max(0.035d, sinAltitude), 0.72d);

        double globalObserved = shortwave != null
                ? shortwave
                : sumIfPresent(directHorizontal, diffuse);
        double globalRatio = globalObserved >= 0d
                ? clamp(globalObserved / Math.max(45d, expectedHorizontal), 0d, 1.15d)
                : 1d;

        double directRatio;
        if (directNormal != null) {
            directRatio = clamp(directNormal / REFERENCE_DNI_W_M2, 0d, 1.08d);
        } else if (directHorizontal != null) {
            directRatio = clamp(
                    directHorizontal / Math.max(35d, expectedHorizontal),
                    0d,
                    1.08d
            );
        } else {
            directRatio = globalRatio;
        }

        double diffuseFraction;
        if (diffuse != null && directHorizontal != null && diffuse + directHorizontal > 1d) {
            diffuseFraction = clamp(diffuse / (diffuse + directHorizontal), 0d, 1d);
        } else if (diffuse != null && globalObserved > 1d) {
            diffuseFraction = clamp(diffuse / globalObserved, 0d, 1d);
        } else {
            diffuseFraction = clamp(1d - directRatio * 0.82d, 0d, 1d);
        }

        // Keep diffuse daylight useful while allowing genuinely weak irradiance to read darker.
        double globalLightFactor = clamp(
                0.62d + globalRatio * 0.38d + diffuseFraction * 0.025d,
                0.62d,
                1.04d
        );

        // Direct beam controls visible solar disc/specular response. Square-root compression keeps
        // partially obscured Sun gradual instead of making it abruptly disappear.
        double directionalVisibilityFactor = clamp(
                0.06d + 0.94d * Math.sqrt(clamp(directRatio, 0d, 1d)),
                0.06d,
                1d
        );

        return new State(
                true,
                globalLightFactor,
                clamp(directRatio, 0d, 1d),
                directionalVisibilityFactor,
                diffuseFraction
        );
    }

    @Nullable
    private static Double sanitize(@Nullable Double value) {
        if (value == null || !Double.isFinite(value) || value < 0d) return null;
        return value;
    }

    private static double sumIfPresent(@Nullable Double a, @Nullable Double b) {
        if (a == null && b == null) return -1d;
        return (a == null ? 0d : a) + (b == null ? 0d : b);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class State {
        private final boolean hasObservation;
        private final double globalLightFactor;
        private final double directLightFactor;
        private final double directionalVisibilityFactor;
        private final double diffuseFraction;

        private State(
                boolean hasObservation,
                double globalLightFactor,
                double directLightFactor,
                double directionalVisibilityFactor,
                double diffuseFraction
        ) {
            this.hasObservation = hasObservation;
            this.globalLightFactor = globalLightFactor;
            this.directLightFactor = directLightFactor;
            this.directionalVisibilityFactor = directionalVisibilityFactor;
            this.diffuseFraction = diffuseFraction;
        }

        @NonNull
        static State neutral() {
            return new State(false, 1d, 1d, 1d, 0.5d);
        }

        public boolean hasObservation() { return hasObservation; }
        public double getGlobalLightFactor() { return globalLightFactor; }
        public double getDirectLightFactor() { return directLightFactor; }
        public double getDirectionalVisibilityFactor() { return directionalVisibilityFactor; }
        public double getDiffuseFraction() { return diffuseFraction; }
    }
}
