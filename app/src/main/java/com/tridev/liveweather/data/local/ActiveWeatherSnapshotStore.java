package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.core.ActiveWeatherRequestPolicy;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.ActiveWeatherSnapshot;

/**
 * Authoritative active-weather identity and generation store.
 *
 * Fixed-city widgets continue to use isolated per-location WeatherCache
 * snapshots. Only ACTIVE surfaces use this target/generation contract.
 */
public final class ActiveWeatherSnapshotStore {

    private static final String PREFS = "live_weather_active_snapshot";
    private static final String KEY_LATITUDE = "active_latitude";
    private static final String KEY_LONGITUDE = "active_longitude";
    private static final String KEY_LABEL = "active_label";
    private static final String KEY_GENERATION = "active_generation";
    private static final String KEY_VERSION = "active_version";

    private final SharedPreferences preferences;
    private final WeatherCache weatherCache;

    public ActiveWeatherSnapshotStore(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        weatherCache = new WeatherCache(appContext);
    }

    /**
     * Starts a new active refresh generation. Every response must commit through
     * the returned token; older same-location and old-location responses are rejected.
     */
    @NonNull
    public RequestToken beginRequest(
            double latitude,
            double longitude,
            @Nullable String locationLabel
    ) {
        Identity previous = loadIdentity();
        String identityKey = ActiveWeatherSnapshot.identityKey(latitude, longitude);
        String cleanLabel = cleanLabel(locationLabel);
        if (cleanLabel == null && previous != null
                && identityKey.equals(previous.getIdentityKey())) {
            cleanLabel = previous.getLocationLabel();
        }

        long generation = nextGeneration();
        weatherCache.activateLocation(latitude, longitude);
        writeTarget(latitude, longitude, cleanLabel, generation, true);
        return new RequestToken(generation, latitude, longitude);
    }

    /**
     * Aligns active identity without pretending that a network request exists.
     * Used at startup and when a saved city becomes selected.
     */
    public void ensureActiveTarget(
            double latitude,
            double longitude,
            @Nullable String locationLabel
    ) {
        String requestedKey = ActiveWeatherSnapshot.identityKey(latitude, longitude);
        Identity current = loadIdentity();
        String cleanLabel = cleanLabel(locationLabel);

        if (current != null && requestedKey.equals(current.getIdentityKey())) {
            weatherCache.activateLocation(latitude, longitude);
            if (cleanLabel != null && !cleanLabel.equals(current.getLocationLabel())) {
                writeTarget(
                        latitude,
                        longitude,
                        cleanLabel,
                        current.getGeneration(),
                        true
                );
            }
            return;
        }

        weatherCache.activateLocation(latitude, longitude);
        writeTarget(latitude, longitude, cleanLabel, nextGeneration(), true);
    }

    /** Clears the active identity while keeping per-location cached snapshots intact. */
    public void clearActiveTarget() {
        weatherCache.clearActiveLocation();
        long generation = nextGeneration();
        long version = nextVersion();
        preferences.edit()
                .remove(KEY_LATITUDE)
                .remove(KEY_LONGITUDE)
                .remove(KEY_LABEL)
                .putLong(KEY_GENERATION, generation)
                .putLong(KEY_VERSION, version)
                .apply();
    }

    /** Captures the currently selected active identity for background work. */
    @Nullable
    public RequestToken captureRequest() {
        Identity identity = loadIdentity();
        if (identity == null) {
            WeatherCache.CachedWeather legacy = weatherCache.load();
            if (legacy == null) return null;
            ensureActiveTarget(legacy.getLatitude(), legacy.getLongitude(), null);
            identity = loadIdentity();
        }
        if (identity == null) return null;
        return new RequestToken(
                identity.getGeneration(),
                identity.getLatitude(),
                identity.getLongitude()
        );
    }

    public boolean isCurrent(@NonNull RequestToken token) {
        Identity identity = loadIdentity();
        return identity != null && ActiveWeatherRequestPolicy.isCurrent(
                token.getGeneration(),
                token.getIdentityKey(),
                identity.getGeneration(),
                identity.getIdentityKey()
        );
    }

    /**
     * Commits a live response only when its generation is still authoritative.
     * A late response is retained as an isolated cache snapshot but can never
     * move the active pointer or replace the newer active truth.
     */
    public boolean commitIfCurrent(
            @NonNull RequestToken token,
            @NonNull WeatherResponse weather,
            long fetchedAt
    ) {
        long safeFetchedAt = fetchedAt > 0L ? fetchedAt : System.currentTimeMillis();
        if (!isCurrent(token)) {
            weatherCache.saveSnapshot(
                    weather,
                    token.getLatitude(),
                    token.getLongitude(),
                    safeFetchedAt
            );
            return false;
        }

        weatherCache.save(
                weather,
                token.getLatitude(),
                token.getLongitude(),
                safeFetchedAt
        );
        signalChanged();
        return true;
    }

