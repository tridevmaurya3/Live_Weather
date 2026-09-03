package com.tridev.liveweather.ui.gl;

/** Continuous astronomy/weather lighting curves shared by app and wallpaper GL scenes. */
public final class AtmosphereLightPolicy {

    private AtmosphereLightPolicy() {}

    public static double twilightWarmth(double sunAltitudeDegrees, double cloudCover) {
        double rise = smoothstep(-12d, -1d, sunAltitudeDegrees);
        double fall = 1d - smoothstep(1d, 12d, sunAltitudeDegrees);
        return clamp01(rise * fall * (1d - clamp01(cloudCover) * 0.58d));
    }

    public static double daylightExposure(
            double sunAltitudeDegrees,
            double cloudCover,
            double fog,
            double haze,
            double storm
    ) {
        double solar = smoothstep(-18d, 14d, sunAltitudeDegrees);
        double obstruction = clamp01(cloudCover) * 0.18d
                + clamp01(fog) * 0.16d
                + clamp01(haze) * 0.08d
                + clamp01(storm) * 0.22d;
        double nightFloor = 0.34d + solar * 0.18d;
        return clamp(0.54d + solar * 0.46d - obstruction, nightFloor, 1d);
    }

    public static double starGate(double sunAltitudeDegrees) {
        return 1d - smoothstep(-18d, -6d, sunAltitudeDegrees);
    }

    public static double horizonDepth(double fog, double haze, double visibilityFactor) {
        return clamp01(clamp01(fog) * 0.72d
                + clamp01(haze) * 0.48d
                + (1d - clamp01(visibilityFactor)) * 0.34d);
    }

    public static double lightningEnvironmentLift(
            double storm,
            double flash,
            double cloudDensity,
            double fog
    ) {
        return clamp(clamp01(storm) * clamp01(flash)
                * (0.10d + clamp01(cloudDensity) * 0.09d + clamp01(fog) * 0.05d), 0d, 0.24d);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp((value - edge0) / (edge1 - edge0), 0d, 1d);
        return t * t * (3d - 2d * t);
    }

    private static double clamp01(double value) { return clamp(value, 0d, 1d); }
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
