package com.tridev.liveweather.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.tridev.liveweather.core.DataReliabilityPolicy;
import com.tridev.liveweather.data.local.WeatherCache;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.repository.WeatherRepository;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Source-aware widget weather refresh. AQI is intentionally not fetched here. */
public final class WidgetRefreshWorker extends Worker {

    private static final String INPUT_WIDGET_ID = "widget_id";
    private static final int MAX_PERIODIC_FIXED_LOCATIONS = 4;
    private static final long FIXED_CACHE_REUSE_MILLIS = 45L * 60L * 1000L;

    public WidgetRefreshWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams
    ) {
        super(appContext, workerParams);
    }

    public static void enqueue(@NonNull Context context) {
        enqueue(context, AppWidgetManager.INVALID_APPWIDGET_ID);
    }

    public static void enqueue(@NonNull Context context, int appWidgetId) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        Data data = new Data.Builder()
                .putInt(INPUT_WIDGET_ID, appWidgetId)
                .build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WidgetRefreshWorker.class)
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .build();
        String workName = appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID
                ? "live_weather_widget_manual_refresh_all"
                : "live_weather_widget_manual_refresh_" + appWidgetId;
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        int appWidgetId = getInputData().getInt(
                INPUT_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            return refreshSingle(context, appWidgetId);
        }

        refreshFixedWidgetLocations(context);
        WeatherWidgetUpdater.updateAll(context);
        return Result.success();
    }

    @NonNull
    private Result refreshSingle(@NonNull Context context, int appWidgetId) {
        WidgetPreferences.Config config = new WidgetPreferences(context).load(appWidgetId);
        WeatherCache cache = new WeatherCache(context);

        double latitude;
        double longitude;
        boolean fixed = config.hasFixedCoordinates();
        if (fixed) {
            latitude = config.getLatitude();
            longitude = config.getLongitude();
        } else {
            WeatherCache.CachedWeather active = cache.load();
            if (active == null) {
                WeatherWidgetUpdater.updateOne(context, appWidgetId);
                return Result.success();
            }
            latitude = active.getLatitude();
            longitude = active.getLongitude();
        }

        try {
            WeatherResponse weather = new WeatherRepository()
                    .loadWeatherBlocking(latitude, longitude);
            long now = System.currentTimeMillis();
            if (fixed) {
                cache.saveSnapshot(weather, latitude, longitude, now);
            } else {
                // A follow-active widget must not move the app/wallpaper active
                // pointer back if the user changed city while this request ran.
                cache.saveIfStillActive(weather, latitude, longitude, now);
            }
            WeatherWidgetUpdater.updateOne(context, appWidgetId);
            return Result.success();
        } catch (IOException exception) {
            WeatherWidgetUpdater.showOffline(context, appWidgetId);
            return DataReliabilityPolicy.shouldRetryBackground(getRunAttemptCount())
                    ? Result.retry()
                    : Result.success();
        } catch (RuntimeException exception) {
            WeatherWidgetUpdater.showOffline(context, appWidgetId);
            return Result.failure();
        }
    }

    private void refreshFixedWidgetLocations(@NonNull Context context) {
        Map<String, WidgetPreferences.Config> uniqueTargets = new LinkedHashMap<>();
        WidgetPreferences preferences = new WidgetPreferences(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);

        addFixedTargets(
                uniqueTargets,
                preferences,
                manager.getAppWidgetIds(new ComponentName(context, CompactWeatherWidgetProvider.class))
        );
        addFixedTargets(
                uniqueTargets,
                preferences,
                manager.getAppWidgetIds(new ComponentName(context, WideWeatherWidgetProvider.class))
        );

        WeatherCache cache = new WeatherCache(context);
        WeatherRepository repository = new WeatherRepository();
        long now = System.currentTimeMillis();
        int refreshed = 0;
        for (WidgetPreferences.Config config : uniqueTargets.values()) {
            WeatherCache.CachedWeather existing = cache.load(
                    config.getLatitude(),
                    config.getLongitude()
            );
            if (existing != null
                    && existing.getSavedAt() > 0L
                    && now - existing.getSavedAt() <= FIXED_CACHE_REUSE_MILLIS) {
                continue;
            }
            if (refreshed >= MAX_PERIODIC_FIXED_LOCATIONS) break;
            try {
                WeatherResponse weather = repository.loadWeatherBlocking(
                        config.getLatitude(),
                        config.getLongitude()
                );
                cache.saveSnapshot(
                        weather,
                        config.getLatitude(),
                        config.getLongitude(),
                        System.currentTimeMillis()
                );
                refreshed++;
            } catch (IOException | RuntimeException ignored) {
                // Existing cached snapshot remains visible with an age/stale label.
            }
        }
    }

    private void addFixedTargets(
            @NonNull Map<String, WidgetPreferences.Config> targets,
            @NonNull WidgetPreferences preferences,
            @NonNull int[] appWidgetIds
    ) {
        for (int id : appWidgetIds) {
            WidgetPreferences.Config config = preferences.load(id);
            if (!config.hasFixedCoordinates()) continue;
            String key = String.format(
                    Locale.US,
                    "%.3f_%.3f",
                    config.getLatitude(),
                    config.getLongitude()
            );
            targets.put(key, config);
        }
    }
}
