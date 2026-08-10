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
 * Shared weather state holder for current location and saved cities.
 */
public final class WeatherViewModel extends AndroidViewModel {

    // Shorter foreground reuse window helps local shower changes appear sooner.
    private static final long LIVE_REUSE_WINDOW_MILLIS = 2 * 60 * 1000L;
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
            weatherState.setValue(fromCache(cachedWeather, false, "Showing last saved weather."));
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

        WeatherResponse existingWeather = null;
        long existingUpdatedAt = 0L;
        boolean existingFromCache = false;

        if (isSameArea(currentState, latitude, longitude) && currentState != null) {
            existingWeather = currentState.getWeather();
            existingUpdatedAt = currentState.getUpdatedAt();
            existingFromCache = currentState.isFromCache();
        } else {
            WeatherCache.CachedWeather targetCache = weatherCache.load(latitude, longitude);
            if (targetCache != null) {
                existingWeather = targetCache.getWeather();
                existingUpdatedAt = targetCache.getSavedAt();
                existingFromCache = true;
            }
        }

        weatherState.setValue(new WeatherUiState(
                true,
                existingWeather,
                existingFromCache,
                existingWeather == null
                        ? "Loading live weather…"
                        : "Showing saved weather while live data refreshes…",
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
                    public void onError(@NonNull String message, Throwable throwable) {
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

    private WeatherUiState fromCache(
            @NonNull WeatherCache.CachedWeather cachedWeather,
            boolean loading,
            String message
    ) {
        return new WeatherUiState(
                loading,
                cachedWeather.getWeather(),
                true,
                message,
                cachedWeather.getSavedAt(),
                cachedWeather.getLatitude(),
                cachedWeather.getLongitude()
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
        if (state == null
                || Double.isNaN(state.getLatitude())
                || Double.isNaN(state.getLongitude())) {
            return false;
        }
        return Math.abs(state.getLatitude() - latitude) <= COORDINATE_REUSE_DELTA
                && Math.abs(state.getLongitude() - longitude) <= COORDINATE_REUSE_DELTA;
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
