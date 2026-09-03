package com.tridev.liveweather.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;

/** Pure policy for battery-safe foreground weather freshness. */
public final class LiveDataFreshnessPolicy {

    public static final long FOREGROUND_CHECK_MILLIS = 60_000L;
    public static final long ACTIVE_WEATHER_REFRESH_MILLIS = 5L * 60L * 1000L;
    public static final long NORMAL_WEATHER_REFRESH_MILLIS = 10L * 60L * 1000L;

    private LiveDataFreshnessPolicy() { }

    public static long refreshIntervalMillis(@Nullable WeatherResponse weather) {
        LiveConditionResolver.ResolvedCondition condition =
                LiveConditionResolver.resolve(weather);
        Integer code = condition.getWeatherCode();
        return isActiveWeather(code)
                ? ACTIVE_WEATHER_REFRESH_MILLIS
                : NORMAL_WEATHER_REFRESH_MILLIS;
    }

    public static boolean shouldRefresh(
            long updatedAt,
            long now,
            long intervalMillis,
            boolean requestInFlight
    ) {
        if (requestInFlight) return false;
        if (updatedAt <= 0L || now <= 0L) return true;
        return Math.max(0L, now - updatedAt) >= Math.max(1L, intervalMillis);
    }

    public static boolean sameArea(
            double firstLatitude,
            double firstLongitude,
            double secondLatitude,
            double secondLongitude
    ) {
        return DataReliabilityPolicy.sameLocation(
                firstLatitude,
                firstLongitude,
                secondLatitude,
                secondLongitude
        );
    }

    private static boolean isActiveWeather(@Nullable Integer code) {
        if (code == null) return false;
        return (code >= 51 && code <= 77) || code >= 80;
    }
}
