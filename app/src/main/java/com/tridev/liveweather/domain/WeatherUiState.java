package com.tridev.liveweather.domain;

import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;

/**
 * Immutable shared weather state observed by the app UI.
 */
public final class WeatherUiState {

    private final boolean loading;
    private final WeatherResponse weather;
    private final boolean fromCache;
    private final String message;
    private final long updatedAt;
    private final double latitude;
    private final double longitude;

    public WeatherUiState(
            boolean loading,
            @Nullable WeatherResponse weather,
            boolean fromCache,
            @Nullable String message,
            long updatedAt,
            double latitude,
            double longitude
    ) {
        this.loading = loading;
        this.weather = weather;
        this.fromCache = fromCache;
        this.message = message;
        this.updatedAt = updatedAt;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean isLoading() {
        return loading;
    }

    @Nullable
    public WeatherResponse getWeather() {
        return weather;
    }

    public boolean isFromCache() {
        return fromCache;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public boolean hasWeather() {
        return weather != null;
    }
}
