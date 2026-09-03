package com.tridev.liveweather.ui.weather;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tridev.liveweather.core.DataReliabilityPolicy;
import com.tridev.liveweather.core.LiveDataFreshnessPolicy;
import com.tridev.liveweather.data.local.ActiveWeatherSnapshotStore;
import com.tridev.liveweather.data.local.SavedCityStore;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.ActiveWeatherSnapshot;
import com.tridev.liveweather.domain.CityLocation;
import com.tridev.liveweather.domain.WeatherUiState;
import com.tridev.liveweather.repository.WeatherRepository;
import com.tridev.liveweather.widget.WeatherWidgetUpdater;

import retrofit2.Call;

/** Shared weather state holder for current location and saved cities. */
public final class WeatherViewModel extends AndroidViewModel {

    private static final long LIVE_REUSE_WINDOW_MILLIS = 2 * 60 * 1000L;

    private final MutableLiveData<WeatherUiState> weatherState = new MutableLiveData<>();
    private final WeatherRepository weatherRepository;
    private final ActiveWeatherSnapshotStore activeSnapshotStore;
    private final SavedCityStore savedCityStore;

    private Call<WeatherResponse> activeCall;

    public WeatherViewModel(@NonNull Application application) {
        super(application);
        weatherRepository = new WeatherRepository();
        activeSnapshotStore = new ActiveWeatherSnapshotStore(application);
        savedCityStore = new SavedCityStore(application);

        CityLocation selectedCity = savedCityStore.getSelectedCity();
        if (selectedCity != null) {
            activeSnapshotStore.ensureActiveTarget(
                    selectedCity.getLatitude(),
                    selectedCity.getLongitude(),
                    selectedCity.getDisplayName()
            );
        }

        long now = System.currentTimeMillis();
        ActiveWeatherSnapshot snapshot = activeSnapshotStore.loadActive(now);
        if (snapshot != null) {
            weatherState.setValue(fromSnapshot(
                    snapshot,
                    false,
                    DataReliabilityPolicy.weatherCacheMessage(snapshot.getFetchedAt(), now)
            ));
        } else if (selectedCity != null) {
            weatherState.setValue(new WeatherUiState(
                    false,
                    null,
                    false,
                    "No saved weather for the selected city. Waiting for live refresh.",
                    0L,
                    selectedCity.getLatitude(),
                    selectedCity.getLongitude()
            ));
        } else {
            weatherState.setValue(new WeatherUiState(
                    false,
                    null,
                    false,
                    null,
                    0L,
                    Double.NaN,
                    Double.NaN
            ));
        }

        WeatherWidgetUpdater.updateAll(application);
    }

    @NonNull
    public LiveData<WeatherUiState> getWeatherState() {
        return weatherState;
    }

    public void refreshWeather(double latitude, double longitude, boolean force) {
        refreshWeatherInternal(latitude, longitude, force, true);
    }

    /**
     * Refreshes an unchanged foreground location only when its adaptive
     * freshness window has elapsed. An existing request is allowed to finish
     * instead of being cancelled and restarted by the minute ticker.
     */
    public boolean refreshWeatherIfDue(double latitude, double longitude, long now) {
        WeatherUiState currentState = weatherState.getValue();
        if (currentState != null && !isSameArea(currentState, latitude, longitude)) {
            return false;
        }
        long interval = LiveDataFreshnessPolicy.refreshIntervalMillis(
                currentState == null ? null : currentState.getWeather()
        );
        if (!LiveDataFreshnessPolicy.shouldRefresh(
                currentState == null ? 0L : currentState.getUpdatedAt(),
                now,
                interval,
                activeCall != null || (currentState != null && currentState.isLoading())
        )) {
            return false;
        }
        refreshWeatherInternal(latitude, longitude, true, false);
        return true;
    }

