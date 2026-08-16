package com.tridev.liveweather.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tridev.liveweather.data.local.AlertCache;
import com.tridev.liveweather.data.local.AlertPreferences;
import com.tridev.liveweather.data.local.WeatherCache;
import com.tridev.liveweather.domain.alert.AlertLocation;
import com.tridev.liveweather.domain.alert.AlertMerger;
import com.tridev.liveweather.domain.alert.AlertTruthPolicy;
import com.tridev.liveweather.domain.alert.SmartAlertEngine;
import com.tridev.liveweather.domain.alert.WeatherAlert;
import com.tridev.liveweather.notification.AlertNotificationManager;
import com.tridev.liveweather.repository.CapAlertRepository;

import java.util.ArrayList;
import java.util.List;

public final class WeatherAlertRefreshWorker extends Worker {

    private static final long SMART_WEATHER_MAX_AGE_MILLIS = 90L * 60L * 1000L;

    public WeatherAlertRefreshWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams
    ) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        AlertPreferences preferences = new AlertPreferences(context);
        AlertNotificationManager notificationManager = new AlertNotificationManager(context);

        // The periodic worker exists only for background notification delivery.
        // Exit before cache parsing/network work when delivery is disabled or blocked.
        if (!preferences.isNotificationsEnabled()
                || !preferences.isAnyNotificationSourceEnabled()
                || !notificationManager.canPostNotifications()) {
            return Result.success();
        }

        WeatherCache.CachedWeather cachedWeather = new WeatherCache(context).load();
        if (cachedWeather == null || cachedWeather.getWeather() == null) {
            return Result.success();
        }

        try {
            long now = System.currentTimeMillis();
            long weatherAge = cachedWeather.getSavedAt() <= 0L
                    ? Long.MAX_VALUE
                    : Math.max(0L, now - cachedWeather.getSavedAt());

            boolean smartDeliveryEnabled = preferences.isSmartRiskEnabled()
                    && preferences.isSmartNotificationsEnabled()
                    && notificationManager.isSmartChannelEnabled();
            List<WeatherAlert> smart = smartDeliveryEnabled
                    && weatherAge <= SMART_WEATHER_MAX_AGE_MILLIS
                    ? SmartAlertEngine.build(cachedWeather.getWeather())
                    : new ArrayList<>();

            AlertLocation alertLocation = preferences.loadLocation();
            List<WeatherAlert> official = new ArrayList<>();
            AlertTruthPolicy.OfficialDelivery officialDelivery =
                    AlertTruthPolicy.OfficialDelivery.UNAVAILABLE;
            long officialSavedAt = 0L;

            boolean officialDeliveryEnabled = preferences.isOfficialAlertsEnabled()
                    && preferences.isOfficialNotificationsEnabled()
                    && notificationManager.isOfficialChannelEnabled();

            if (!officialDeliveryEnabled) {
                officialDelivery = AlertTruthPolicy.OfficialDelivery.NOT_APPLICABLE;
            } else if (alertLocation != null && !alertLocation.isIndia()) {
                officialDelivery = AlertTruthPolicy.OfficialDelivery.NOT_APPLICABLE;
            } else if (alertLocation != null
                    && close(alertLocation.getLatitude(), cachedWeather.getLatitude())
                    && close(alertLocation.getLongitude(), cachedWeather.getLongitude())) {
                AlertCache alertCache = new AlertCache(context);
                AlertCache.CachedAlerts cachedAlerts = alertCache.loadOfficial(
                        cachedWeather.getLatitude(),
                        cachedWeather.getLongitude()
                );
                try {
                    CapAlertRepository.Result capResult = new CapAlertRepository()
                            .loadImdAlertsBlocking(alertLocation, cachedAlerts.getEtag());
                    String etag = capResult.getEtag() == null
                            ? cachedAlerts.getEtag()
                            : capResult.getEtag();

                    official = capResult.isNotModified()
                            ? cachedAlerts.getAlerts()
                            : capResult.getAlerts();

                    // Both 200 and 304 are a current provider validation.
                    alertCache.saveOfficial(
                            cachedWeather.getLatitude(),
                            cachedWeather.getLongitude(),
                            official,
                            etag,
                            now
                    );
                    officialSavedAt = now;
                    officialDelivery = official.isEmpty()
                            ? AlertTruthPolicy.OfficialDelivery.NETWORK_EMPTY
                            : AlertTruthPolicy.OfficialDelivery.NETWORK;
                } catch (Exception ignored) {
                    official = cachedAlerts.getAlerts();
                    officialSavedAt = cachedAlerts.getSavedAt();
                    officialDelivery = officialSavedAt > 0L
                            ? AlertTruthPolicy.OfficialDelivery.CACHE
                            : AlertTruthPolicy.OfficialDelivery.UNAVAILABLE;
                }
            }

            List<WeatherAlert> merged = AlertMerger.merge(official, smart, now);
            List<WeatherAlert> candidates = AlertTruthPolicy.notificationCandidates(
                    merged,
                    officialDelivery,
                    officialSavedAt,
                    now
            );
            notificationManager.notifyNewAlerts(candidates);
            return Result.success();
        } catch (RuntimeException exception) {
            return Result.failure();
        }
    }

    private boolean close(double first, double second) {
        return Math.abs(first - second) <= 0.02d;
    }
}