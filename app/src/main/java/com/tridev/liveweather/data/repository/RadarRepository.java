package com.tridev.liveweather.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.LiveWeatherApplication;
import com.tridev.liveweather.data.local.RadarPersistentCache;
import com.tridev.liveweather.data.remote.api.RadarFieldApiClient;
import com.tridev.liveweather.data.remote.api.NetworkFailureMessage;
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
 * RainViewer is observed past radar imagery. Open-Meteo is sampled atmospheric
 * model context. Phase 23 adds bounded persistent metadata/model fallback after
 * process restart; it does not cache OSM/RainViewer image tiles or fabricate
 * future radar frames.
 */
public final class RadarRepository {

    private static final long RADAR_CACHE_MILLIS = 5L * 60L * 1000L;
    private static final long FIELD_CACHE_MILLIS = 10L * 60L * 1000L;
    private static final long PERSISTENT_RADAR_MAX_MILLIS = 6L * 60L * 60L * 1000L;
    private static final long PERSISTENT_FIELD_MAX_MILLIS = 3L * 60L * 60L * 1000L;
    private static final String FIELD_VARIABLES =
            "temperature_2m,cloud_cover,wind_speed_10m,wind_direction_10m";

    private final RadarPersistentCache persistentCache =
            new RadarPersistentCache(LiveWeatherApplication.appContext());

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
        void onSuccess(@NonNull T value, @NonNull DeliverySource source, long savedAtMillis);
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

        RadarPersistentCache.CachedRadar persisted = persistentCache.loadRadar();
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
                    persistentCache.saveRadar(body, receivedAt);
                    callback.onSuccess(body, DeliverySource.NETWORK, cachedRadarAt);
                    return;
                }

                if (cachedRadar != null
                        && RadarObservedDataPolicy.hasUsableObservedTimeline(cachedRadar, receivedAt)) {
                    callback.onSuccess(cachedRadar, DeliverySource.SERVER_FALLBACK_CACHE, cachedRadarAt);
                    return;
                }

                if (usablePersistentRadar(persisted, receivedAt)) {
                    adoptPersistentRadar(persisted);
                    callback.onSuccess(cachedRadar, DeliverySource.SERVER_FALLBACK_CACHE, cachedRadarAt);
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
                    callback.onSuccess(cachedRadar, DeliverySource.NETWORK_FALLBACK_CACHE, cachedRadarAt);
                    return;
                }
                if (usablePersistentRadar(persisted, failedAt)) {
                    adoptPersistentRadar(persisted);
                    callback.onSuccess(cachedRadar, DeliverySource.NETWORK_FALLBACK_CACHE, cachedRadarAt);
                    return;
                }
                callback.onError(
                        NetworkFailureMessage.forService("Observed radar", throwable),
                        throwable
                );
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
        boolean sameArea = sameArea(cachedFieldLatitude, cachedFieldLongitude, latitude, longitude);
        if (!force && sameArea && !cachedField.isEmpty()
                && now - cachedFieldAt < FIELD_CACHE_MILLIS) {
            callback.onSuccess(cachedField, DeliverySource.MEMORY_CACHE, cachedFieldAt);
            return;
        }

        RadarPersistentCache.CachedField persisted = persistentCache.loadField(latitude, longitude);
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
                    persistentCache.saveField(body, latitude, longitude, receivedAt);
                    callback.onSuccess(body, DeliverySource.NETWORK, cachedFieldAt);
                    return;
                }

                if (sameArea && !cachedField.isEmpty()) {
                    callback.onSuccess(cachedField, DeliverySource.SERVER_FALLBACK_CACHE, cachedFieldAt);
                    return;
                }
                if (usablePersistentField(persisted, latitude, longitude, receivedAt)) {
                    adoptPersistentField(persisted);
                    callback.onSuccess(cachedField, DeliverySource.SERVER_FALLBACK_CACHE, cachedFieldAt);
                    return;
                }
                callback.onError("Atmospheric model field unavailable (HTTP " + response.code() + ")", null);
            }

            @Override
            public void onFailure(
                    @NonNull Call<List<RadarFieldPointResponse>> call,
                    @NonNull Throwable throwable
            ) {
                long failedAt = System.currentTimeMillis();
                if (sameArea && !cachedField.isEmpty()) {
                    callback.onSuccess(cachedField, DeliverySource.NETWORK_FALLBACK_CACHE, cachedFieldAt);
                    return;
                }
                if (usablePersistentField(persisted, latitude, longitude, failedAt)) {
                    adoptPersistentField(persisted);
                    callback.onSuccess(cachedField, DeliverySource.NETWORK_FALLBACK_CACHE, cachedFieldAt);
                    return;
                }
                callback.onError(
                        NetworkFailureMessage.forService("Atmospheric model field", throwable),
                        throwable
                );
            }
        });
    }

    private boolean usablePersistentRadar(
            @Nullable RadarPersistentCache.CachedRadar persisted,
            long now
    ) {
        if (persisted == null || persisted.getSavedAt() <= 0L) return false;
        long age = Math.max(0L, now - persisted.getSavedAt());
        return age <= PERSISTENT_RADAR_MAX_MILLIS
                && RadarObservedDataPolicy.hasUsableObservedTimeline(persisted.getResponse(), now);
    }

    private void adoptPersistentRadar(@NonNull RadarPersistentCache.CachedRadar persisted) {
        cachedRadar = persisted.getResponse();
        cachedRadarAt = persisted.getSavedAt();
    }

    private boolean usablePersistentField(
            @Nullable RadarPersistentCache.CachedField persisted,
            double latitude,
            double longitude,
            long now
    ) {
        if (persisted == null || persisted.getSavedAt() <= 0L || persisted.getField().isEmpty()) {
            return false;
        }
        long age = Math.max(0L, now - persisted.getSavedAt());
        return age <= PERSISTENT_FIELD_MAX_MILLIS
                && sameArea(
                        persisted.getLatitude(),
                        persisted.getLongitude(),
                        latitude,
                        longitude
                );
    }

    private void adoptPersistentField(@NonNull RadarPersistentCache.CachedField persisted) {
        cachedField = persisted.getField();
        cachedFieldAt = persisted.getSavedAt();
        cachedFieldLatitude = persisted.getLatitude();
        cachedFieldLongitude = persisted.getLongitude();
    }

    private boolean sameArea(
            double firstLatitude,
            double firstLongitude,
            double secondLatitude,
            double secondLongitude
    ) {
        return !Double.isNaN(firstLatitude)
                && !Double.isNaN(firstLongitude)
                && Math.abs(firstLatitude - secondLatitude) < 0.08d
                && Math.abs(firstLongitude - secondLongitude) < 0.08d;
    }

    @NonNull
    private GridQuery buildGrid(double latitude, double longitude) {
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
