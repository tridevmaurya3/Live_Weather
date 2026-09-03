package com.tridev.liveweather.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.core.DataReliabilityPolicy;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import java.util.Locale;

/**
 * Immutable weather truth consumed by active app/wallpaper surfaces.
 *
 * The payload, location identity, provider observation time, fetch timestamp,
 * timezone and freshness classification travel together so a surface cannot
 * accidentally combine weather from one location with metadata from another.
 */
public final class ActiveWeatherSnapshot {

    public enum Scope {
        ACTIVE,
        FIXED_CITY
    }

    public enum Freshness {
        LIVE,
        CACHED,
        STALE
    }

    @NonNull private final WeatherResponse weather;
    private final double latitude;
    private final double longitude;
    private final long fetchedAt;
    private final long generation;
    @NonNull private final Scope scope;
    @NonNull private final Freshness freshness;
    @Nullable private final String locationLabel;

    public ActiveWeatherSnapshot(
            @NonNull WeatherResponse weather,
            double latitude,
            double longitude,
            long fetchedAt,
            long generation,
            @NonNull Scope scope,
            @Nullable String locationLabel,
            long now
    ) {
        this.weather = weather;
        this.latitude = latitude;
        this.longitude = longitude;
        this.fetchedAt = fetchedAt;
        this.generation = generation;
        this.scope = scope;
        this.locationLabel = cleanLabel(locationLabel);
        this.freshness = resolveFreshness(fetchedAt, now);
    }

    @NonNull public WeatherResponse getWeather() { return weather; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public long getFetchedAt() { return fetchedAt; }
    public long getGeneration() { return generation; }
    @NonNull public Scope getScope() { return scope; }
    @NonNull public Freshness getFreshness() { return freshness; }
    @Nullable public String getLocationLabel() { return locationLabel; }

    @Nullable
    public String getObservationTime() {
        WeatherResponse.CurrentWeather current = weather.getCurrent();
        return current == null ? null : current.getTime();
    }

    @Nullable
    public String getTimezone() {
        return weather.getTimezone();
    }

    @NonNull
    public String getIdentityKey() {
        return identityKey(latitude, longitude);
    }

    @NonNull
    public static Freshness resolveFreshness(long fetchedAt, long now) {
        DataReliabilityPolicy.Freshness freshness =
                DataReliabilityPolicy.weatherFreshness(fetchedAt, now);
        if (freshness == DataReliabilityPolicy.Freshness.RECENT) {
            return Freshness.LIVE;
        }
        if (freshness == DataReliabilityPolicy.Freshness.AGING) {
            return Freshness.CACHED;
        }
        return Freshness.STALE;
    }

    @NonNull
    public static String identityKey(double latitude, double longitude) {
        return String.format(Locale.US, "%.3f_%.3f", latitude, longitude);
    }

    @Nullable
    private static String cleanLabel(@Nullable String label) {
        if (label == null) return null;
        String clean = label.trim();
        return clean.isEmpty() ? null : clean;
    }
}
