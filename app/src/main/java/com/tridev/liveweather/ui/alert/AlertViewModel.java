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
import com.tridev.liveweather.domain.alert.AlertTruthPolicy;
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
                0L,
                AlertTruthPolicy.OfficialDelivery.UNAVAILABLE
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
        final AlertTruthPolicy.OfficialDelivery cachedDelivery = cached.getSavedAt() > 0L
                ? AlertTruthPolicy.OfficialDelivery.CACHE
                : AlertTruthPolicy.OfficialDelivery.UNAVAILABLE;

        state.setValue(new AlertUiState(
                true,
                AlertMerger.merge(cached.getAlerts(), smart, now),
                preferences.loadLocation(),
                cached.getSavedAt() > 0L
                        ? "Refreshing official warning source while saved data remains visible."
                        : "Checking official weather warnings…",
                now,
                cached.getSavedAt(),
                cachedDelivery
        ));

        locationResolver.resolve(latitude, longitude, location -> {
            if (requestGeneration != generation) return;
            preferences.saveLocation(location);

            if (!location.isIndia()) {
                long resolvedAt = System.currentTimeMillis();
                List<WeatherAlert> merged = AlertMerger.merge(
                        new ArrayList<>(),
                        smart,
                        resolvedAt
                );
                AlertUiState resolved = new AlertUiState(
                        false,
                        merged,
                        location,
                        "Official IMD warning scope applies to India locations; Smart Risk remains separate and active here.",
                        resolvedAt,
                        0L,
                        AlertTruthPolicy.OfficialDelivery.NOT_APPLICABLE
                );
                state.postValue(resolved);
                notifyTruthfulCandidates(resolved, resolvedAt);
                return;
            }

            long resolvedAt = System.currentTimeMillis();
            boolean cacheFresh = AlertTruthPolicy.isOfficialCacheFresh(
                    cached.getSavedAt(),
                    resolvedAt
            );
            if (!force && cacheFresh) {
                List<WeatherAlert> merged = AlertMerger.merge(
                        cached.getAlerts(),
                        smart,
                        resolvedAt
                );
                AlertUiState resolved = new AlertUiState(
                        false,
                        merged,
                        location,
                        "Recent saved official warning check is still within the reuse window.",
                        resolvedAt,
                        cached.getSavedAt(),
                        AlertTruthPolicy.OfficialDelivery.CACHE
                );
                state.postValue(resolved);
                notifyTruthfulCandidates(resolved, resolvedAt);
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

            long now = System.currentTimeMillis();
            List<WeatherAlert> official;
            String etag = result.getEtag() == null ? cached.getEtag() : result.getEtag();

            if (result.isNotModified()) {
                official = cached.getAlerts();
                // A 304 confirms that the provider's current representation has
                // not changed. Refresh the validation timestamp without changing
                // alert content so the UI does not falsely display an old age.
                cache.saveOfficial(
                        location.getLatitude(),
                        location.getLongitude(),
                        official,
                        etag,
                        now
                );
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
            AlertTruthPolicy.OfficialDelivery delivery = official.isEmpty()
                    ? AlertTruthPolicy.OfficialDelivery.NETWORK_EMPTY
                    : AlertTruthPolicy.OfficialDelivery.NETWORK;
            AlertUiState resolved = new AlertUiState(
                    false,
                    merged,
                    location,
                    result.isNotModified()
                            ? "Official warning source checked · alert content unchanged."
                            : "Official warning source synchronized.",
                    now,
                    now,
                    delivery
            );
            state.postValue(resolved);
            notifyTruthfulCandidates(resolved, now);
        } catch (Exception exception) {
            if (requestGeneration != generation) return;
            long now = System.currentTimeMillis();
            List<WeatherAlert> merged = AlertMerger.merge(cached.getAlerts(), smart, now);
            AlertTruthPolicy.OfficialDelivery delivery = cached.getSavedAt() > 0L
                    ? AlertTruthPolicy.OfficialDelivery.CACHE
                    : AlertTruthPolicy.OfficialDelivery.UNAVAILABLE;
            boolean stale = AlertTruthPolicy.isOfficialSourceStale(
                    delivery,
                    cached.getSavedAt(),
                    now
            );
            AlertUiState resolved = new AlertUiState(
                    false,
                    merged,
                    location,
                    cached.getSavedAt() <= 0L
                            ? "Official warning source unavailable · Smart Risk only. This is not an official all-clear."
                            : stale
                            ? "Official refresh unavailable · saved official warnings are stale and shown only as fallback."
                            : "Official refresh unavailable · recent saved official warnings remain visible.",
                    now,
                    cached.getSavedAt(),
                    delivery
            );
            state.postValue(resolved);
            notifyTruthfulCandidates(resolved, now);
        }
    }

    private void notifyTruthfulCandidates(@NonNull AlertUiState resolved, long nowMillis) {
        notificationManager.notifyNewAlerts(
                AlertTruthPolicy.notificationCandidates(
                        resolved.getAlerts(),
                        resolved.getOfficialDelivery(),
                        resolved.getOfficialSavedAt(),
                        nowMillis
                )
        );
    }

    @Override
    protected void onCleared() {
        generation++;
        executor.shutdownNow();
        super.onCleared();
    }
}
