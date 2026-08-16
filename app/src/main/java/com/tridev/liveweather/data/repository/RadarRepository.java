package com.tridev.liveweather.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.api.RadarFieldApiClient;
import com.tridev.liveweather.data.remote.api.RainViewerApiClient;
import com.tridev.liveweather.data.remote.dto.RadarFieldPointResponse;
import com.tridev.liveweather.data.remote.dto.RainViewerResponse;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Radar Pro repository.
 *
 * RainViewer is treated as observed past radar imagery. Open-Meteo is a sampled
 * atmospheric model field for cloud/wind/temperature context; it is never
 * promoted to observed radar truth or used to fabricate future radar frames.
 *
 * Phase 20B.5 also reports where each successful delivery came from and when
 * the cached/network payload was saved. This lets the UI distinguish a normal
 * in-memory cache hit from a network/server fallback instead of calling every
 * cached response "offline".
 */
public final class RadarRepository {

    private static final long RADAR_CACHE_MILLIS = 5L * 60L * 1000L;
    private static final long FIELD_CACHE_MILLIS = 10L * 60L * 1000L;
    private static final String FIELD_VARIABLES =
            "temperature_2m,cloud_cover,wind_speed_10m,wind_direction_10m";

    private RainViewerResponse cachedRadar;
    private long cachedRadarAt;

    private List<RadarFieldPointResponse> cachedField = Collections.emptyList();
    private long cachedFieldAt;
    private double cachedFieldLatitude = Double.NaN;
    private double cachedFieldLongitude = Double.NaN;

    public enum DeliverySource {
        NETWORK,
        MEMORY_CACHE,
        NETWORK_FALLBACK_CACHE,
        SERVER_FALLBACK_CACHE;

        public boolean isCache() {
            return this != NETWORK;
        }

        public boolean isFallback() {
            return this == NETWORK_FALLBACK_CACHE || this == SERVER_FALLBACK_CACHE;
        }

        public boolean isNetworkFallback() {
            return this == NETWORK_FALLBACK_CACHE;
        }

        public boolean isServerFallback() {
            return this == SERVER_FALLBACK_CACHE;
        }
    }

    public interface ResultCallback<T> {
        void onSuccess(
                @NonNull T value,
                @NonNull DeliverySource source,
                long savedAtMillis
        );

        void onError(@NonNull String message, @Nullable Throwable throwable);
    }

    public void loadRadar(boolean force, @NonNull ResultCallback<RainViewerResponse> callback) {
        long now = System.currentTimeMillis();
        if (!force
                && cachedRadar != null
                && now - cachedRadarAt < RADAR_CACHE_MILLIS
                && RadarObservedDataPolicy.hasUsableObservedTimeline(cachedRadar, now)) {
            callback.onSuccess(cachedRadar, DeliverySource.MEMORY_CACHE, cachedRadarAt);
            return;
        }

        RainViewerApiClient.getApiService().getWeatherMaps().enqueue(new Callback<RainViewerResponse>() {
            @Override
            public void onResponse(
                    @NonNull Call<RainViewerResponse> call,
                    @NonNull Response<RainViewerResponse> response
            ) {
                long receivedAt = System.currentTimeMillis();
                RainViewerResponse body = response.body();
                if (response.isSuccessful()
                        && body != null
                        && RadarObservedDataPolicy.hasUsableObservedTimeline(body, receivedAt)) {
                    cachedRadar = body;
                    cachedRadarAt = receivedAt;
                    callback.onSuccess(body, DeliverySource.NETWORK, cachedRadarAt);
                    return;
                }

                if (cachedRadar != null
                        && RadarObservedDataPolicy.hasUsableObservedTimeline(cachedRadar, receivedAt)) {
                    callback.onSuccess(
                            cachedRadar,
                            DeliverySource.SERVER_FALLBACK_CACHE,
                            cachedRadarAt
                    );
                    return;
                }

                if (response.isSuccessful() && body != null) {
                    callback.onError("Radar metadata contained no usable observed frames", null);
                } else {
                    callback.onError("Radar timeline unavailable (HTTP " + response.code() + ")", null);
                }
            }

            @Override
            public void onFailure(
                    @NonNull Call<RainViewerResponse> call,
                    @NonNull Throwable throwable
            ) {
                long failedAt = System.currentTimeMillis();
                if (cachedRadar != null
                        && RadarObservedDataPolicy.hasUsableObservedTimeline(cachedRadar, failedAt)) {
                    callback.onSuccess(
                            cachedRadar,
                            DeliverySource.NETWORK_FALLBACK_CACHE,
                            cachedRadarAt
                    );
                    return;
                }
                callback.onError("Observed radar network unavailable", throwable);
            }
        });
    }

