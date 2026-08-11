package com.tridev.liveweather.ui.air;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tridev.liveweather.data.local.AirQualityCache;
import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.domain.AirQualityUiState;
import com.tridev.liveweather.repository.AirQualityRepository;

import retrofit2.Call;

public final class AirQualityViewModel extends AndroidViewModel {

    private static final long REUSE_WINDOW_MILLIS = 15 * 60 * 1000L;
    private static final double SAME_AREA_DELTA = 0.01d;

    private final MutableLiveData<AirQualityUiState> state = new MutableLiveData<>();
    private final AirQualityRepository repository = new AirQualityRepository();
    private final AirQualityCache cache;
    private Call<AirQualityResponse> activeCall;

    public AirQualityViewModel(@NonNull Application application) {
        super(application);
        cache = new AirQualityCache(application);
        AirQualityCache.CachedAirQuality cached = cache.load();
        if (cached != null) {
            state.setValue(new AirQualityUiState(
                    false,
                    cached.getResponse(),
                    true,
                    "Showing saved air quality.",
                    cached.getSavedAt(),
                    cached.getLatitude(),
                    cached.getLongitude()
            ));
        } else {
            state.setValue(new AirQualityUiState(
                    false, null, false, null, 0L, Double.NaN, Double.NaN
            ));
        }
    }

    @NonNull
    public LiveData<AirQualityUiState> getState() {
        return state;
    }

    public void refresh(double latitude, double longitude, boolean force) {
        AirQualityUiState current = state.getValue();
        if (!force && canReuse(current, latitude, longitude)) {
            return;
        }
        if (activeCall != null) {
            activeCall.cancel();
        }

        AirQualityResponse existing = null;
        long existingTime = 0L;
        boolean fromCache = false;
        AirQualityCache.CachedAirQuality local = cache.load(latitude, longitude);
        if (local != null) {
            existing = local.getResponse();
            existingTime = local.getSavedAt();
            fromCache = true;
        } else if (sameArea(current, latitude, longitude) && current != null) {
            existing = current.getData();
            existingTime = current.getUpdatedAt();
            fromCache = current.isFromCache();
        }

        state.setValue(new AirQualityUiState(
                true,
                existing,
                fromCache,
                existing == null ? "Loading air quality…" : "Refreshing air quality…",
                existingTime,
                latitude,
                longitude
        ));

        activeCall = repository.loadAirQuality(latitude, longitude, new AirQualityRepository.CallbackResult() {
            @Override
            public void onSuccess(@NonNull AirQualityResponse response) {
                activeCall = null;
                long now = System.currentTimeMillis();
                cache.save(response, latitude, longitude, now);
                state.setValue(new AirQualityUiState(
                        false, response, false, null, now, latitude, longitude
                ));
            }

            @Override
            public void onError(@NonNull String message, Throwable throwable) {
                activeCall = null;
                AirQualityUiState latest = state.getValue();
                AirQualityResponse fallback = latest == null ? null : latest.getData();
                long fallbackTime = latest == null ? 0L : latest.getUpdatedAt();
                state.setValue(new AirQualityUiState(
                        false,
                        fallback,
                        fallback != null,
                        message,
                        fallbackTime,
                        latitude,
                        longitude
                ));
            }
        });
    }

    private boolean canReuse(AirQualityUiState current, double latitude, double longitude) {
        return current != null
                && current.hasData()
                && !current.isLoading()
                && !current.isFromCache()
                && sameArea(current, latitude, longitude)
                && System.currentTimeMillis() - current.getUpdatedAt() <= REUSE_WINDOW_MILLIS;
    }

    private boolean sameArea(AirQualityUiState current, double latitude, double longitude) {
        return current != null
                && !Double.isNaN(current.getLatitude())
                && !Double.isNaN(current.getLongitude())
                && Math.abs(current.getLatitude() - latitude) <= SAME_AREA_DELTA
                && Math.abs(current.getLongitude() - longitude) <= SAME_AREA_DELTA;
    }

    @Override
    protected void onCleared() {
        if (activeCall != null) {
            activeCall.cancel();
        }
        super.onCleared();
    }
}
