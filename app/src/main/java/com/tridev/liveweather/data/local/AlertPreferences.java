package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.domain.alert.AlertLocation;
import com.tridev.liveweather.domain.alert.WeatherAlert;

import java.util.HashSet;
import java.util.Set;

public final class AlertPreferences {

    private static final String PREFS = "weather_alert_preferences";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";
    private static final String KEY_NOTIFIED = "notified_fingerprints";
    private static final String KEY_DISTRICT = "last_district";
    private static final String KEY_STATE = "last_state";
    private static final String KEY_COUNTRY = "last_country";
    private static final String KEY_LATITUDE = "last_latitude";
    private static final String KEY_LONGITUDE = "last_longitude";
    private static final String KEY_HAS_LOCATION = "has_location";

    private final SharedPreferences preferences;

    public AlertPreferences(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isNotificationsEnabled() {
        return preferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, false);
    }

    public void setNotificationsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public void saveLocation(@NonNull AlertLocation location) {
        preferences.edit()
                .putBoolean(KEY_HAS_LOCATION, true)
                .putLong(KEY_LATITUDE, Double.doubleToRawLongBits(location.getLatitude()))
                .putLong(KEY_LONGITUDE, Double.doubleToRawLongBits(location.getLongitude()))
                .putString(KEY_DISTRICT, location.getDistrict())
                .putString(KEY_STATE, location.getState())
                .putString(KEY_COUNTRY, location.getCountryCode())
                .apply();
    }

    @Nullable
    public AlertLocation loadLocation() {
        if (!preferences.getBoolean(KEY_HAS_LOCATION, false)) return null;
        return new AlertLocation(
                Double.longBitsToDouble(preferences.getLong(KEY_LATITUDE, 0L)),
                Double.longBitsToDouble(preferences.getLong(KEY_LONGITUDE, 0L)),
                preferences.getString(KEY_DISTRICT, null),
                preferences.getString(KEY_STATE, null),
                preferences.getString(KEY_COUNTRY, null)
        );
    }

    public boolean markIfNew(@NonNull WeatherAlert alert) {
        Set<String> fingerprints = new HashSet<>(
                preferences.getStringSet(KEY_NOTIFIED, new HashSet<>())
        );
        String fingerprint = alert.fingerprint();
        if (fingerprints.contains(fingerprint)) return false;
        fingerprints.add(fingerprint);
        if (fingerprints.size() > 80) {
            fingerprints.clear();
            fingerprints.add(fingerprint);
        }
        preferences.edit().putStringSet(KEY_NOTIFIED, fingerprints).apply();
        return true;
    }
}
