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
        WeatherCache.CachedWeather cachedWeather = new WeatherCache(context).load();
        if (cachedWeather == null || cachedWeather.getWeather() == null) {
            return Result.success();
        }

        try {
            long now = System.currentTimeMillis();
            List<WeatherAlert> smart = SmartAlertEngine.build(cachedWeather.getWeather());
            AlertPreferences preferences = new AlertPreferences(context);
            AlertLocation alertLocation = preferences.loadLocation();
            List<WeatherAlert> official = new ArrayList<>();
            AlertTruthPolicy.OfficialDelivery officialDelivery =
                    AlertTruthPolicy.OfficialDelivery.UNAVAILABLE;
            long officialSavedAt = 0L;

            if (alertLocation != null && !alertLocation.isIndia()) {
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

                    if (capResult.isNotModified()) {
                        official = cachedAlerts.getAlerts();
                    } else {
                        official = capResult.getAlerts();
                    }

                    // A successful 200 or 304 is a fresh validation of the
                    // provider state. Save the validation time even if alert
                    // content itself did not change.
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
            new AlertNotificationManager(context).notifyNewAlerts(candidates);
            return Result.success();
        } catch (RuntimeException exception) {
            return Result.failure();
        }
    }

    private boolean close(double first, double second) {
        return Math.abs(first - second) <= 0.02d;
    }
}
