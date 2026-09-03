package com.tridev.liveweather.core.location;

/** Pure rules for accepting a device fix without keeping GPS continuously active. */
public final class LocationContinuityPolicy {

    public static final long FOREGROUND_RECHECK_MILLIS = 5 * 60 * 1000L;
    public static final long SNAPSHOT_STALE_MILLIS = 15 * 60 * 1000L;
    public static final float MIN_MOVEMENT_METERS = 750f;
    public static final float MAX_USEFUL_ACCURACY_METERS = 5_000f;

    private LocationContinuityPolicy() {}

    public static boolean isStale(long capturedAt, long now) {
        return capturedAt <= 0L || now < capturedAt || now - capturedAt >= SNAPSHOT_STALE_MILLIS;
    }

    public static boolean shouldRecheck(long lastCheckAt, long now) {
        return lastCheckAt <= 0L || now < lastCheckAt
                || now - lastCheckAt >= FOREGROUND_RECHECK_MILLIS;
    }

    public static boolean isUsable(double latitude, double longitude, float accuracyMeters) {
        return Double.isFinite(latitude) && latitude >= -90d && latitude <= 90d
                && Double.isFinite(longitude) && longitude >= -180d && longitude <= 180d
                && (Float.isNaN(accuracyMeters)
                || (accuracyMeters > 0f && accuracyMeters <= MAX_USEFUL_ACCURACY_METERS));
    }

    public static boolean shouldActivate(
            double oldLatitude,
            double oldLongitude,
            long oldCapturedAt,
            double newLatitude,
            double newLongitude,
            float newAccuracyMeters,
            long now
    ) {
        if (!isUsable(newLatitude, newLongitude, newAccuracyMeters)) return false;
        if (!Double.isFinite(oldLatitude) || !Double.isFinite(oldLongitude)) return true;
        return isStale(oldCapturedAt, now)
                || distanceMeters(oldLatitude, oldLongitude, newLatitude, newLongitude)
                >= MIN_MOVEMENT_METERS;
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double latDelta = Math.toRadians(lat2 - lat1);
        double lonDelta = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDelta / 2d) * Math.sin(latDelta / 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDelta / 2d) * Math.sin(lonDelta / 2d);
        return 6_371_000d * 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
    }
}