    public void loadField(
            double latitude,
            double longitude,
            boolean force,
            @NonNull ResultCallback<List<RadarFieldPointResponse>> callback
    ) {
        long now = System.currentTimeMillis();
        boolean sameArea = !Double.isNaN(cachedFieldLatitude)
                && Math.abs(cachedFieldLatitude - latitude) < 0.08d
                && Math.abs(cachedFieldLongitude - longitude) < 0.08d;
        if (!force && sameArea && !cachedField.isEmpty()
                && now - cachedFieldAt < FIELD_CACHE_MILLIS) {
            callback.onSuccess(cachedField, DeliverySource.MEMORY_CACHE, cachedFieldAt);
            return;
        }

        GridQuery grid = buildGrid(latitude, longitude);
        RadarFieldApiClient.getApiService().getCurrentField(
                grid.latitudes,
                grid.longitudes,
                FIELD_VARIABLES,
                "auto"
        ).enqueue(new Callback<List<RadarFieldPointResponse>>() {
            @Override
            public void onResponse(
                    @NonNull Call<List<RadarFieldPointResponse>> call,
                    @NonNull Response<List<RadarFieldPointResponse>> response
            ) {
                long receivedAt = System.currentTimeMillis();
                List<RadarFieldPointResponse> body = response.body();
                if (response.isSuccessful() && body != null && !body.isEmpty()) {
                    cachedField = body;
                    cachedFieldAt = receivedAt;
                    cachedFieldLatitude = latitude;
                    cachedFieldLongitude = longitude;
                    callback.onSuccess(body, DeliverySource.NETWORK, cachedFieldAt);
                    return;
                }

                if (sameArea && !cachedField.isEmpty()) {
                    callback.onSuccess(
                            cachedField,
                            DeliverySource.SERVER_FALLBACK_CACHE,
                            cachedFieldAt
                    );
                    return;
                }
                callback.onError("Atmospheric model field unavailable (HTTP " + response.code() + ")", null);
            }

            @Override
            public void onFailure(
                    @NonNull Call<List<RadarFieldPointResponse>> call,
                    @NonNull Throwable throwable
            ) {
                if (sameArea && !cachedField.isEmpty()) {
                    callback.onSuccess(
                            cachedField,
                            DeliverySource.NETWORK_FALLBACK_CACHE,
                            cachedFieldAt
                    );
                    return;
                }
                callback.onError("Atmospheric model field network unavailable", throwable);
            }
        });
    }

    @NonNull
    private GridQuery buildGrid(double latitude, double longitude) {
        // 5x5 model field around the active location. One Open-Meteo request is
        // used for all 25 coordinates, avoiding per-point network calls.
        double[] offsets = {-2.0d, -1.0d, 0.0d, 1.0d, 2.0d};
        StringBuilder latitudes = new StringBuilder();
        StringBuilder longitudes = new StringBuilder();

        for (double latOffset : offsets) {
            for (double lonOffset : offsets) {
                if (latitudes.length() > 0) {
                    latitudes.append(',');
                    longitudes.append(',');
                }
                double lat = Math.max(-89.5d, Math.min(89.5d, latitude + latOffset));
                double lon = normalizeLongitude(longitude + lonOffset);
                latitudes.append(String.format(Locale.US, "%.4f", lat));
                longitudes.append(String.format(Locale.US, "%.4f", lon));
            }
        }
        return new GridQuery(latitudes.toString(), longitudes.toString());
    }

    private double normalizeLongitude(double longitude) {
        double value = longitude;
        while (value > 180d) value -= 360d;
        while (value < -180d) value += 360d;
        return value;
    }

    private static final class GridQuery {
        final String latitudes;
        final String longitudes;

        GridQuery(String latitudes, String longitudes) {
            this.latitudes = latitudes;
            this.longitudes = longitudes;
        }
    }
}
