package com.tridev.liveweather.ui.weather;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tridev.liveweather.data.local.WeatherCache;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.WeatherUiState;
import com.tridev.liveweather.repository.WeatherRepository;

import retrofit2.Call;

/**
 * Shared Phase 2 weather state holder.
 *
 * It survives Activity recreation, exposes cached data immediately and keeps
 * the app on one weather request/state source instead of separate requests per screen.
 */
public final class WeatherViewModel extends AndroidViewModel {

    private static final long LIVE_REUSE_WINDOW_MILLIS = 5 * 60 * 1000L;
    private static final double COORDINATE_REUSE_DELTA = 0.01d;

    private final MutableLiveData<WeatherUiState> weatherState = new MutableLiveData<>();
    private final WeatherRepository weatherRepository;
    private final WeatherCache weatherCache;

    private Call<WeatherResponse> activeCall;

    public WeatherViewModel(@NonNull Application application) {
        super(application);
        weatherRepository = new WeatherRepository();
        weatherCache = new WeatherCache(application);

        WeatherCache.CachedWeather cachedWeather = weatherCache.load();
        if (cachedWeather != null) {
            weatherState.setValue(new WeatherUiState(
                    false,
                    cachedWeather.getWeather(),
                    true,
                    "Showing last saved weather while live data refreshes.",
                    cachedWeather.getSavedAt(),
                    cachedWeather.getLatitude(),
                    cachedWeather.getLongitude()
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
    }

    @NonNull
    public LiveData<WeatherUiState> getWeatherState() {
        return weatherState;
    }

    public void refreshWeather(double latitude, double longitude, boolean force) {
        WeatherUiState currentState = weatherState.getValue();

        if (!force && canReuseLiveState(currentState, latitude, longitude)) {
            return;
        }

        if (activeCall != null) {
            activeCall.cancel();
            activeCall = null;
        }

        WeatherResponse existingWeather = currentState != null
                ? currentState.getWeather()
                : null;
        long existingUpdatedAt = currentState != null
                ? currentState.getUpdatedAt()
                : 0L;
        boolean existingFromCache = currentState != null && currentState.isFromCache();

        weatherState.setValue(new WeatherUiState(
                true,
                existingWeather,
                existingFromCache,
                "Updating live weather…",
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
                        activeCall = null;
                        long now = System.currentTimeMillis();
                        weatherCache.save(weatherResponse, latitude, longitude, now);
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
                    public void onError(
                            @NonNull String message,
                            Throwable throwable
                    ) {
                        activeCall = null;
                        WeatherUiState latestState = weatherState.getValue();
                        WeatherResponse fallbackWeather = latestState != null
                                ? latestState.getWeather()
                                : null;
                        long fallbackUpdatedAt = latestState != null
                                ? latestState.getUpdatedAt()
                                : 0L;

                        weatherState.setValue(new WeatherUiState(
                                false,
                                fallbackWeather,
                                fallbackWeather != null,
                                message,
                                fallbackUpdatedAt,
                                latitude,
                                longitude
                        ));
                    }
                }
        );
    }

    private boolean canReuseLiveState(
            WeatherUiState state,
            double latitude,
            double longitude
    ) {
        if (state == null || state.isLoading() || !state.hasWeather() || state.isFromCache()) {
            return false;
        }

        if (Double.isNaN(state.getLatitude()) || Double.isNaN(state.getLongitude())) {
            return false;
        }

        boolean sameArea = Math.abs(state.getLatitude() - latitude) <= COORDINATE_REUSE_DELTA
                && Math.abs(state.getLongitude() - longitude) <= COORDINATE_REUSE_DELTA;
        boolean recent = System.currentTimeMillis() - state.getUpdatedAt()
                <= LIVE_REUSE_WINDOW_MILLIS;

        return sameArea && recent;
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
