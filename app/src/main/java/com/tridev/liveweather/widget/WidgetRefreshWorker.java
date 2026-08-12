package com.tridev.liveweather.widget;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tridev.liveweather.data.local.AirQualityCache;
import com.tridev.liveweather.data.local.WeatherCache;
import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.repository.AirQualityRepository;
import com.tridev.liveweather.repository.WeatherRepository;

import java.io.IOException;

/**
 * One-shot refresh used by widget refresh buttons and first placement.
 * Network work never runs in AppWidgetProvider.onReceive().
 */
public final class WidgetRefreshWorker extends Worker {

    private static final String UNIQUE_WORK = "live_weather_widget_manual_refresh";

    public WidgetRefreshWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams
    ) {
        super(appContext, workerParams);
    }

    public static void enqueue(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WidgetRefreshWorker.class)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        WeatherCache weatherCache = new WeatherCache(context);
        WeatherCache.CachedWeather cached = weatherCache.load();

        if (cached == null) {
            WeatherWidgetUpdater.updateAll(context);
            return Result.success();
        }

        double latitude = cached.getLatitude();
        double longitude = cached.getLongitude();
        long now = System.currentTimeMillis();

        try {
            WeatherResponse weather = new WeatherRepository().loadWeatherBlocking(latitude, longitude);
            weatherCache.save(weather, latitude, longitude, now);
        } catch (IOException exception) {
            WeatherWidgetUpdater.updateAll(context);
            return Result.retry();
        } catch (RuntimeException exception) {
            WeatherWidgetUpdater.updateAll(context);
            return Result.failure();
        }

        try {
            AirQualityResponse air = new AirQualityRepository()
                    .loadAirQualityBlocking(latitude, longitude);
            new AirQualityCache(context).save(air, latitude, longitude, now);
        } catch (IOException | RuntimeException ignored) {
            // Widgets do not require AQI; weather remains valid.
        }

        WeatherWidgetUpdater.updateAll(context);
        return Result.success();
    }
}
