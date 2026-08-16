package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.tridev.liveweather.data.remote.dto.RadarFieldPointResponse;
import com.tridev.liveweather.data.remote.dto.RainViewerResponse;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded persistent fallback for Radar metadata/model fields, not map image tiles. */
public final class RadarPersistentCache {

    private static final String PREFS = "live_weather_radar_persistent_cache";
    private static final String KEY_RADAR_JSON = "radar_json";
    private static final String KEY_RADAR_SAVED_AT = "radar_saved_at";
    private static final String PREFIX_FIELD_JSON = "field_json_";
    private static final String PREFIX_FIELD_LAT = "field_lat_";
    private static final String PREFIX_FIELD_LON = "field_lon_";
    private static final String PREFIX_FIELD_SAVED_AT = "field_saved_at_";

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();
    private final Type fieldListType = new TypeToken<ArrayList<RadarFieldPointResponse>>() { }.getType();

    public RadarPersistentCache(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveRadar(@NonNull RainViewerResponse response, long savedAt) {
        preferences.edit()
                .putString(KEY_RADAR_JSON, gson.toJson(response))
                .putLong(KEY_RADAR_SAVED_AT, safeTime(savedAt))
                .apply();
    }

    @Nullable
    public CachedRadar loadRadar() {
        String json = preferences.getString(KEY_RADAR_JSON, null);
        if (json == null || json.trim().isEmpty()) return null;
        try {
            RainViewerResponse response = gson.fromJson(json, RainViewerResponse.class);
            if (response == null) {
                clearRadar();
                return null;
            }
            return new CachedRadar(response, preferences.getLong(KEY_RADAR_SAVED_AT, 0L));
        } catch (JsonSyntaxException exception) {
            clearRadar();
            return null;
        }
    }

    public void saveField(
            @NonNull List<RadarFieldPointResponse> field,
            double latitude,
            double longitude,
            long savedAt
    ) {
        if (field.isEmpty()) return;
        String key = locationKey(latitude, longitude);
        preferences.edit()
                .putString(PREFIX_FIELD_JSON + key, gson.toJson(field))
                .putString(PREFIX_FIELD_LAT + key, Double.toString(latitude))
                .putString(PREFIX_FIELD_LON + key, Double.toString(longitude))
                .putLong(PREFIX_FIELD_SAVED_AT + key, safeTime(savedAt))
                .apply();
    }

    @Nullable
    public CachedField loadField(double latitude, double longitude) {
        String key = locationKey(latitude, longitude);
        String json = preferences.getString(PREFIX_FIELD_JSON + key, null);
        String latValue = preferences.getString(PREFIX_FIELD_LAT + key, null);
        String lonValue = preferences.getString(PREFIX_FIELD_LON + key, null);
        if (json == null || latValue == null || lonValue == null) return null;
        try {
            List<RadarFieldPointResponse> field = gson.fromJson(json, fieldListType);
            double storedLatitude = Double.parseDouble(latValue);
            double storedLongitude = Double.parseDouble(lonValue);
            if (field == null || field.isEmpty()) {
                clearField(key);
                return null;
            }
            return new CachedField(
                    field,
                    storedLatitude,
                    storedLongitude,
                    preferences.getLong(PREFIX_FIELD_SAVED_AT + key, 0L)
            );
        } catch (JsonSyntaxException | NumberFormatException exception) {
            clearField(key);
            return null;
        }
    }

    private void clearRadar() {
        preferences.edit().remove(KEY_RADAR_JSON).remove(KEY_RADAR_SAVED_AT).apply();
    }

    private void clearField(@NonNull String key) {
        preferences.edit()
                .remove(PREFIX_FIELD_JSON + key)
                .remove(PREFIX_FIELD_LAT + key)
                .remove(PREFIX_FIELD_LON + key)
                .remove(PREFIX_FIELD_SAVED_AT + key)
                .apply();
    }

    private long safeTime(long savedAt) {
        return savedAt > 0L ? savedAt : System.currentTimeMillis();
    }

    @NonNull
    private String locationKey(double latitude, double longitude) {
        return String.format(Locale.US, "%.2f_%.2f", latitude, longitude);
    }

    public static final class CachedRadar {
        private final RainViewerResponse response;
        private final long savedAt;

        CachedRadar(@NonNull RainViewerResponse response, long savedAt) {
            this.response = response;
            this.savedAt = savedAt;
        }

        @NonNull public RainViewerResponse getResponse() { return response; }
        public long getSavedAt() { return savedAt; }
    }

    public static final class CachedField {
        private final List<RadarFieldPointResponse> field;
        private final double latitude;
        private final double longitude;
        private final long savedAt;

        CachedField(
                @NonNull List<RadarFieldPointResponse> field,
                double latitude,
                double longitude,
                long savedAt
        ) {
            this.field = field;
            this.latitude = latitude;
            this.longitude = longitude;
            this.savedAt = savedAt;
        }

        @NonNull public List<RadarFieldPointResponse> getField() { return field; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public long getSavedAt() { return savedAt; }
    }
}
