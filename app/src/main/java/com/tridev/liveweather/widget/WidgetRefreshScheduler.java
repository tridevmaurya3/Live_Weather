package com.tridev.liveweather.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Battery-safe high-freshness periodic refresh for installed weather widgets.
 *
 * Android periodic work cannot be a continuous animation/data stream. Fifteen minutes
 * is the platform periodic-work floor, so installed widgets use that cadence while
 * retaining a small flex window for scheduler/battery cooperation. App foreground
 * refresh and Live Wallpaper rendering remain independent from this job.
 */
public final class WidgetRefreshScheduler {

    private static final String UNIQUE_WORK = "live_weather_widgets_periodic";

    private WidgetRefreshScheduler() {
    }

    public static void schedule(@NonNull Context context) {
        Context app = context.getApplicationContext();
        if (!hasWidgets(app)) return;

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                WidgetRefreshWorker.class,
                15,
                TimeUnit.MINUTES,
                5,
                TimeUnit.MINUTES
        ).setConstraints(constraints).build();

        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }

    public static void cancelIfNoWidgets(@NonNull Context context) {
        Context app = context.getApplicationContext();
        if (!hasWidgets(app)) {
            WorkManager.getInstance(app).cancelUniqueWork(UNIQUE_WORK);
        }
    }

    public static boolean hasWidgets(@NonNull Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int compact = manager.getAppWidgetIds(
                new ComponentName(context, CompactWeatherWidgetProvider.class)
        ).length;
        int wide = manager.getAppWidgetIds(
                new ComponentName(context, WideWeatherWidgetProvider.class)
        ).length;
        return compact + wide > 0;
    }
}
