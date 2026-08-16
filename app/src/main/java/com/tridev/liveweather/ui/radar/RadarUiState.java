package com.tridev.liveweather.ui.radar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.RadarFieldPointResponse;
import com.tridev.liveweather.data.remote.dto.RainViewerResponse;
import com.tridev.liveweather.data.repository.RadarObservedDataPolicy;
import com.tridev.liveweather.data.repository.RadarRepository;

import java.util.Collections;
import java.util.List;

/**
 * Radar UI state with an explicit truth boundary:
 * - observedFrames = validated RainViewer past radar observations
 * - field = Open-Meteo sampled atmospheric model context
 * - source/savedAt = delivery provenance and cache age, not meteorological truth
 */
public final class RadarUiState {

    private static final long FIELD_DELAYED_MILLIS = 30L * 60L * 1000L;
    private static final long RADAR_DELAYED_MILLIS = 30L * 60L * 1000L;

    private final double latitude;
    private final double longitude;
    private final boolean loadingRadar;
    private final boolean loadingField;
    private final RainViewerResponse radar;
    private final List<RainViewerResponse.Frame> observedFrames;
    private final String safeRadarHost;
    private final boolean radarMetadataStale;
    private final List<RadarFieldPointResponse> field;
    private final String radarError;
    private final String fieldError;
    private final RadarRepository.DeliverySource radarSource;
    private final RadarRepository.DeliverySource fieldSource;
    private final long radarSavedAtMillis;
    private final long fieldSavedAtMillis;

    public RadarUiState(
            double latitude,
            double longitude,
            boolean loadingRadar,
            boolean loadingField,
            @Nullable RainViewerResponse radar,
            @Nullable List<RadarFieldPointResponse> field,
            @Nullable String radarError,
            @Nullable String fieldError,
            @Nullable RadarRepository.DeliverySource radarSource,
            @Nullable RadarRepository.DeliverySource fieldSource,
            long radarSavedAtMillis,
            long fieldSavedAtMillis
    ) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.loadingRadar = loadingRadar;
        this.loadingField = loadingField;
        this.radar = radar;

        long now = System.currentTimeMillis();
        this.observedFrames = RadarObservedDataPolicy.sanitizePastFrames(radar, now);
        this.safeRadarHost = RadarObservedDataPolicy.safeHost(radar);
        this.radarMetadataStale = radar != null && RadarObservedDataPolicy.isMetadataStale(radar, now);

