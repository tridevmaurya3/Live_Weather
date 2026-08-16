package com.tridev.liveweather.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Keeps weather refresh independent from the animation loop. */
public final class WallpaperWeatherScheduler {

    private static final String UNIQUE_WORK = "live_weather_wallpaper_refresh";

    private WallpaperWeatherScheduler() {
    }

    public static void schedule(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                WallpaperWeatherRefreshWorker.class,
                15,
                TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(
                        UNIQUE_WORK,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                );
    }
}
