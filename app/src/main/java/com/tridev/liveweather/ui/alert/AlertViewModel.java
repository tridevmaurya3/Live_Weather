package com.tridev.liveweather.ui.alert;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tridev.liveweather.core.location.AlertLocationResolver;
import com.tridev.liveweather.data.local.AlertCache;
import com.tridev.liveweather.data.local.AlertPreferences;
import com.tridev.liveweather.domain.WeatherUiState;
import com.tridev.liveweather.domain.alert.AlertLocation;
import com.tridev.liveweather.domain.alert.AlertMerger;
import com.tridev.liveweather.domain.alert.AlertUiState;
import com.tridev.liveweather.domain.alert.SmartAlertEngine;
import com.tridev.liveweather.domain.alert.WeatherAlert;
import com.tridev.liveweather.notification.AlertNotificationManager;
import com.tridev.liveweather.repository.CapAlertRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AlertViewModel extends AndroidViewModel {

    private static final long OFFICIAL_REUSE_MILLIS = 10 * 60 * 1000L;

    private final MutableLiveData<AlertUiState> state = new MutableLiveData<>();
    private final AlertLocationResolver locationResolver;
    private final AlertCache cache;
    private final AlertPreferences preferences;
    private final CapAlertRepository capRepository = new CapAlertRepository();
    private final AlertNotificationManager notificationManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int generation;

    public AlertViewModel(@NonNull Application application) {
        super(application);
        locationResolver = new AlertLocationResolver(application);
        cache = new AlertCache(application);
        preferences = new AlertPreferences(application);
        notificationManager = new AlertNotificationManager(application);
        state.setValue(new AlertUiState(
                false,
                new ArrayList<>(),
                null,
                "Waiting for active weather location.",
                0L,
                false
        ));
    }

    @NonNull
    public LiveData<AlertUiState> getState() {
        return state;
    }

    public void refresh(@NonNull WeatherUiState weatherState, boolean force) {
        if (!weatherState.hasWeather() || weatherState.getWeather() == null
                || Double.isNaN(weatherState.getLatitude())
                || Double.isNaN(weatherState.getLongitude())) {
            return;
        }

        final int requestGeneration = ++generation;
        final double latitude = weatherState.getLatitude();
        final double longitude = weatherState.getLongitude();
        final List<WeatherAlert> smart = SmartAlertEngine.build(weatherState.getWeather());
        final AlertCache.CachedAlerts cached = cache.loadOfficial(latitude, longitude);
        final long now = System.currentTimeMillis();

        state.setValue(new AlertUiState(
                true,
                AlertMerger.merge(cached.getAlerts(), smart, now),
                preferences.loadLocation(),
                cached.getAlerts().isEmpty() ? "Checking official weather alerts…" : "Refreshing official alerts…",
                Math.max(cached.getSavedAt(), now),
                !cached.getAlerts().isEmpty()
        ));

        locationResolver.resolve(latitude, longitude, location -> {
            if (requestGeneration != generation) return;
            preferences.saveLocation(location);

            if (!location.isIndia()) {
                List<WeatherAlert> merged = AlertMerger.merge(
                        new ArrayList<>(),
                        smart,
                        System.currentTimeMillis()
                );
                AlertUiState resolved = new AlertUiState(
                        false,
                        merged,
                        location,
                        "Official IMD CAP warnings apply to India locations; Smart Risk remains active here.",
                        System.currentTimeMillis(),
                        false
                );
                // Geocoder callbacks are allowed to arrive on a Binder/background thread.
                // MutableLiveData.setValue() is main-thread only, so all resolver callback
                // branches use postValue() to remain thread-safe.
                state.postValue(resolved);
                notificationManager.notifyNewAlerts(merged);
                return;
            }

            boolean cacheFresh = cached.getSavedAt() > 0L
                    && System.currentTimeMillis() - cached.getSavedAt() <= OFFICIAL_REUSE_MILLIS;
            if (!force && cacheFresh) {
                List<WeatherAlert> merged = AlertMerger.merge(
                        cached.getAlerts(), smart, System.currentTimeMillis()
                );
                AlertUiState resolved = new AlertUiState(
                        false,
                        merged,
                        location,
                        "Official IMD CAP cache is fresh.",
                        cached.getSavedAt(),
                        true
                );
                state.postValue(resolved);
                notificationManager.notifyNewAlerts(merged);
                return;
            }

            executor.execute(() -> fetchOfficial(
                    requestGeneration,
                    location,
                    cached,
                    smart
            ));
        });
    }

    private void fetchOfficial(
            int requestGeneration,
            @NonNull AlertLocation location,
            @NonNull AlertCache.CachedAlerts cached,
            @NonNull List<WeatherAlert> smart
    ) {
        try {
            CapAlertRepository.Result result = capRepository.loadImdAlertsBlocking(
                    location,
                    cached.getEtag()
            );
            if (requestGeneration != generation) return;

            List<WeatherAlert> official;
            String etag = result.getEtag() == null ? cached.getEtag() : result.getEtag();
            long now = System.currentTimeMillis();
            if (result.isNotModified()) {
                official = cached.getAlerts();
            } else {
                official = result.getAlerts();
                cache.saveOfficial(
                        location.getLatitude(),
                        location.getLongitude(),
                        official,
                        etag,
                        now
                );
            }

            List<WeatherAlert> merged = AlertMerger.merge(official, smart, now);
            boolean officialAvailable = result.isNotModified()
                    ? !cached.getAlerts().isEmpty()
                    : true;
            AlertUiState resolved = new AlertUiState(
                    false,
                    merged,
                    location,
                    result.isNotModified()
                            ? "IMD CAP feed unchanged · cached official alerts retained."
                            : "Official IMD CAP feed synchronized.",
                    result.isNotModified() ? cached.getSavedAt() : now,
                    officialAvailable
            );
            state.postValue(resolved);
            notificationManager.notifyNewAlerts(merged);
        } catch (Exception exception) {
            if (requestGeneration != generation) return;
            long now = System.currentTimeMillis();
            List<WeatherAlert> merged = AlertMerger.merge(cached.getAlerts(), smart, now);
            state.postValue(new AlertUiState(
                    false,
                    merged,
                    location,
                    cached.getAlerts().isEmpty()
                            ? "Official CAP feed unavailable · showing Smart Risk only."
                            : "Official CAP refresh unavailable · showing saved official alerts + Smart Risk.",
                    cached.getSavedAt(),
                    !cached.getAlerts().isEmpty()
            ));
            notificationManager.notifyNewAlerts(merged);
        }
    }

    @Override
    protected void onCleared() {
        generation++;
        executor.shutdownNow();
        super.onCleared();
    }
}
