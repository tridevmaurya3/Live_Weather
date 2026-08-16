package com.tridev.liveweather.ui.air;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tridev.liveweather.core.DataReliabilityPolicy;
import com.tridev.liveweather.data.local.AirQualityCache;
import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.domain.AirQualityUiState;
import com.tridev.liveweather.repository.AirQualityRepository;

import retrofit2.Call;

public final class AirQualityViewModel extends AndroidViewModel {

    private static final long REUSE_WINDOW_MILLIS = 15 * 60 * 1000L;

    private final MutableLiveData<AirQualityUiState> state = new MutableLiveData<>();
    private final AirQualityRepository repository = new AirQualityRepository();
    private final AirQualityCache cache;
    private Call<AirQualityResponse> activeCall;

    public AirQualityViewModel(@NonNull Application application) {
        super(application);
        cache = new AirQualityCache(application);
        state.setValue(new AirQualityUiState(
                false,
                null,
                false,
                "Waiting for active weather location.",
                0L,
                Double.NaN,
                Double.NaN
        ));
    }

    @NonNull
    public LiveData<AirQualityUiState> getState() {
        return state;
    }

    public void refresh(double latitude, double longitude, boolean force) {
        AirQualityUiState current = state.getValue();
        if (!force && canReuse(current, latitude, longitude)) return;
        if (activeCall != null) activeCall.cancel();

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

        String loadingMessage = existing == null
                ? "Loading air quality…"
                : fromCache
                ? "Refreshing air quality · saved data "
                        + DataReliabilityPolicy.ageLabel(existingTime, System.currentTimeMillis()) + "."
                : "Refreshing air quality…";

        state.setValue(new AirQualityUiState(
                true,
                existing,
                fromCache,
                loadingMessage,
                existingTime,
                latitude,
                longitude
        ));

        activeCall = repository.loadAirQuality(
                latitude,
                longitude,
                new AirQualityRepository.CallbackResult() {
                    @Override
                    public void onSuccess(@NonNull AirQualityResponse response) {
                        activeCall = null;
                        long now = System.currentTimeMillis();
                        cache.save(response, latitude, longitude, now);
                        state.setValue(new AirQualityUiState(
                                false,
                                response,
                                false,
                                null,
                                now,
                                latitude,
                                longitude
                        ));
                    }

                    @Override
                    public void onError(@NonNull String message, Throwable throwable) {
                        activeCall = null;
                        AirQualityUiState latest = state.getValue();
                        boolean sameRequestedArea = sameArea(latest, latitude, longitude);
                        AirQualityResponse fallback = sameRequestedArea && latest != null
                                ? latest.getData()
                                : null;
                        long fallbackTime = sameRequestedArea && latest != null
                                ? latest.getUpdatedAt()
                                : 0L;
                        String reliabilityMessage = fallback == null
                                ? message
                                : message + " Showing saved AQI · "
                                        + DataReliabilityPolicy.ageLabel(
                                                fallbackTime,
                                                System.currentTimeMillis()
                                        ) + ".";

                        state.setValue(new AirQualityUiState(
                                false,
                                fallback,
                                fallback != null,
                                reliabilityMessage,
                                fallbackTime,
                                latitude,
                                longitude
                        ));
                    }
                }
        );
    }

    private boolean canReuse(
            AirQualityUiState current,
            double latitude,
            double longitude
    ) {
        return current != null
                && current.hasData()
                && !current.isLoading()
                && !current.isFromCache()
                && sameArea(current, latitude, longitude)
                && System.currentTimeMillis() - current.getUpdatedAt() <= REUSE_WINDOW_MILLIS;
    }

    private boolean sameArea(
            AirQualityUiState current,
            double latitude,
            double longitude
    ) {
        return current != null && DataReliabilityPolicy.sameLocation(
                current.getLatitude(),
                current.getLongitude(),
                latitude,
                longitude
        );
    }

    @Override
    protected void onCleared() {
        if (activeCall != null) activeCall.cancel();
        super.onCleared();
    }
}
