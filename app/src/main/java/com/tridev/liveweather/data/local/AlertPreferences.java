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
    private static final String KEY_OFFICIAL_ALERTS_ENABLED = "official_alerts_enabled";
    private static final String KEY_SMART_RISK_ENABLED = "smart_risk_enabled";
    private static final String KEY_OFFICIAL_NOTIFICATIONS_ENABLED = "official_notifications_enabled";
    private static final String KEY_SMART_NOTIFICATIONS_ENABLED = "smart_notifications_enabled";
    private static final String KEY_MINIMUM_SEVERITY = "minimum_severity";
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

    /**
     * Source display filters default to enabled so Phase 21 never silently hides
     * categories that were visible before the settings surface existed.
     */
    public boolean isOfficialAlertsEnabled() {
        return preferences.getBoolean(KEY_OFFICIAL_ALERTS_ENABLED, true);
    }

    public void setOfficialAlertsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_OFFICIAL_ALERTS_ENABLED, enabled).apply();
    }

    public boolean isSmartRiskEnabled() {
        return preferences.getBoolean(KEY_SMART_RISK_ENABLED, true);
    }

    public void setSmartRiskEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_SMART_RISK_ENABLED, enabled).apply();
    }

    /** Per-source notification toggles are additional gates, not replacements for the master switch. */
    public boolean isOfficialNotificationsEnabled() {
        return preferences.getBoolean(KEY_OFFICIAL_NOTIFICATIONS_ENABLED, true);
    }

    public void setOfficialNotificationsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_OFFICIAL_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public boolean isSmartNotificationsEnabled() {
        return preferences.getBoolean(KEY_SMART_NOTIFICATIONS_ENABLED, true);
    }

    public void setSmartNotificationsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_SMART_NOTIFICATIONS_ENABLED, enabled).apply();
    }

    public boolean isAnyNotificationSourceEnabled() {
        return (isOfficialAlertsEnabled() && isOfficialNotificationsEnabled())
                || (isSmartRiskEnabled() && isSmartNotificationsEnabled());
    }

    @NonNull
    public WeatherAlert.Severity getMinimumSeverity() {
        String stored = preferences.getString(
                KEY_MINIMUM_SEVERITY,
                WeatherAlert.Severity.YELLOW.name()
        );
        if (stored == null) return WeatherAlert.Severity.YELLOW;
        try {
            return WeatherAlert.Severity.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            return WeatherAlert.Severity.YELLOW;
        }
    }

    public void setMinimumSeverity(@NonNull WeatherAlert.Severity severity) {
        preferences.edit().putString(KEY_MINIMUM_SEVERITY, severity.name()).apply();
    }

    @NonNull
    public WeatherAlert.Severity cycleMinimumSeverity() {
        WeatherAlert.Severity next;
        switch (getMinimumSeverity()) {
            case INFO:
                next = WeatherAlert.Severity.YELLOW;
                break;
            case YELLOW:
                next = WeatherAlert.Severity.ORANGE;
                break;
            case ORANGE:
                next = WeatherAlert.Severity.RED;
                break;
            default:
                next = WeatherAlert.Severity.INFO;
                break;
        }
        setMinimumSeverity(next);
        return next;
    }

    public boolean isSourceEnabled(@NonNull WeatherAlert alert) {
        return alert.isOfficial() ? isOfficialAlertsEnabled() : isSmartRiskEnabled();
    }

    public boolean isNotificationSourceEnabled(@NonNull WeatherAlert alert) {
        if (!isSourceEnabled(alert)) return false;
        return alert.isOfficial()
                ? isOfficialNotificationsEnabled()
                : isSmartNotificationsEnabled();
    }

    public boolean meetsMinimumSeverity(@NonNull WeatherAlert.Severity severity) {
        return severityRank(severity) >= severityRank(getMinimumSeverity());
    }

    public boolean shouldShow(@NonNull WeatherAlert alert) {
        return isSourceEnabled(alert) && meetsMinimumSeverity(alert.getSeverity());
    }

    /**
     * Notification policy keeps the pre-Phase-21 confidence floors:
     * official warnings require Yellow+, while Smart Risk requires Orange+.
     * User settings may make delivery stricter but cannot lower the Smart Risk floor.
     */
    public boolean shouldNotify(@NonNull WeatherAlert alert) {
        if (!isNotificationSourceEnabled(alert) || !meetsMinimumSeverity(alert.getSeverity())) {
            return false;
        }
        if (alert.isOfficial()) {
            return severityRank(alert.getSeverity()) >= severityRank(WeatherAlert.Severity.YELLOW);
        }
        return severityRank(alert.getSeverity()) >= severityRank(WeatherAlert.Severity.ORANGE);
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

    private static int severityRank(@NonNull WeatherAlert.Severity severity) {
        switch (severity) {
            case RED:
                return 4;
            case ORANGE:
                return 3;
            case YELLOW:
                return 2;
            default:
                return 1;
        }
    }
}