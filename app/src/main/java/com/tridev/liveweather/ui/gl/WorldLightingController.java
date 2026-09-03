package com.tridev.liveweather.ui.gl;

/**
 * R11 Sun/Cloud World Lighting, extended in Stage 13 with real solar irradiance calibration.
 *
 * Produces a bounded renderer-facing world-light signal from already resolved weather truth.
 * It never invents clouds, rain or storms. Broken-cloud shadow variation is intentionally slow
 * and subtle so the app Hero and Live Wallpaper feel illuminated by the same moving sky without
 * making clear weather pulse or turning humid/overcast scenes unrealistically dark.
 */
public final class WorldLightingController {

    private WorldLightingController() {
    }

    /** Stage 11 compatibility path. Missing solar observations remain exactly neutral. */
    public static double resolveSceneLight(
            double baseSceneLight,
            double sunAltitudeDegrees,
            double sunVisibility,
            double cloudCover,
            double cloudDensity,
            double cloudMidLayer,
            double cloudNearLayer,
            double cloudStormCeiling,
            double cloudBrightness,
            double stormIntensity,
            double fogIntensity,
            double airHazeIntensity,
            double windStrength,
            double windDirectionRadians,
            long epochMillis
    ) {
        return resolveSceneLight(
                baseSceneLight,
                sunAltitudeDegrees,
                sunVisibility,
                cloudCover,
                cloudDensity,
                cloudMidLayer,
                cloudNearLayer,
                cloudStormCeiling,
                cloudBrightness,
                stormIntensity,
                fogIntensity,
                airHazeIntensity,
                1d,
                1d,
                0.5d,
                false,
                windStrength,
                windDirectionRadians,
                epochMillis
        );
    }

    /**
     * Stage 13 solar-aware path. Solar inputs are renderer calibration only; they never change
     * resolved weather truth. Values are bounded so provider/model differences cannot flash the UI.
     */
    public static double resolveSceneLight(
            double baseSceneLight,
            double sunAltitudeDegrees,
            double sunVisibility,
            double cloudCover,
            double cloudDensity,
            double cloudMidLayer,
            double cloudNearLayer,
            double cloudStormCeiling,
            double cloudBrightness,
            double stormIntensity,
            double fogIntensity,
            double airHazeIntensity,
            double solarGlobalLightFactor,
            double solarDirectLightFactor,
            double solarDiffuseFraction,
            boolean hasSolarObservation,
            double windStrength,
            double windDirectionRadians,
            long epochMillis
    ) {
        double base = clamp(baseSceneLight, 0.01d, 1d);
        double daylight = smoothstep(-6d, 18d, sunAltitudeDegrees);
        if (daylight <= 0.01d) return base;

        double cover = clamp01(cloudCover);
        double density = clamp01(cloudDensity);
        double mid = clamp01(cloudMidLayer);
        double near = clamp01(cloudNearLayer);
        double ceiling = clamp01(cloudStormCeiling);
        double brightness = clamp01(cloudBrightness);
        double storm = clamp01(stormIntensity);
        double fog = clamp01(fogIntensity);
        double haze = clamp01(airHazeIntensity);
        double sun = clamp01(sunVisibility);
        double wind = clamp01(windStrength);

        double solarGlobal = hasSolarObservation
                ? clamp(solarGlobalLightFactor, 0.58d, 1.06d)
                : 1d;
        double solarDirect = hasSolarObservation
                ? clamp(solarDirectLightFactor, 0d, 1d)
                : 1d;
        double diffuseFraction = hasSolarObservation
                ? clamp01(solarDiffuseFraction)
                : 0.5d;

        double cloudMass = clamp01(
                cover * 0.42d
                        + density * 0.28d
                        + mid * 0.10d
                        + near * 0.12d
                        + ceiling * 0.20d
        );
        double brightCloudRecovery = brightness * cover * 0.075d;
        double overcastLoss = daylight * (
                cloudMass * 0.18d
                        + storm * 0.18d
                        + fog * 0.075d
                        + haze * 0.045d
        );
        overcastLoss = clamp(overcastLoss - brightCloudRecovery, 0d, 0.38d);

        double brokenCloud = brokenCloudSignal(cover);
        double shadowEligibility = brokenCloud
                * smoothstep(0.14d, 0.72d, density)
                * sun
                * daylight
                * (1d - fog * 0.78d)
                * (1d - haze * 0.45d)
                * (1d - storm * 0.70d);
        if (hasSolarObservation) {
            // Weak direct beam cannot cast a strong moving cloud shadow even when cloud geometry
            // looks broken. Bright direct sun keeps the original Stage 11 response.
            shadowEligibility *= 0.32d + solarDirect * 0.68d;
        }

        double seconds = Math.max(0d, epochMillis / 1000d);
        double speed = 0.010d + wind * 0.030d;
        double directionPhase = normalizeRadians(windDirectionRadians) * 0.62d;
        double layerPhase = mid * 1.35d + near * 2.10d + density * 0.75d;
        double waveA = 0.5d + 0.5d * Math.sin(seconds * speed + directionPhase + layerPhase);
        double waveB = 0.5d + 0.5d * Math.sin(seconds * speed * 0.57d + 1.70d - layerPhase * 0.43d);
        double passingShade = clamp01(waveA * 0.68d + waveB * 0.32d);

        double shadowDepth = shadowEligibility
                * (0.018d + density * 0.055d + near * 0.025d)
                * passingShade;
        shadowDepth = clamp(shadowDepth, 0d, 0.10d);

        double sunLift = daylight
                * sun
                * (1d - cover)
                * (1d - fog * 0.65d)
                * (1d - haze * 0.35d)
                * 0.025d;
        if (hasSolarObservation) sunLift *= solarDirect;

        double diffuseRecovery = hasSolarObservation
                ? daylight * diffuseFraction * (1d - solarDirect) * brightness * 0.022d
                : 0d;

        double factor = clamp(
                1d - overcastLoss - shadowDepth + sunLift + diffuseRecovery,
                0.58d,
                1.04d
        );

        if (hasSolarObservation) {
            // Irradiance is a calibration, not a replacement for the existing atmosphere model.
            // This bounded blend is strong enough to distinguish bright vs dark overcast while
            // preventing model updates from causing large exposure jumps.
            double irradianceCalibration = clamp(0.78d + solarGlobal * 0.22d, 0.88d, 1.02d);
            factor *= irradianceCalibration;
        }

        return clamp(base * factor, 0.01d, 1d);
    }

    static double brokenCloudSignal(double cloudCover) {
        double cover = clamp01(cloudCover);
        return smoothstep(0.16d, 0.42d, cover)
                * (1d - smoothstep(0.72d, 0.94d, cover));
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        if (edge1 <= edge0) return value >= edge1 ? 1d : 0d;
        double t = clamp((value - edge0) / (edge1 - edge0), 0d, 1d);
        return t * t * (3d - 2d * t);
    }

    private static double normalizeRadians(double radians) {
        double tau = Math.PI * 2d;
        double value = radians % tau;
        return value < 0d ? value + tau : value;
    }

    private static double clamp01(double value) {
        return clamp(value, 0d, 1d);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
