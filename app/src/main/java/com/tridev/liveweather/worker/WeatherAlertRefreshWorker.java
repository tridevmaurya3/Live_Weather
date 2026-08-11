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
import com.tridev.liveweather.domain.alert.SmartAlertEngine;
import com.tridev.liveweather.domain.alert.WeatherAlert;
import com.tridev.liveweather.notification.AlertNotificationManager;
import com.tridev.liveweather.repository.CapAlertRepository;
import com.tridev.liveweather.repository.WeatherRepository;

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
        WeatherCache weatherCache = new WeatherCache(context);
        WeatherCache.CachedWeather cachedWeather = weatherCache.load();
        if (cachedWeather == null) return Result.success();

        try {
            WeatherRepository weatherRepository = new WeatherRepository();
            com.tridev.liveweather.data.remote.dto.WeatherResponse weather =
                    weatherRepository.loadWeatherBlocking(
                            cachedWeather.getLatitude(),
                            cachedWeather.getLongitude()
                    );
            long now = System.currentTimeMillis();
            weatherCache.save(
                    weather,
                    cachedWeather.getLatitude(),
                    cachedWeather.getLongitude(),
                    now
            );

            List<WeatherAlert> smart = SmartAlertEngine.build(weather);
            AlertPreferences preferences = new AlertPreferences(context);
            AlertLocation alertLocation = preferences.loadLocation();
            List<WeatherAlert> official = new ArrayList<>();

            if (alertLocation != null
                    && alertLocation.isIndia()
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
                    if (capResult.isNotModified()) {
                        official = cachedAlerts.getAlerts();
                    } else {
                        official = capResult.getAlerts();
                        alertCache.saveOfficial(
                                cachedWeather.getLatitude(),
                                cachedWeather.getLongitude(),
                                official,
                                capResult.getEtag(),
                                now
                        );
                    }
                } catch (Exception ignored) {
                    official = cachedAlerts.getAlerts();
                }
            }

            List<WeatherAlert> merged = AlertMerger.merge(official, smart, now);
            new AlertNotificationManager(context).notifyNewAlerts(merged);
            return Result.success();
        } catch (java.io.IOException exception) {
            return Result.retry();
        } catch (RuntimeException exception) {
            return Result.failure();
        }
    }

    private boolean close(double first, double second) {
        return Math.abs(first - second) <= 0.02d;
    }
}
