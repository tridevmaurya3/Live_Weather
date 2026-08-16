package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import java.util.Locale;

/**
 * Persistent weather cache with per-location snapshots.
 *
 * Active app weather updates the last-location pointer. Background consumers
 * such as fixed-city widgets may refresh another location without changing the
 * app's currently active weather identity.
 */
public final class WeatherCache {

    private static final String PREFS_NAME = "live_weather_cache";

    private static final String KEY_LAST_LOCATION = "last_location_key";
    private static final String PREFIX_WEATHER = "weather_";
    private static final String PREFIX_LATITUDE = "latitude_";
    private static final String PREFIX_LONGITUDE = "longitude_";
    private static final String PREFIX_SAVED_AT = "saved_at_";

    private static final String LEGACY_WEATHER_JSON = "weather_json";
    private static final String LEGACY_LATITUDE = "latitude";
    private static final String LEGACY_LONGITUDE = "longitude";
    private static final String LEGACY_SAVED_AT = "saved_at";

    private final SharedPreferences preferences;
    private final Gson gson;

    public WeatherCache(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        migrateLegacyCacheIfNeeded();
    }

    /** Save weather and make this location the active app/wallpaper location. */
    public void save(
            @NonNull WeatherResponse weather,
            double latitude,
            double longitude,
            long savedAt
    ) {
        saveInternal(weather, latitude, longitude, savedAt, true);
    }

    /**
     * Save a per-location snapshot without moving the active-location pointer.
     * Used by fixed-city widgets and other background location consumers.
     */
    public void saveSnapshot(
            @NonNull WeatherResponse weather,
            double latitude,
            double longitude,
            long savedAt
    ) {
        saveInternal(weather, latitude, longitude, savedAt, false);
    }

    /**
     * Background-safe write for work that started from the active cache.
     * If the user changed the active location while network work was running,
     * the response is retained only as a snapshot and cannot move the active
     * pointer back to the old location.
     *
     * @return true when the requested location was still active at write time.
     */
    public boolean saveIfStillActive(
            @NonNull WeatherResponse weather,
            double latitude,
            double longitude,
            long savedAt
    ) {
        String requestedKey = locationKey(latitude, longitude);
        String activeKey = preferences.getString(KEY_LAST_LOCATION, null);
        boolean stillActive = requestedKey.equals(activeKey);
        saveInternal(weather, latitude, longitude, savedAt, stillActive);
        return stillActive;
    }

    public boolean isActiveLocation(double latitude, double longitude) {
        String activeKey = preferences.getString(KEY_LAST_LOCATION, null);
        return activeKey != null && activeKey.equals(locationKey(latitude, longitude));
    }

    private void saveInternal(
            @NonNull WeatherResponse weather,
            double latitude,
            double longitude,
            long savedAt,
            boolean makeActive
    ) {
        String key = locationKey(latitude, longitude);
        long safeSavedAt = savedAt > 0L ? savedAt : System.currentTimeMillis();
        SharedPreferences.Editor editor = preferences.edit()
                .putString(PREFIX_WEATHER + key, gson.toJson(weather))
                .putString(PREFIX_LATITUDE + key, Double.toString(latitude))
                .putString(PREFIX_LONGITUDE + key, Double.toString(longitude))
                .putLong(PREFIX_SAVED_AT + key, safeSavedAt);
        if (makeActive) {
            editor.putString(KEY_LAST_LOCATION, key);
        }
        editor.apply();
    }

    @Nullable
    public CachedWeather load() {
        String lastKey = preferences.getString(KEY_LAST_LOCATION, null);
        if (lastKey == null) return null;
        return loadByKey(lastKey);
    }

    @Nullable
    public CachedWeather load(double latitude, double longitude) {
        return loadByKey(locationKey(latitude, longitude));
    }

    @Nullable
    private CachedWeather loadByKey(@NonNull String key) {
        String json = preferences.getString(PREFIX_WEATHER + key, null);
        String latitudeValue = preferences.getString(PREFIX_LATITUDE + key, null);
        String longitudeValue = preferences.getString(PREFIX_LONGITUDE + key, null);

        if (json == null || latitudeValue == null || longitudeValue == null) return null;

        try {
            WeatherResponse weather = gson.fromJson(json, WeatherResponse.class);
            double latitude = Double.parseDouble(latitudeValue);
            double longitude = Double.parseDouble(longitudeValue);
            long savedAt = preferences.getLong(PREFIX_SAVED_AT + key, 0L);
            if (weather == null) return null;
            return new CachedWeather(weather, latitude, longitude, savedAt);
        } catch (JsonSyntaxException | NumberFormatException exception) {
            clearLocationKey(key);
            return null;
        }
    }

    private void migrateLegacyCacheIfNeeded() {
        if (preferences.getString(KEY_LAST_LOCATION, null) != null) return;

        String json = preferences.getString(LEGACY_WEATHER_JSON, null);
        String latitudeValue = preferences.getString(LEGACY_LATITUDE, null);
        String longitudeValue = preferences.getString(LEGACY_LONGITUDE, null);
        if (json == null || latitudeValue == null || longitudeValue == null) return;

        try {
            double latitude = Double.parseDouble(latitudeValue);
            double longitude = Double.parseDouble(longitudeValue);
            String key = locationKey(latitude, longitude);
            long savedAt = preferences.getLong(LEGACY_SAVED_AT, 0L);

            preferences.edit()
                    .putString(PREFIX_WEATHER + key, json)
                    .putString(PREFIX_LATITUDE + key, latitudeValue)
                    .putString(PREFIX_LONGITUDE + key, longitudeValue)
                    .putLong(PREFIX_SAVED_AT + key, savedAt)
                    .putString(KEY_LAST_LOCATION, key)
                    .remove(LEGACY_WEATHER_JSON)
                    .remove(LEGACY_LATITUDE)
                    .remove(LEGACY_LONGITUDE)
                    .remove(LEGACY_SAVED_AT)
                    .apply();
        } catch (NumberFormatException ignored) {
        }
    }

    private void clearLocationKey(@NonNull String key) {
        SharedPreferences.Editor editor = preferences.edit()
                .remove(PREFIX_WEATHER + key)
                .remove(PREFIX_LATITUDE + key)
                .remove(PREFIX_LONGITUDE + key)
                .remove(PREFIX_SAVED_AT + key);

        String lastKey = preferences.getString(KEY_LAST_LOCATION, null);
        if (key.equals(lastKey)) editor.remove(KEY_LAST_LOCATION);
        editor.apply();
    }

    @NonNull
    private String locationKey(double latitude, double longitude) {
        return String.format(Locale.US, "%.3f_%.3f", latitude, longitude);
    }

    public static final class CachedWeather {
        private final WeatherResponse weather;
        private final double latitude;
        private final double longitude;
        private final long savedAt;

        public CachedWeather(
                @NonNull WeatherResponse weather,
                double latitude,
                double longitude,
                long savedAt
        ) {
            this.weather = weather;
            this.latitude = latitude;
            this.longitude = longitude;
            this.savedAt = savedAt;
        }

        @NonNull public WeatherResponse getWeather() { return weather; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public long getSavedAt() { return savedAt; }
    }
}
