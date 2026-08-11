package com.tridev.liveweather.domain;

import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.AirQualityResponse;

public final class AirQualityUiState {

    private final boolean loading;
    private final AirQualityResponse data;
    private final boolean fromCache;
    private final String message;
    private final long updatedAt;
    private final double latitude;
    private final double longitude;

    public AirQualityUiState(
            boolean loading,
            @Nullable AirQualityResponse data,
            boolean fromCache,
            @Nullable String message,
            long updatedAt,
            double latitude,
            double longitude
    ) {
        this.loading = loading;
        this.data = data;
        this.fromCache = fromCache;
        this.message = message;
        this.updatedAt = updatedAt;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean isLoading() { return loading; }
    @Nullable public AirQualityResponse getData() { return data; }
    public boolean hasData() { return data != null; }
    public boolean isFromCache() { return fromCache; }
    @Nullable public String getMessage() { return message; }
    public long getUpdatedAt() { return updatedAt; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
