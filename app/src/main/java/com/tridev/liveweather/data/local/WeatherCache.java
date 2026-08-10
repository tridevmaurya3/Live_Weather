package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

/**
 * Small persistent cache for the last successful weather response.
 *
 * Phase 2 keeps a single last-known snapshot so the app can show useful
 * weather immediately after launch and when a refresh temporarily fails.
 */
public final class WeatherCache {

    private static final String PREFS_NAME = "live_weather_cache";
    private static final String KEY_WEATHER_JSON = "weather_json";
    private static final String KEY_LATITUDE = "latitude";
    private static final String KEY_LONGITUDE = "longitude";
    private static final String KEY_SAVED_AT = "saved_at";

    private final SharedPreferences preferences;
    private final Gson gson;

    public WeatherCache(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public void save(
            @NonNull WeatherResponse weather,
            double latitude,
            double longitude,
            long savedAt
    ) {
        preferences.edit()
                .putString(KEY_WEATHER_JSON, gson.toJson(weather))
                .putString(KEY_LATITUDE, Double.toString(latitude))
                .putString(KEY_LONGITUDE, Double.toString(longitude))
                .putLong(KEY_SAVED_AT, savedAt)
                .apply();
    }

    @Nullable
    public CachedWeather load() {
        String json = preferences.getString(KEY_WEATHER_JSON, null);
        String latitudeValue = preferences.getString(KEY_LATITUDE, null);
        String longitudeValue = preferences.getString(KEY_LONGITUDE, null);

        if (json == null || latitudeValue == null || longitudeValue == null) {
            return null;
        }

        try {
            WeatherResponse weather = gson.fromJson(json, WeatherResponse.class);
            double latitude = Double.parseDouble(latitudeValue);
            double longitude = Double.parseDouble(longitudeValue);
            long savedAt = preferences.getLong(KEY_SAVED_AT, 0L);

            if (weather == null) {
                return null;
            }

            return new CachedWeather(weather, latitude, longitude, savedAt);
        } catch (JsonSyntaxException | NumberFormatException exception) {
            clear();
            return null;
        }
    }

    public void clear() {
        preferences.edit()
                .remove(KEY_WEATHER_JSON)
                .remove(KEY_LATITUDE)
                .remove(KEY_LONGITUDE)
                .remove(KEY_SAVED_AT)
                .apply();
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

        @NonNull
        public WeatherResponse getWeather() {
            return weather;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public long getSavedAt() {
            return savedAt;
        }
    }
}
