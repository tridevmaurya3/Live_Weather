package com.tridev.liveweather.widget;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Handles explicit widget actions without doing network work on the broadcast thread. */
public final class WeatherWidgetActionReceiver extends BroadcastReceiver {

    public static final String ACTION_REFRESH =
            "com.tridev.liveweather.action.WIDGET_REFRESH";
    public static final String EXTRA_WIDGET_ID = "widget_id";

    @Override
    public void onReceive(@NonNull Context context, @Nullable Intent intent) {
        if (intent == null || !ACTION_REFRESH.equals(intent.getAction())) return;
        int appWidgetId = intent.getIntExtra(
                EXTRA_WIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return;

        WeatherWidgetUpdater.showRefreshing(context, appWidgetId);
        WidgetRefreshWorker.enqueue(context, appWidgetId);
    }
}
