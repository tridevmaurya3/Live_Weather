package com.tridev.liveweather.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

import androidx.annotation.NonNull;

import com.tridev.liveweather.worker.WallpaperWeatherScheduler;

/** Wide current + next-hours home-screen widget. */
public final class WideWeatherWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(
            @NonNull Context context,
            @NonNull AppWidgetManager appWidgetManager,
            @NonNull int[] appWidgetIds
    ) {
        WeatherWidgetUpdater.updateWide(context);
    }

    @Override
    public void onEnabled(@NonNull Context context) {
        super.onEnabled(context);
        WallpaperWeatherScheduler.schedule(context);
        WeatherWidgetUpdater.updateWide(context);
        WidgetRefreshWorker.enqueue(context);
    }
}
