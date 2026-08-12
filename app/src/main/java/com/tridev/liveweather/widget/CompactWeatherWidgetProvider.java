package com.tridev.liveweather.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

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
        for (int id : appWidgetIds) WeatherWidgetUpdater.updateOne(context, id);
        WidgetRefreshScheduler.schedule(context);
    }

    @Override
    public void onEnabled(@NonNull Context context) {
        super.onEnabled(context);
        WallpaperWeatherScheduler.schedule(context);
        WidgetRefreshScheduler.schedule(context);
        WeatherWidgetUpdater.updateCompact(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            @NonNull Context context,
            @NonNull AppWidgetManager appWidgetManager,
            int appWidgetId,
            @NonNull Bundle newOptions
    ) {
        WeatherWidgetUpdater.updateOne(context, appWidgetId);
    }

    @Override
    public void onDeleted(@NonNull Context context, @NonNull int[] appWidgetIds) {
        WidgetPreferences preferences = new WidgetPreferences(context);
        for (int id : appWidgetIds) preferences.delete(id);
        WidgetRefreshScheduler.cancelIfNoWidgets(context);
    }

    @Override
    public void onDisabled(@NonNull Context context) {
        super.onDisabled(context);
        WidgetRefreshScheduler.cancelIfNoWidgets(context);
    }
}
