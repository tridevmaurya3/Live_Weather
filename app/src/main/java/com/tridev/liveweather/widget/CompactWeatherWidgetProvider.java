package com.tridev.liveweather.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.tridev.liveweather.worker.WallpaperWeatherScheduler;

/** Compact current-conditions home-screen widget. */
public final class CompactWeatherWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(
            @NonNull Context context,
            @NonNull AppWidgetManager appWidgetManager,
            @NonNull int[] appWidgetIds
    ) {
        WeatherWidgetUpdater.updateCompact(context);
    }

    @Override
    public void onEnabled(@NonNull Context context) {
        super.onEnabled(context);
        WallpaperWeatherScheduler.schedule(context);
        WeatherWidgetUpdater.updateCompact(context);
        WidgetRefreshWorker.enqueue(context);
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        super.onReceive(context, intent);
        if (WeatherWidgetUpdater.ACTION_REFRESH_WIDGETS.equals(intent.getAction())) {
            WeatherWidgetUpdater.showRefreshing(context);
            WidgetRefreshWorker.enqueue(context);
        }
    }
}
