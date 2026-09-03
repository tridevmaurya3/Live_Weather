package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Stores the last accepted device fix independently from a manually selected city. */
public final class LocationSnapshotStore {

    private static final String PREFS = "device_location_snapshot";
    private static final String LATITUDE = "latitude";
    private static final String LONGITUDE = "longitude";
    private static final String ACCURACY = "accuracy";
    private static final String CAPTURED_AT = "captured_at";

    private final SharedPreferences preferences;

    public LocationSnapshotStore(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(@NonNull Location location, long capturedAt) {
        preferences.edit()
                .putString(LATITUDE, Double.toString(location.getLatitude()))
                .putString(LONGITUDE, Double.toString(location.getLongitude()))
                .putFloat(ACCURACY, location.hasAccuracy() ? location.getAccuracy() : Float.NaN)
                .putLong(CAPTURED_AT, capturedAt > 0L ? capturedAt : System.currentTimeMillis())
                .apply();
    }

    @Nullable
    public Snapshot load() {
        String latitude = preferences.getString(LATITUDE, null);
        String longitude = preferences.getString(LONGITUDE, null);
        if (latitude == null || longitude == null) return null;
        try {
            return new Snapshot(
                    Double.parseDouble(latitude),
                    Double.parseDouble(longitude),
                    preferences.getFloat(ACCURACY, Float.NaN),
                    preferences.getLong(CAPTURED_AT, 0L)
            );
        } catch (NumberFormatException exception) {
            preferences.edit().clear().apply();
            return null;
        }
    }

    public static final class Snapshot {
        private final double latitude;
        private final double longitude;
        private final float accuracyMeters;
        private final long capturedAt;

        Snapshot(double latitude, double longitude, float accuracyMeters, long capturedAt) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.accuracyMeters = accuracyMeters;
            this.capturedAt = capturedAt;
        }

        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public float getAccuracyMeters() { return accuracyMeters; }
        public long getCapturedAt() { return capturedAt; }
    }
}