    private void refreshWeatherInternal(
            double latitude,
            double longitude,
            boolean force,
            boolean replaceActiveRequest
    ) {
        WeatherUiState currentState = weatherState.getValue();
        if (!force && canReuseLiveState(currentState, latitude, longitude)) return;

        if (activeCall != null) {
            if (!replaceActiveRequest) return;
            activeCall.cancel();
            activeCall = null;
        }

        ActiveWeatherSnapshotStore.RequestToken requestToken = activeSnapshotStore.beginRequest(
                latitude,
                longitude,
                resolveLocationLabel(latitude, longitude)
        );
        WeatherWidgetUpdater.updateAll(getApplication());

        WeatherResponse existingWeather = null;
        long existingUpdatedAt = 0L;
        boolean existingFromCache = false;

        if (isSameArea(currentState, latitude, longitude) && currentState != null) {
            existingWeather = currentState.getWeather();
            existingUpdatedAt = currentState.getUpdatedAt();
            existingFromCache = currentState.isFromCache();
        } else {
            ActiveWeatherSnapshot targetSnapshot = activeSnapshotStore.loadActive(
                    System.currentTimeMillis()
            );
            if (targetSnapshot != null) {
                existingWeather = targetSnapshot.getWeather();
                existingUpdatedAt = targetSnapshot.getFetchedAt();
                existingFromCache = true;
            }
        }

        String loadingMessage;
        if (existingWeather == null) {
            loadingMessage = "Loading live weather…";
        } else if (existingFromCache) {
            loadingMessage = DataReliabilityPolicy.weatherCacheMessage(
                    existingUpdatedAt,
                    System.currentTimeMillis()
            ) + " Refreshing live data…";
        } else {
            loadingMessage = "Refreshing live weather…";
        }

        weatherState.setValue(new WeatherUiState(
                true,
                existingWeather,
                existingFromCache,
                loadingMessage,
                existingUpdatedAt,
                latitude,
                longitude
        ));

        activeCall = weatherRepository.loadWeather(
                latitude,
                longitude,
                new WeatherRepository.WeatherCallback() {
                    @Override
                    public void onSuccess(@NonNull WeatherResponse weatherResponse) {
                        long now = System.currentTimeMillis();
                        if (!activeSnapshotStore.commitIfCurrent(
                                requestToken,
                                weatherResponse,
                                now
                        )) {
                            return;
                        }

                        activeCall = null;
                        WeatherWidgetUpdater.updateAll(getApplication());
                        weatherState.setValue(new WeatherUiState(
                                false,
                                weatherResponse,
                                false,
                                null,
                                now,
                                latitude,
                                longitude
                        ));
                    }

                    @Override
                    public void onError(@NonNull String message, Throwable throwable) {
                        if (!activeSnapshotStore.isCurrent(requestToken)) return;

                        activeCall = null;
                        WeatherUiState latestState = weatherState.getValue();
                        boolean sameRequestedArea = isSameArea(latestState, latitude, longitude);
                        WeatherResponse fallbackWeather = sameRequestedArea && latestState != null
                                ? latestState.getWeather()
                                : null;
                        long fallbackUpdatedAt = sameRequestedArea && latestState != null
                                ? latestState.getUpdatedAt()
                                : 0L;
                        String reliabilityMessage = fallbackWeather == null
                                ? message
                                : message + " " + DataReliabilityPolicy.weatherCacheMessage(
                                        fallbackUpdatedAt,
                                        System.currentTimeMillis()
                                );

                        weatherState.setValue(new WeatherUiState(
                                false,
                                fallbackWeather,
                                fallbackWeather != null,
                                reliabilityMessage,
                                fallbackUpdatedAt,
                                latitude,
                                longitude
                        ));
                    }
                }
        );
    }

    private WeatherUiState fromSnapshot(
            @NonNull ActiveWeatherSnapshot snapshot,
            boolean loading,
            @Nullable String message
    ) {
        return new WeatherUiState(
                loading,
                snapshot.getWeather(),
                true,
                message,
                snapshot.getFetchedAt(),
                snapshot.getLatitude(),
                snapshot.getLongitude()
        );
    }

    @NonNull
    private String resolveLocationLabel(double latitude, double longitude) {
        CityLocation selectedCity = savedCityStore.getSelectedCity();
        if (selectedCity != null && DataReliabilityPolicy.sameLocation(
                selectedCity.getLatitude(),
                selectedCity.getLongitude(),
                latitude,
                longitude
        )) {
            return selectedCity.getDisplayName();
        }
        return "Current location";
    }

    private boolean canReuseLiveState(
            WeatherUiState state,
            double latitude,
            double longitude
    ) {
        if (state == null || state.isLoading() || !state.hasWeather() || state.isFromCache()) {
            return false;
        }
        boolean sameArea = isSameArea(state, latitude, longitude);
        boolean recent = System.currentTimeMillis() - state.getUpdatedAt()
                <= LIVE_REUSE_WINDOW_MILLIS;
        return sameArea && recent;
    }

    private boolean isSameArea(
            WeatherUiState state,
            double latitude,
            double longitude
    ) {
        return state != null && DataReliabilityPolicy.sameLocation(
                state.getLatitude(),
                state.getLongitude(),
                latitude,
                longitude
        );
    }

    @Override
    protected void onCleared() {
        if (activeCall != null) {
            activeCall.cancel();
            activeCall = null;
        }
        super.onCleared();
    }
}