    @Nullable
    public ActiveWeatherSnapshot loadActive(long now) {
        Identity identity = loadIdentity();
        if (identity == null) {
            WeatherCache.CachedWeather legacy = weatherCache.load();
            if (legacy == null) return null;
            ensureActiveTarget(legacy.getLatitude(), legacy.getLongitude(), null);
            identity = loadIdentity();
        }
        if (identity == null) return null;

        WeatherCache.CachedWeather cached = weatherCache.load(
                identity.getLatitude(),
                identity.getLongitude()
        );
        if (cached == null) return null;

        return new ActiveWeatherSnapshot(
                cached.getWeather(),
                cached.getLatitude(),
                cached.getLongitude(),
                cached.getSavedAt(),
                identity.getGeneration(),
                ActiveWeatherSnapshot.Scope.ACTIVE,
                identity.getLocationLabel(),
                now
        );
    }

    @Nullable
    public ActiveWeatherSnapshot loadFixed(
            double latitude,
            double longitude,
            @Nullable String label,
            long now
    ) {
        WeatherCache.CachedWeather cached = weatherCache.load(latitude, longitude);
        if (cached == null) return null;
        return new ActiveWeatherSnapshot(
                cached.getWeather(),
                cached.getLatitude(),
                cached.getLongitude(),
                cached.getSavedAt(),
                0L,
                ActiveWeatherSnapshot.Scope.FIXED_CITY,
                label,
                now
        );
    }

    @Nullable
    public Identity loadIdentity() {
        String latitudeValue = preferences.getString(KEY_LATITUDE, null);
        String longitudeValue = preferences.getString(KEY_LONGITUDE, null);
        if (latitudeValue == null || longitudeValue == null) return null;
        try {
            double latitude = Double.parseDouble(latitudeValue);
            double longitude = Double.parseDouble(longitudeValue);
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) return null;
            return new Identity(
                    latitude,
                    longitude,
                    preferences.getString(KEY_LABEL, null),
                    preferences.getLong(KEY_GENERATION, 0L)
            );
        } catch (NumberFormatException exception) {
            clearCorruptIdentity();
            return null;
        }
    }

    public void registerListener(
            @NonNull SharedPreferences.OnSharedPreferenceChangeListener listener
    ) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(
            @NonNull SharedPreferences.OnSharedPreferenceChangeListener listener
    ) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    public static boolean isChangeSignal(@Nullable String key) {
        return KEY_VERSION.equals(key);
    }

    private void writeTarget(
            double latitude,
            double longitude,
            @Nullable String label,
            long generation,
            boolean signalChange
    ) {
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_LATITUDE, Double.toString(latitude))
                .putString(KEY_LONGITUDE, Double.toString(longitude))
                .putLong(KEY_GENERATION, generation);
        if (label == null) editor.remove(KEY_LABEL);
        else editor.putString(KEY_LABEL, label);
        if (signalChange) editor.putLong(KEY_VERSION, nextVersion());
        editor.apply();
    }

    private void signalChanged() {
        preferences.edit().putLong(KEY_VERSION, nextVersion()).apply();
    }

    private long nextGeneration() {
        long current = preferences.getLong(KEY_GENERATION, 0L);
        return current == Long.MAX_VALUE ? 1L : Math.max(1L, current + 1L);
    }

    private long nextVersion() {
        long current = preferences.getLong(KEY_VERSION, 0L);
        return current == Long.MAX_VALUE ? 1L : Math.max(1L, current + 1L);
    }

    private void clearCorruptIdentity() {
        weatherCache.clearActiveLocation();
        preferences.edit()
                .remove(KEY_LATITUDE)
                .remove(KEY_LONGITUDE)
                .remove(KEY_LABEL)
                .putLong(KEY_VERSION, nextVersion())
                .apply();
    }

    @Nullable
    private String cleanLabel(@Nullable String label) {
        if (label == null) return null;
        String clean = label.trim();
        return clean.isEmpty() ? null : clean;
    }

    public static final class Identity {
        private final double latitude;
        private final double longitude;
        @Nullable private final String locationLabel;
        private final long generation;

        Identity(
                double latitude,
                double longitude,
                @Nullable String locationLabel,
                long generation
        ) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.locationLabel = locationLabel;
            this.generation = generation;
        }

        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        @Nullable public String getLocationLabel() { return locationLabel; }
        public long getGeneration() { return generation; }
        @NonNull public String getIdentityKey() {
            return ActiveWeatherSnapshot.identityKey(latitude, longitude);
        }
    }

    public static final class RequestToken {
        private final long generation;
        private final double latitude;
        private final double longitude;

        RequestToken(long generation, double latitude, double longitude) {
            this.generation = generation;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public long getGeneration() { return generation; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        @NonNull public String getIdentityKey() {
            return ActiveWeatherSnapshot.identityKey(latitude, longitude);
        }
    }
}
