package com.tridev.liveweather.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tridev.liveweather.core.DataReliabilityPolicy;
import com.tridev.liveweather.data.local.AirQualityCache;
import com.tridev.liveweather.data.local.WeatherCache;
import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.repository.AirQualityRepository;
import com.tridev.liveweather.repository.WeatherRepository;
import com.tridev.liveweather.widget.WeatherWidgetUpdater;

import java.io.IOException;

public final class WallpaperWeatherRefreshWorker extends Worker {

    public WallpaperWeatherRefreshWorker(
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
        WeatherCache.CachedWeather cached = weatherCache.load();
        if (cached == null) {
            WeatherWidgetUpdater.updateAll(context);
            return Result.success();
        }

        double latitude = cached.getLatitude();
        double longitude = cached.getLongitude();
        long now = System.currentTimeMillis();
        boolean remainedActive;

        try {
            WeatherResponse weather = new WeatherRepository().loadWeatherBlocking(latitude, longitude);
            remainedActive = weatherCache.saveIfStillActive(weather, latitude, longitude, now);
        } catch (IOException exception) {
            WeatherWidgetUpdater.updateAll(context);
            return DataReliabilityPolicy.shouldRetryBackground(getRunAttemptCount())
                    ? Result.retry()
                    : Result.success();
        } catch (RuntimeException exception) {
            WeatherWidgetUpdater.updateAll(context);
            return Result.failure();
        }

        try {
            AirQualityResponse airQuality = new AirQualityRepository()
                    .loadAirQualityBlocking(latitude, longitude);
            AirQualityCache airCache = new AirQualityCache(context);
            if (remainedActive) {
                airCache.save(airQuality, latitude, longitude, now);
            } else {
                airCache.saveSnapshot(airQuality, latitude, longitude, now);
            }
        } catch (IOException | RuntimeException ignored) {
            // Weather remains useful even if the separate CAMS AQI model is unavailable.
        }

        // Widgets and Live Wallpaper consume the same refreshed active cache.
        // If the user changed location during this worker, the old result was
        // stored only as a snapshot and cannot overwrite the new active identity.
        WeatherWidgetUpdater.updateAll(context);
        return Result.success();
    }
}
