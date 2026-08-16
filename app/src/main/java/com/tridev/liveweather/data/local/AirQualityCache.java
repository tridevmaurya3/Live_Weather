package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.tridev.liveweather.data.remote.dto.AirQualityResponse;

import java.util.Locale;

public final class AirQualityCache {

    private static final String PREFS_NAME = "live_weather_air_quality_cache";
    private static final String KEY_LAST_LOCATION = "last_location_key";
    private static final String PREFIX_DATA = "aq_";
    private static final String PREFIX_LAT = "lat_";
    private static final String PREFIX_LON = "lon_";
    private static final String PREFIX_TIME = "saved_";

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    public AirQualityCache(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void save(
            @NonNull AirQualityResponse response,
            double latitude,
            double longitude,
            long savedAt
    ) {
        saveInternal(response, latitude, longitude, savedAt, true);
    }

    /** Save a location snapshot without moving the generic last-location pointer. */
    public void saveSnapshot(
            @NonNull AirQualityResponse response,
            double latitude,
            double longitude,
            long savedAt
    ) {
        saveInternal(response, latitude, longitude, savedAt, false);
    }

    private void saveInternal(
            @NonNull AirQualityResponse response,
            double latitude,
            double longitude,
            long savedAt,
            boolean makeActive
    ) {
        String key = key(latitude, longitude);
        long safeSavedAt = savedAt > 0L ? savedAt : System.currentTimeMillis();
        SharedPreferences.Editor editor = preferences.edit()
                .putString(PREFIX_DATA + key, gson.toJson(response))
                .putString(PREFIX_LAT + key, Double.toString(latitude))
                .putString(PREFIX_LON + key, Double.toString(longitude))
                .putLong(PREFIX_TIME + key, safeSavedAt);
        if (makeActive) editor.putString(KEY_LAST_LOCATION, key);
        editor.apply();
    }

    @Nullable
    public CachedAirQuality load() {
        String key = preferences.getString(KEY_LAST_LOCATION, null);
        return key == null ? null : loadByKey(key);
    }

    @Nullable
    public CachedAirQuality load(double latitude, double longitude) {
        return loadByKey(key(latitude, longitude));
    }

    @Nullable
    private CachedAirQuality loadByKey(@NonNull String key) {
        String json = preferences.getString(PREFIX_DATA + key, null);
        String lat = preferences.getString(PREFIX_LAT + key, null);
        String lon = preferences.getString(PREFIX_LON + key, null);
        if (json == null || lat == null || lon == null) {
            return null;
        }
        try {
            AirQualityResponse response = gson.fromJson(json, AirQualityResponse.class);
            if (response == null) {
                clearLocationKey(key);
                return null;
            }
            return new CachedAirQuality(
                    response,
                    Double.parseDouble(lat),
                    Double.parseDouble(lon),
                    preferences.getLong(PREFIX_TIME + key, 0L)
            );
        } catch (JsonSyntaxException | NumberFormatException exception) {
            clearLocationKey(key);
            return null;
        }
    }

    private void clearLocationKey(@NonNull String key) {
        SharedPreferences.Editor editor = preferences.edit()
                .remove(PREFIX_DATA + key)
                .remove(PREFIX_LAT + key)
                .remove(PREFIX_LON + key)
                .remove(PREFIX_TIME + key);
        String active = preferences.getString(KEY_LAST_LOCATION, null);
        if (key.equals(active)) editor.remove(KEY_LAST_LOCATION);
        editor.apply();
    }

    @NonNull
    private String key(double latitude, double longitude) {
        return String.format(Locale.US, "%.3f_%.3f", latitude, longitude);
    }

    public static final class CachedAirQuality {
        private final AirQualityResponse response;
        private final double latitude;
        private final double longitude;
        private final long savedAt;

        CachedAirQuality(AirQualityResponse response, double latitude, double longitude, long savedAt) {
            this.response = response;
            this.latitude = latitude;
            this.longitude = longitude;
            this.savedAt = savedAt;
        }

        public AirQualityResponse getResponse() { return response; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public long getSavedAt() { return savedAt; }
    }
}
