package com.tridev.liveweather.core;

import android.content.Context;

import androidx.annotation.NonNull;

import com.tridev.liveweather.data.local.AirQualityCache;
import com.tridev.liveweather.data.local.RadarPersistentCache;
import com.tridev.liveweather.data.local.SavedCityStore;
import com.tridev.liveweather.data.local.WeatherCache;
import com.tridev.liveweather.domain.CityLocation;

import java.util.Locale;

/** Shared Phase 23 cache-age and location-identity truth policy. */
public final class DataReliabilityPolicy {

    public static final long WEATHER_RECENT_MILLIS = 45L * 60L * 1000L;
    public static final long WEATHER_STALE_MILLIS = 3L * 60L * 60L * 1000L;
    public static final long WEATHER_VERY_STALE_MILLIS = 12L * 60L * 60L * 1000L;
    public static final long AIR_RECENT_MILLIS = 45L * 60L * 1000L;
    public static final long AIR_STALE_MILLIS = 3L * 60L * 60L * 1000L;
    public static final double LOCATION_MATCH_DELTA = 0.01d;
    public static final int MAX_BACKGROUND_RETRY_ATTEMPTS = 3;

    private DataReliabilityPolicy() {
    }

    public enum Freshness {
        RECENT,
        AGING,
        STALE,
        VERY_STALE,
        UNKNOWN
    }

    @NonNull
    public static Freshness weatherFreshness(long savedAt, long now) {
        long age = ageMillis(savedAt, now);
        if (age == Long.MAX_VALUE) return Freshness.UNKNOWN;
        if (age <= WEATHER_RECENT_MILLIS) return Freshness.RECENT;
        if (age <= WEATHER_STALE_MILLIS) return Freshness.AGING;
        if (age <= WEATHER_VERY_STALE_MILLIS) return Freshness.STALE;
        return Freshness.VERY_STALE;
    }

    @NonNull
    public static Freshness airFreshness(long savedAt, long now) {
        long age = ageMillis(savedAt, now);
        if (age == Long.MAX_VALUE) return Freshness.UNKNOWN;
        if (age <= AIR_RECENT_MILLIS) return Freshness.RECENT;
        if (age <= AIR_STALE_MILLIS) return Freshness.AGING;
        return Freshness.STALE;
    }

    public static long ageMillis(long savedAt, long now) {
        if (savedAt <= 0L || now <= 0L) return Long.MAX_VALUE;
        return Math.max(0L, now - savedAt);
    }

    @NonNull
    public static String ageLabel(long savedAt, long now) {
        long age = ageMillis(savedAt, now);
        if (age == Long.MAX_VALUE) return "unknown age";
        long minutes = age / 60_000L;
        if (minutes < 1L) return "less than 1 min old";
        if (minutes < 60L) return minutes + " min old";
        long hours = minutes / 60L;
        long remainder = minutes % 60L;
        if (hours < 24L) {
            return remainder == 0L ? hours + " h old" : hours + " h " + remainder + " min old";
        }
        long days = hours / 24L;
        return days + (days == 1L ? " day old" : " days old");
    }

    @NonNull
    public static String weatherCacheMessage(long savedAt, long now) {
        Freshness freshness = weatherFreshness(savedAt, now);
        String age = ageLabel(savedAt, now);
        switch (freshness) {
            case RECENT:
                return "Showing recent saved weather · " + age + ".";
            case AGING:
                return "Showing saved weather · " + age + " · refresh recommended.";
            case STALE:
                return "Showing stale saved weather · " + age + " · live conditions may have changed.";
            case VERY_STALE:
                return "Showing very old saved weather · " + age + " · offline fallback only.";
            default:
                return "Showing saved weather with unknown cache age.";
        }
    }

    public static boolean sameLocation(
            double firstLatitude,
            double firstLongitude,
            double secondLatitude,
            double secondLongitude
    ) {
        return !Double.isNaN(firstLatitude)
                && !Double.isNaN(firstLongitude)
                && !Double.isNaN(secondLatitude)
                && !Double.isNaN(secondLongitude)
                && Math.abs(firstLatitude - secondLatitude) <= LOCATION_MATCH_DELTA
                && Math.abs(firstLongitude - secondLongitude) <= LOCATION_MATCH_DELTA;
    }

    public static boolean shouldRetryBackground(int runAttemptCount) {
        return runAttemptCount + 1 < MAX_BACKGROUND_RETRY_ATTEMPTS;
    }

    @NonNull
    public static String diagnostics(@NonNull Context context) {
        long now = System.currentTimeMillis();
        WeatherCache weatherCache = new WeatherCache(context);
        WeatherCache.CachedWeather weather = weatherCache.load();
        CityLocation selectedCity = new SavedCityStore(context).getSelectedCity();

        StringBuilder report = new StringBuilder();
        if (weather == null) {
            report.append("Weather cache: none\n");
            report.append("AQI cache: waiting for active weather location\n");
        } else {
            report.append("Weather cache: ")
                    .append(freshnessLabel(weatherFreshness(weather.getSavedAt(), now)))
                    .append(" · ")
                    .append(ageLabel(weather.getSavedAt(), now))
                    .append('\n');
            report.append(String.format(
                    Locale.US,
                    "Active cache: %.3f, %.3f\n",
                    weather.getLatitude(),
                    weather.getLongitude()
            ));

            AirQualityCache.CachedAirQuality air = new AirQualityCache(context)
                    .load(weather.getLatitude(), weather.getLongitude());
            if (air == null) {
                report.append("AQI cache: none for active weather location\n");
            } else {
                report.append("AQI cache: ")
                        .append(freshnessLabel(airFreshness(air.getSavedAt(), now)))
                        .append(" · ")
                        .append(ageLabel(air.getSavedAt(), now))
                        .append('\n');
            }
        }

        if (selectedCity == null) {
            report.append("Location mode: device/current location\n");
        } else {
            boolean identityMatch = weather != null && sameLocation(
                    selectedCity.getLatitude(),
                    selectedCity.getLongitude(),
                    weather.getLatitude(),
                    weather.getLongitude()
            );
            report.append("Selected city: ").append(selectedCity.getDisplayName()).append('\n');
            report.append("Selected-city/cache identity: ")
                    .append(identityMatch ? "aligned" : "waiting for selected-city refresh")
                    .append('\n');
        }

        RadarPersistentCache radarCache = new RadarPersistentCache(context);
        RadarPersistentCache.CachedRadar radar = radarCache.loadRadar();
        report.append("Radar metadata cache: ")
                .append(radar == null ? "none" : ageLabel(radar.getSavedAt(), now))
                .append('\n');
        if (weather != null) {
            RadarPersistentCache.CachedField field = radarCache.loadField(
                    weather.getLatitude(),
                    weather.getLongitude()
            );
            report.append("Radar model field cache: ")
                    .append(field == null ? "none for active location" : ageLabel(field.getSavedAt(), now))
                    .append('\n');
        }

        report.append("App + Live Wallpaper: shared active cache\n");
        report.append("Fixed-city widgets: isolated snapshots\n");
        report.append("Radar image tiles: provider/network backed, not persistent offline tiles\n");
        report.append("Offline mode: saved data remains visible with age/stale labels");
        return report.toString();
    }

    @NonNull
    private static String freshnessLabel(@NonNull Freshness freshness) {
        switch (freshness) {
            case RECENT: return "recent";
            case AGING: return "aging";
            case STALE: return "stale";
            case VERY_STALE: return "very stale";
            default: return "unknown";
        }
    }
}
