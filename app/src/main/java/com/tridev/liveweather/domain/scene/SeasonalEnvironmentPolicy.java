package com.tridev.liveweather.domain.scene;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Stage 10 hemisphere-aware seasonal material context.
 *
 * Calendar season is presentation context only. It never creates snow, rain,
 * fog, alerts or a weather condition. Tropical latitudes are intentionally
 * attenuated because four-season calendar assumptions are weak near the equator.
 */
public final class SeasonalEnvironmentPolicy {

    private static final double YEAR_DAYS = 365.2425d;
    private static final int NORTHERN_WARM_PEAK_DAY = 203;
    private static final float SEASONAL_MATERIAL_WEIGHT = 0.14f;

    private SeasonalEnvironmentPolicy() {
    }

    /**
     * Applies a small seasonal material context to the live thermal signal.
     * Live measured/apparent weather always dominates and the result remains -1..1.
     */
    public static float applyToThermal(
            float liveThermalBias,
            double latitudeDegrees,
            long epochMillis
    ) {
        float live = clamp(liveThermalBias, -1f, 1f);
        float seasonal = resolveSeasonalBias(latitudeDegrees, epochMillis);
        float liveDominance = 1f - Math.abs(live) * 0.50f;
        float combined = live + seasonal * SEASONAL_MATERIAL_WEIGHT * liveDominance;
        return clamp(combined, -1f, 1f);
    }

    /** Returns hemisphere-aware seasonal phase in -1..1 for the supplied instant. */
    public static float resolveSeasonalBias(double latitudeDegrees, long epochMillis) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(epochMillis);
        return resolveSeasonalBias(latitudeDegrees, calendar.get(Calendar.DAY_OF_YEAR));
    }

    /** Pure deterministic overload for regression tests. */
    public static float resolveSeasonalBias(double latitudeDegrees, int dayOfYear) {
        double latitude = clamp(latitudeDegrees, -90d, 90d);
        double absLatitude = Math.abs(latitude);

        // Four-season material cues are intentionally near-zero in the tropics and
        // reach full strength only in temperate/high latitudes.
        double latitudeWeight = smooth01((absLatitude - 12d) / 33d);
        if (latitudeWeight <= 0d) return 0f;

        int day = Math.max(1, Math.min(366, dayOfYear));
        double angle = ((day - NORTHERN_WARM_PEAK_DAY) / YEAR_DAYS) * Math.PI * 2d;
        double northernPhase = Math.cos(angle);
        double hemispherePhase = latitude < 0d ? -northernPhase : northernPhase;
        return (float) clamp(hemispherePhase * latitudeWeight, -1d, 1d);
    }

    private static double smooth01(double value) {
        double x = clamp(value, 0d, 1d);
        return x * x * (3d - 2d * x);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
