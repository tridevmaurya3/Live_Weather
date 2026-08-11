package com.tridev.liveweather.ui.radar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.RadarFieldPointResponse;
import com.tridev.liveweather.data.remote.dto.RainViewerResponse;

import java.util.Collections;
import java.util.List;

public final class RadarUiState {

    private final double latitude;
    private final double longitude;
    private final boolean loadingRadar;
    private final boolean loadingField;
    private final RainViewerResponse radar;
    private final List<RadarFieldPointResponse> field;
    private final String radarError;
    private final String fieldError;
    private final boolean radarFromCache;
    private final boolean fieldFromCache;

    public RadarUiState(
            double latitude,
            double longitude,
            boolean loadingRadar,
            boolean loadingField,
            @Nullable RainViewerResponse radar,
            @Nullable List<RadarFieldPointResponse> field,
            @Nullable String radarError,
            @Nullable String fieldError,
            boolean radarFromCache,
            boolean fieldFromCache
    ) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.loadingRadar = loadingRadar;
        this.loadingField = loadingField;
        this.radar = radar;
        this.field = field == null ? Collections.emptyList() : field;
        this.radarError = radarError;
        this.fieldError = fieldError;
        this.radarFromCache = radarFromCache;
        this.fieldFromCache = fieldFromCache;
    }

    @NonNull
    public static RadarUiState loading(double latitude, double longitude) {
        return new RadarUiState(
                latitude, longitude, true, true,
                null, Collections.emptyList(), null, null, false, false
        );
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public boolean isLoadingRadar() { return loadingRadar; }
    public boolean isLoadingField() { return loadingField; }
    @Nullable public RainViewerResponse getRadar() { return radar; }
    @NonNull public List<RadarFieldPointResponse> getField() { return field; }
    @Nullable public String getRadarError() { return radarError; }
    @Nullable public String getFieldError() { return fieldError; }
    public boolean isRadarFromCache() { return radarFromCache; }
    public boolean isFieldFromCache() { return fieldFromCache; }

    public boolean hasRadarFrames() {
        return radar != null && radar.getPastFrames() != null && !radar.getPastFrames().isEmpty();
    }

    public boolean hasField() {
        return !field.isEmpty();
    }

    @NonNull
    public RadarUiState withRadar(
            @Nullable RainViewerResponse value,
            @Nullable String error,
            boolean fromCache
    ) {
        return new RadarUiState(
                latitude, longitude, false, loadingField,
                value, field, error, fieldError, fromCache, fieldFromCache
        );
    }

    @NonNull
    public RadarUiState withField(
            @Nullable List<RadarFieldPointResponse> value,
            @Nullable String error,
            boolean fromCache
    ) {
        return new RadarUiState(
                latitude, longitude, loadingRadar, false,
                radar, value, radarError, error, radarFromCache, fromCache
        );
    }
}
