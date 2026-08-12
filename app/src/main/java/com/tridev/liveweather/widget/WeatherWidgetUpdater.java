package com.tridev.liveweather.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.MainActivity;
import com.tridev.liveweather.R;
import com.tridev.liveweather.data.local.WeatherCache;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.util.List;
import java.util.Locale;

/**
 * Single source of truth for Phase 10 home-screen widgets.
 *
 * Widgets deliberately consume the persistent weather cache instead of making
 * network calls from RemoteViews. WidgetRefreshWorker and the existing
 * wallpaper refresh worker update that cache off the UI/render paths.
 */
public final class WeatherWidgetUpdater {

    public static final String ACTION_REFRESH_WIDGETS =
            "com.tridev.liveweather.action.REFRESH_WIDGETS";

    private WeatherWidgetUpdater() {
    }

    public static void updateAll(@NonNull Context context) {
        updateCompact(context);
        updateWide(context);
    }

    public static void updateCompact(@NonNull Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, CompactWeatherWidgetProvider.class)
        );
        if (ids == null || ids.length == 0) return;

        WeatherCache.CachedWeather cached = new WeatherCache(context).load();
        for (int id : ids) {
            manager.updateAppWidget(id, compactViews(context, cached));
        }
    }

    public static void updateWide(@NonNull Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, WideWeatherWidgetProvider.class)
        );
        if (ids == null || ids.length == 0) return;

        WeatherCache.CachedWeather cached = new WeatherCache(context).load();
        for (int id : ids) {
            manager.updateAppWidget(id, wideViews(context, cached));
        }
    }

    public static void showRefreshing(@NonNull Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);

        int[] compactIds = manager.getAppWidgetIds(
                new ComponentName(context, CompactWeatherWidgetProvider.class)
        );
        for (int id : compactIds) {
            RemoteViews views = compactViews(context, new WeatherCache(context).load());
            views.setTextViewText(R.id.widgetCompactUpdated, context.getString(R.string.widget_refreshing));
            manager.updateAppWidget(id, views);
        }

        int[] wideIds = manager.getAppWidgetIds(
                new ComponentName(context, WideWeatherWidgetProvider.class)
        );
        for (int id : wideIds) {
            RemoteViews views = wideViews(context, new WeatherCache(context).load());
            views.setTextViewText(R.id.widgetWideUpdated, context.getString(R.string.widget_refreshing));
            manager.updateAppWidget(id, views);
        }
    }

    @NonNull
    private static RemoteViews compactViews(
            @NonNull Context context,
            @Nullable WeatherCache.CachedWeather cached
    ) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_weather_compact);
        attachActions(context, views, R.id.widgetCompactRoot, R.id.widgetCompactRefresh, 3101);

        if (cached == null) {
            views.setTextViewText(R.id.widgetCompactSymbol, "•");
            views.setTextViewText(R.id.widgetCompactTemperature, "—°");
            views.setTextViewText(R.id.widgetCompactCondition, context.getString(R.string.widget_waiting));
            views.setTextViewText(R.id.widgetCompactUpdated, context.getString(R.string.widget_tap_refresh));
            views.setTextViewText(R.id.widgetCompactHumidity, context.getString(R.string.widget_humidity_waiting));
            views.setTextViewText(R.id.widgetCompactWind, context.getString(R.string.widget_wind_waiting));
            return views;
        }

        WeatherResponse weather = cached.getWeather();
        WeatherResponse.CurrentWeather current = weather.getCurrent();
        LiveConditionResolver.ResolvedCondition condition = LiveConditionResolver.resolve(weather);

        views.setTextViewText(
                R.id.widgetCompactSymbol,
                WeatherFormatter.symbol(condition.getWeatherCode(), condition.getIsDay())
        );
        views.setTextViewText(
                R.id.widgetCompactTemperature,
                WeatherFormatter.temperature(current == null ? null : current.getTemperature2m())
        );
        views.setTextViewText(R.id.widgetCompactCondition, condition.getLabel());
        views.setTextViewText(
                R.id.widgetCompactUpdated,
                context.getString(
                        R.string.widget_updated_format,
                        WeatherFormatter.updatedTime(cached.getSavedAt())
                )
        );
        views.setTextViewText(
                R.id.widgetCompactHumidity,
                context.getString(
                        R.string.widget_humidity_format,
                        WeatherFormatter.percent(current == null ? null : current.getRelativeHumidity2m())
                )
        );
        views.setTextViewText(
                R.id.widgetCompactWind,
                context.getString(
                        R.string.widget_wind_format,
                        windLabel(current)
                )
        );
        return views;
    }

    @NonNull
    private static RemoteViews wideViews(
            @NonNull Context context,
            @Nullable WeatherCache.CachedWeather cached
    ) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_weather_wide);
        attachActions(context, views, R.id.widgetWideRoot, R.id.widgetWideRefresh, 3201);

        if (cached == null) {
            views.setTextViewText(R.id.widgetWideSymbol, "•");
            views.setTextViewText(R.id.widgetWideTemperature, "—°");
            views.setTextViewText(R.id.widgetWideCondition, context.getString(R.string.widget_waiting));
            views.setTextViewText(R.id.widgetWideMetrics, context.getString(R.string.widget_metrics_waiting));
            views.setTextViewText(R.id.widgetWideUpdated, context.getString(R.string.widget_tap_refresh));
            clearHours(views);
            return views;
        }

        WeatherResponse weather = cached.getWeather();
        WeatherResponse.CurrentWeather current = weather.getCurrent();
        LiveConditionResolver.ResolvedCondition condition = LiveConditionResolver.resolve(weather);

        views.setTextViewText(
                R.id.widgetWideSymbol,
                WeatherFormatter.symbol(condition.getWeatherCode(), condition.getIsDay())
        );
        views.setTextViewText(
                R.id.widgetWideTemperature,
                WeatherFormatter.temperature(current == null ? null : current.getTemperature2m())
        );
        views.setTextViewText(R.id.widgetWideCondition, condition.getLabel());
        views.setTextViewText(
                R.id.widgetWideUpdated,
                context.getString(
                        R.string.widget_updated_short_format,
                        WeatherFormatter.updatedTime(cached.getSavedAt())
                )
        );
        views.setTextViewText(
                R.id.widgetWideMetrics,
                context.getString(
                        R.string.widget_metrics_format,
                        WeatherFormatter.percent(current == null ? null : current.getRelativeHumidity2m()),
                        windLabel(current),
                        WeatherFormatter.precipitation(current == null ? null : current.getPrecipitation())
                )
        );
        bindHours(views, weather);
        return views;
    }

    private static void bindHours(@NonNull RemoteViews views, @NonNull WeatherResponse weather) {
        WeatherResponse.HourlyWeather hourly = weather.getHourly();
        if (hourly == null) {
            clearHours(views);
            return;
        }

        int start = WeatherFormatter.findCurrentHourlyIndex(weather);
        int[] timeIds = {
                R.id.widgetHour1Time,
                R.id.widgetHour2Time,
                R.id.widgetHour3Time,
                R.id.widgetHour4Time
        };
        int[] symbolIds = {
                R.id.widgetHour1Symbol,
                R.id.widgetHour2Symbol,
                R.id.widgetHour3Symbol,
                R.id.widgetHour4Symbol
        };
        int[] tempIds = {
                R.id.widgetHour1Temp,
                R.id.widgetHour2Temp,
                R.id.widgetHour3Temp,
                R.id.widgetHour4Temp
        };

        List<String> times = hourly.getTime();
        List<Integer> codes = hourly.getWeatherCode();
        List<Integer> dayFlags = hourly.getIsDay();
        List<Double> temperatures = hourly.getTemperature2m();

        for (int slot = 0; slot < 4; slot++) {
            int index = start + slot;
            String time = WeatherFormatter.valueAt(times, index);
            Integer code = WeatherFormatter.valueAt(codes, index);
            Integer isDay = WeatherFormatter.valueAt(dayFlags, index);
            Double temperature = WeatherFormatter.valueAt(temperatures, index);

            views.setTextViewText(
                    timeIds[slot],
                    slot == 0 ? "Now" : WeatherFormatter.hourLabel(time)
            );
            views.setTextViewText(symbolIds[slot], WeatherFormatter.symbol(code, isDay));
            views.setTextViewText(tempIds[slot], WeatherFormatter.temperature(temperature));
        }
    }

    private static void clearHours(@NonNull RemoteViews views) {
        int[] timeIds = {
                R.id.widgetHour1Time,
                R.id.widgetHour2Time,
                R.id.widgetHour3Time,
                R.id.widgetHour4Time
        };
        int[] symbolIds = {
                R.id.widgetHour1Symbol,
                R.id.widgetHour2Symbol,
                R.id.widgetHour3Symbol,
                R.id.widgetHour4Symbol
        };
        int[] tempIds = {
                R.id.widgetHour1Temp,
                R.id.widgetHour2Temp,
                R.id.widgetHour3Temp,
                R.id.widgetHour4Temp
        };
        String[] labels = {"Now", "+1h", "+2h", "+3h"};
        for (int index = 0; index < 4; index++) {
            views.setTextViewText(timeIds[index], labels[index]);
            views.setTextViewText(symbolIds[index], "•");
            views.setTextViewText(tempIds[index], "—°");
        }
    }

    private static void attachActions(
            @NonNull Context context,
            @NonNull RemoteViews views,
            int rootId,
            int refreshId,
            int refreshRequestCode
    ) {
        Intent openIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context,
                3001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(rootId, openPendingIntent);

        Intent refreshIntent = new Intent(context, CompactWeatherWidgetProvider.class)
                .setAction(ACTION_REFRESH_WIDGETS);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                refreshRequestCode,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(refreshId, refreshPendingIntent);
    }

    @NonNull
    private static String windLabel(@Nullable WeatherResponse.CurrentWeather current) {
        if (current == null) return "—";
        String speed = WeatherFormatter.wind(current.getWindSpeed10m());
        String direction = WeatherFormatter.windDirection(current.getWindDirection10m());
        if ("—".equals(speed)) return direction;
        if ("—".equals(direction)) return speed;
        return String.format(Locale.getDefault(), "%s %s", speed, direction);
    }
}