        this.field = field == null ? Collections.emptyList() : field;
        this.radarError = radarError;
        this.fieldError = fieldError;
        this.radarSource = radarSource;
        this.fieldSource = fieldSource;
        this.radarSavedAtMillis = Math.max(0L, radarSavedAtMillis);
        this.fieldSavedAtMillis = Math.max(0L, fieldSavedAtMillis);
    }

    /** Backward-compatible constructor for older callers. */
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
        this(
                latitude,
                longitude,
                loadingRadar,
                loadingField,
                radar,
                field,
                radarError,
                fieldError,
                radar == null ? null : (radarFromCache
                        ? RadarRepository.DeliverySource.MEMORY_CACHE
                        : RadarRepository.DeliverySource.NETWORK),
                field == null || field.isEmpty() ? null : (fieldFromCache
                        ? RadarRepository.DeliverySource.MEMORY_CACHE
                        : RadarRepository.DeliverySource.NETWORK),
                radar == null ? 0L : System.currentTimeMillis(),
                field == null || field.isEmpty() ? 0L : System.currentTimeMillis()
        );
    }

    @NonNull
    public static RadarUiState loading(double latitude, double longitude) {
        return new RadarUiState(
                latitude,
                longitude,
                true,
                true,
                null,
                Collections.emptyList(),
                null,
                null,
                null,
                null,
                0L,
                0L
        );
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public boolean isLoadingRadar() { return loadingRadar; }
    public boolean isLoadingField() { return loadingField; }
    @Nullable public RainViewerResponse getRadar() { return radar; }
    @NonNull public List<RainViewerResponse.Frame> getObservedFrames() { return observedFrames; }
    @Nullable public String getSafeRadarHost() { return safeRadarHost; }
    public boolean isRadarMetadataStale() { return radarMetadataStale; }
    @NonNull public List<RadarFieldPointResponse> getField() { return field; }
    @Nullable public String getRadarError() { return radarError; }
    @Nullable public String getFieldError() { return fieldError; }
    @Nullable public RadarRepository.DeliverySource getRadarSource() { return radarSource; }
    @Nullable public RadarRepository.DeliverySource getFieldSource() { return fieldSource; }
    public long getRadarSavedAtMillis() { return radarSavedAtMillis; }
    public long getFieldSavedAtMillis() { return fieldSavedAtMillis; }

    public boolean isRadarFromCache() {
        return radarSource != null && radarSource.isCache();
    }

    public boolean isFieldFromCache() {
        return fieldSource != null && fieldSource.isCache();
    }

    public boolean isRadarFallback() {
        return radarSource != null && radarSource.isFallback();
    }

    public boolean isFieldFallback() {
        return fieldSource != null && fieldSource.isFallback();
    }

    public boolean isRadarNetworkFallback() {
        return radarSource != null && radarSource.isNetworkFallback();
    }

    public boolean isFieldNetworkFallback() {
        return fieldSource != null && fieldSource.isNetworkFallback();
    }

    public boolean isRadarServerFallback() {
        return radarSource != null && radarSource.isServerFallback();
    }

    public boolean isFieldServerFallback() {
        return fieldSource != null && fieldSource.isServerFallback();
    }

    public boolean hasRadarFrames() {
        return safeRadarHost != null && !observedFrames.isEmpty();
    }

    /** Open-Meteo points are model context, not observed radar imagery. */
    public boolean hasField() {
        return !field.isEmpty();
    }

    public long getLatestObservedAtMillis() {
        if (observedFrames.isEmpty()) return 0L;
        Long seconds = observedFrames.get(observedFrames.size() - 1).getTime();
        return seconds == null || seconds <= 0L ? 0L : seconds * 1000L;
    }

    public long getRadarObservationAgeMillis(long nowMillis) {
        return ageMillis(nowMillis, getLatestObservedAtMillis());
    }

    public long getRadarCacheAgeMillis(long nowMillis) {
        return ageMillis(nowMillis, radarSavedAtMillis);
    }

    public long getFieldAgeMillis(long nowMillis) {
        return ageMillis(nowMillis, fieldSavedAtMillis);
    }

    public boolean isRadarObservationDelayed(long nowMillis) {
        long age = getRadarObservationAgeMillis(nowMillis);
        return radarMetadataStale || (age >= 0L && age > RADAR_DELAYED_MILLIS);
    }

    public boolean isFieldDelayed(long nowMillis) {
        long age = getFieldAgeMillis(nowMillis);
        return age >= 0L && age > FIELD_DELAYED_MILLIS;
    }

    private long ageMillis(long nowMillis, long timestampMillis) {
        if (timestampMillis <= 0L) return -1L;
        return Math.max(0L, nowMillis - timestampMillis);
    }

    @NonNull
    public RadarUiState withRadar(
            @Nullable RainViewerResponse value,
            @Nullable String error,
            boolean fromCache
    ) {
        return new RadarUiState(
                latitude,
                longitude,
                false,
                loadingField,
                value,
                field,
                error,
                fieldError,
                value == null ? null : (fromCache
                        ? RadarRepository.DeliverySource.MEMORY_CACHE
                        : RadarRepository.DeliverySource.NETWORK),
                fieldSource,
                value == null ? 0L : System.currentTimeMillis(),
                fieldSavedAtMillis
        );
    }

    @NonNull
    public RadarUiState withField(
            @Nullable List<RadarFieldPointResponse> value,
            @Nullable String error,
            boolean fromCache
    ) {
        return new RadarUiState(
                latitude,
                longitude,
                loadingRadar,
                false,
                radar,
                value,
                radarError,
                error,
                radarSource,
                value == null || value.isEmpty() ? null : (fromCache
                        ? RadarRepository.DeliverySource.MEMORY_CACHE
                        : RadarRepository.DeliverySource.NETWORK),
                radarSavedAtMillis,
                value == null || value.isEmpty() ? 0L : System.currentTimeMillis()
        );
    }
}
