package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tridev.liveweather.domain.alert.WeatherAlert;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AlertCache {

    private static final String PREFS = "weather_alert_cache";
    private static final Type ALERT_LIST_TYPE = new TypeToken<List<WeatherAlert>>() {}.getType();

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    public AlertCache(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveOfficial(
            double latitude,
            double longitude,
            @NonNull List<WeatherAlert> alerts,
            @Nullable String etag,
            long savedAt
    ) {
        String key = locationKey(latitude, longitude);
        preferences.edit()
                .putString(key + "_alerts", gson.toJson(alerts))
                .putString(key + "_etag", etag)
                .putLong(key + "_saved_at", savedAt)
                .apply();
    }

    @NonNull
    public CachedAlerts loadOfficial(double latitude, double longitude) {
        String key = locationKey(latitude, longitude);
        String json = preferences.getString(key + "_alerts", null);
        List<WeatherAlert> alerts = new ArrayList<>();
        if (json != null && !json.trim().isEmpty()) {
            try {
                List<WeatherAlert> parsed = gson.fromJson(json, ALERT_LIST_TYPE);
                if (parsed != null) alerts.addAll(parsed);
            } catch (RuntimeException ignored) {
            }
        }
        return new CachedAlerts(
                alerts,
                preferences.getString(key + "_etag", null),
                preferences.getLong(key + "_saved_at", 0L)
        );
    }

    private String locationKey(double latitude, double longitude) {
        return String.format(
                Locale.ROOT,
                "loc_%+.2f_%+.2f",
                latitude,
                longitude
        ).replace('.', '_').replace('+', 'p').replace('-', 'm');
    }

    public static final class CachedAlerts {
        private final List<WeatherAlert> alerts;
        private final String etag;
        private final long savedAt;

        CachedAlerts(
                @NonNull List<WeatherAlert> alerts,
                @Nullable String etag,
                long savedAt
        ) {
            this.alerts = alerts;
            this.etag = etag;
            this.savedAt = savedAt;
        }

        @NonNull public List<WeatherAlert> getAlerts() { return alerts; }
        @Nullable public String getEtag() { return etag; }
        public long getSavedAt() { return savedAt; }
    }
}
