package com.tridev.liveweather.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tridev.liveweather.data.local.WeatherCache;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.repository.WeatherRepository;

import java.io.IOException;

/**
 * Refreshes the most recently active weather coordinates for the live wallpaper.
 * It deliberately does not request background GPS; the app updates coordinates
 * when foreground location access is available.
 */
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
        WeatherCache cache = new WeatherCache(getApplicationContext());
        WeatherCache.CachedWeather cached = cache.load();
        if (cached == null) {
            return Result.success();
        }

        try {
            WeatherRepository repository = new WeatherRepository();
            WeatherResponse response = repository.loadWeatherBlocking(
                    cached.getLatitude(),
                    cached.getLongitude()
            );
            cache.save(
                    response,
                    cached.getLatitude(),
                    cached.getLongitude(),
                    System.currentTimeMillis()
            );
            return Result.success();
        } catch (IOException exception) {
            return Result.retry();
        } catch (RuntimeException exception) {
            return Result.failure();
        }
    }
}
