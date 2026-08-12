package com.tridev.liveweather.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.MainActivity;
import com.tridev.liveweather.R;
import com.tridev.liveweather.data.local.SavedCityStore;
import com.tridev.liveweather.data.local.WeatherCache;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.CityLocation;
import com.tridev.liveweather.domain.LiveConditionResolver;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.util.List;
import java.util.Locale;

/** Single rendering/action source for every Phase 17 home-screen widget instance. */
public final class WeatherWidgetUpdater {

    public static final String EXTRA_OPEN_DESTINATION = "widget_open_destination";
    public static final String DESTINATION_HOME = "home";
    public static final String DESTINATION_FORECAST = "forecast";

    private static final long FRESH_MILLIS = 45L * 60L * 1000L;
    private static final long STALE_MILLIS = 3L * 60L * 60L * 1000L;

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
        for (int id : ids) updateCompactId(context, manager, id, null);
    }

    public static void updateWide(@NonNull Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, WideWeatherWidgetProvider.class)
        );
        for (int id : ids) updateWideId(context, manager, id, null);
    }

    public static void updateOne(@NonNull Context context, int appWidgetId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (contains(
                manager.getAppWidgetIds(new ComponentName(context, CompactWeatherWidgetProvider.class)),
                appWidgetId
        )) {
            updateCompactId(context, manager, appWidgetId, null);
            return;
        }
        if (contains(
                manager.getAppWidgetIds(new ComponentName(context, WideWeatherWidgetProvider.class)),
                appWidgetId
        )) {
            updateWideId(context, manager, appWidgetId, null);
        }
    }

    public static void showRefreshing(@NonNull Context context, int appWidgetId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        String label = "Refreshing…";
        if (contains(manager.getAppWidgetIds(
                new ComponentName(context, CompactWeatherWidgetProvider.class)), appWidgetId)) {
            updateCompactId(context, manager, appWidgetId, label);
        } else if (contains(manager.getAppWidgetIds(
                new ComponentName(context, WideWeatherWidgetProvider.class)), appWidgetId)) {
            updateWideId(context, manager, appWidgetId, label);
        }
    }

    public static void showOffline(@NonNull Context context, int appWidgetId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        WidgetPreferences.Config config = new WidgetPreferences(context).load(appWidgetId);
        WeatherCache.CachedWeather cached = resolveCached(context, config);
        String label = cached == null
                ? "Offline · no saved weather"
                : "Offline · saved " + WeatherFormatter.updatedTime(cached.getSavedAt());
        if (contains(manager.getAppWidgetIds(
                new ComponentName(context, CompactWeatherWidgetProvider.class)), appWidgetId)) {
            updateCompactId(context, manager, appWidgetId, label);
        } else if (contains(manager.getAppWidgetIds(
                new ComponentName(context, WideWeatherWidgetProvider.class)), appWidgetId)) {
            updateWideId(context, manager, appWidgetId, label);
        }
    }

    private static void updateCompactId(
            @NonNull Context context,
            @NonNull AppWidgetManager manager,
            int appWidgetId,
            @Nullable String statusOverride
    ) {
        WidgetPreferences.Config config = new WidgetPreferences(context).load(appWidgetId);
        WeatherCache.CachedWeather cached = resolveCached(context, config);
        RemoteViews views = compactViews(context, appWidgetId, config, cached, statusOverride);
        applyCompactResize(manager, appWidgetId, views);
        manager.updateAppWidget(appWidgetId, views);
    }

    private static void updateWideId(
            @NonNull Context context,
            @NonNull AppWidgetManager manager,
            int appWidgetId,
            @Nullable String statusOverride
    ) {
        WidgetPreferences.Config config = new WidgetPreferences(context).load(appWidgetId);
        WeatherCache.CachedWeather cached = resolveCached(context, config);
        RemoteViews views = wideViews(context, appWidgetId, config, cached, statusOverride);
        applyWideResize(manager, appWidgetId, views);
        manager.updateAppWidget(appWidgetId, views);
    }

    @Nullable
    private static WeatherCache.CachedWeather resolveCached(
            @NonNull Context context,
            @NonNull WidgetPreferences.Config config
    ) {
        WeatherCache cache = new WeatherCache(context);
        if (config.hasFixedCoordinates()) {
            return cache.load(config.getLatitude(), config.getLongitude());
        }
        return cache.load();
    }

    @NonNull
    private static RemoteViews compactViews(
            @NonNull Context context,
            int appWidgetId,
            @NonNull WidgetPreferences.Config config,
            @Nullable WeatherCache.CachedWeather cached,
            @Nullable String statusOverride
    ) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_weather_compact);
        applyAppearance(views, R.id.widgetCompactRoot, config);
        attachCommonActions(
                context,
                views,
                appWidgetId,
                R.id.widgetCompactRoot,
                R.id.widgetCompactBrand,
                R.id.widgetCompactRefresh
        );
        views.setTextViewText(R.id.widgetCompactBrand, sourceLabel(context, config));

        if (cached == null) {
            views.setTextViewText(R.id.widgetCompactSymbol, "•");
            views.setTextViewText(R.id.widgetCompactTemperature, "—°");
            views.setTextViewText(R.id.widgetCompactCondition, "Waiting for weather");
            views.setTextViewText(
                    R.id.widgetCompactUpdated,
                    statusOverride == null ? "Tap refresh" : statusOverride
            );
            views.setTextViewText(R.id.widgetCompactHumidity, "Humidity —");
            views.setTextViewText(R.id.widgetCompactWind, "Wind —");
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
                statusOverride == null ? ageLabel(cached.getSavedAt()) : statusOverride
        );
        views.setTextViewText(
                R.id.widgetCompactHumidity,
                "Humidity " + WeatherFormatter.percent(
                        current == null ? null : current.getRelativeHumidity2m())
        );
        views.setTextViewText(R.id.widgetCompactWind, "Wind " + windLabel(current));
        return views;
    }

    @NonNull
    private static RemoteViews wideViews(
            @NonNull Context context,
            int appWidgetId,
            @NonNull WidgetPreferences.Config config,
            @Nullable WeatherCache.CachedWeather cached,
            @Nullable String statusOverride
    ) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_weather_wide);
        applyAppearance(views, R.id.widgetWideRoot, config);
        attachCommonActions(
                context,
                views,
                appWidgetId,
                R.id.widgetWideRoot,
                R.id.widgetWideBrand,
                R.id.widgetWideRefresh
        );
        attachDestination(
                context,
                views,
                appWidgetId,
                R.id.widgetWideHoursRow,
                DESTINATION_FORECAST,
                4
        );
        views.setTextViewText(R.id.widgetWideBrand, sourceLabel(context, config));

        if (cached == null) {
            views.setTextViewText(R.id.widgetWideSymbol, "•");
            views.setTextViewText(R.id.widgetWideTemperature, "—°");
            views.setTextViewText(R.id.widgetWideCondition, "Waiting for weather");
            views.setTextViewText(R.id.widgetWideMetrics, "Humidity — · Wind — · Rain —");
            views.setTextViewText(
                    R.id.widgetWideUpdated,
                    statusOverride == null ? "Tap refresh" : statusOverride
            );
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
                statusOverride == null ? shortAgeLabel(cached.getSavedAt()) : statusOverride
        );
        views.setTextViewText(
                R.id.widgetWideMetrics,
                String.format(
                        Locale.getDefault(),
                        "Humidity %s · Wind %s · Rain %s",
                        WeatherFormatter.percent(current == null ? null : current.getRelativeHumidity2m()),
                        windLabel(current),
                        WeatherFormatter.precipitation(current == null ? null : current.getPrecipitation())
                )
        );
        bindHours(views, weather);
        return views;
    }

    private static void applyAppearance(
            @NonNull RemoteViews views,
            int rootId,
            @NonNull WidgetPreferences.Config config
    ) {
        int background = config.getAppearance() == WidgetPreferences.Appearance.TRANSPARENT
                ? R.drawable.widget_weather_background_transparent
                : R.drawable.widget_weather_background;
        views.setInt(rootId, "setBackgroundResource", background);
    }

    private static void applyCompactResize(
            @NonNull AppWidgetManager manager,
            int appWidgetId,
            @NonNull RemoteViews views
    ) {
        Bundle options = manager.getAppWidgetOptions(appWidgetId);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
        views.setViewVisibility(
                R.id.widgetCompactMetricsRow,
                minHeight < 105 ? View.GONE : View.VISIBLE
        );
    }

    private static void applyWideResize(
            @NonNull AppWidgetManager manager,
            int appWidgetId,
            @NonNull RemoteViews views
    ) {
        Bundle options = manager.getAppWidgetOptions(appWidgetId);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150);
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300);
        views.setViewVisibility(
                R.id.widgetWideHoursRow,
                minHeight < 130 || minWidth < 260 ? View.GONE : View.VISIBLE
        );
    }

    private static void bindHours(@NonNull RemoteViews views, @NonNull WeatherResponse weather) {
        WeatherResponse.HourlyWeather hourly = weather.getHourly();
        if (hourly == null) {
            clearHours(views);
            return;
        }

        int start = WeatherFormatter.findCurrentHourlyIndex(weather);
        int[] timeIds = { R.id.widgetHour1Time, R.id.widgetHour2Time, R.id.widgetHour3Time, R.id.widgetHour4Time };
        int[] symbolIds = { R.id.widgetHour1Symbol, R.id.widgetHour2Symbol, R.id.widgetHour3Symbol, R.id.widgetHour4Symbol };
        int[] tempIds = { R.id.widgetHour1Temp, R.id.widgetHour2Temp, R.id.widgetHour3Temp, R.id.widgetHour4Temp };

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
            views.setTextViewText(timeIds[slot], slot == 0 ? "Now" : WeatherFormatter.hourLabel(time));
            views.setTextViewText(symbolIds[slot], WeatherFormatter.symbol(code, isDay));
            views.setTextViewText(tempIds[slot], WeatherFormatter.temperature(temperature));
        }
    }

    private static void clearHours(@NonNull RemoteViews views) {
        int[] timeIds = { R.id.widgetHour1Time, R.id.widgetHour2Time, R.id.widgetHour3Time, R.id.widgetHour4Time };
        int[] symbolIds = { R.id.widgetHour1Symbol, R.id.widgetHour2Symbol, R.id.widgetHour3Symbol, R.id.widgetHour4Symbol };
        int[] tempIds = { R.id.widgetHour1Temp, R.id.widgetHour2Temp, R.id.widgetHour3Temp, R.id.widgetHour4Temp };
        String[] labels = { "Now", "+1h", "+2h", "+3h" };
        for (int i = 0; i < 4; i++) {
            views.setTextViewText(timeIds[i], labels[i]);
            views.setTextViewText(symbolIds[i], "•");
            views.setTextViewText(tempIds[i], "—°");
        }
    }

    private static void attachCommonActions(
            @NonNull Context context,
            @NonNull RemoteViews views,
            int appWidgetId,
            int rootId,
            int brandId,
            int refreshId
    ) {
        attachDestination(context, views, appWidgetId, rootId, DESTINATION_HOME, 1);

        Intent configureIntent = new Intent(context, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent configurePending = PendingIntent.getActivity(
                context,
                requestCode(appWidgetId, 2),
                configureIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(brandId, configurePending);

        Intent refreshIntent = new Intent(context, WeatherWidgetActionReceiver.class)
                .setAction(WeatherWidgetActionReceiver.ACTION_REFRESH)
                .putExtra(WeatherWidgetActionReceiver.EXTRA_WIDGET_ID, appWidgetId);
        PendingIntent refreshPending = PendingIntent.getBroadcast(
                context,
                requestCode(appWidgetId, 3),
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(refreshId, refreshPending);
    }

    private static void attachDestination(
            @NonNull Context context,
            @NonNull RemoteViews views,
            int appWidgetId,
            int viewId,
            @NonNull String destination,
            int slot
    ) {
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra(EXTRA_OPEN_DESTINATION, destination)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                requestCode(appWidgetId, slot),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(viewId, pendingIntent);
    }

    private static int requestCode(int appWidgetId, int slot) {
        return appWidgetId * 10 + slot;
    }

    @NonNull
    private static String sourceLabel(
            @NonNull Context context,
            @NonNull WidgetPreferences.Config config
    ) {
        if (config.hasFixedCoordinates()) {
            String label = config.getCityName();
            return "LIVE WEATHER · " + (label == null ? "Saved city" : label);
        }
        CityLocation selected = new SavedCityStore(context).getSelectedCity();
        return selected == null
                ? "LIVE WEATHER · Current location"
                : "LIVE WEATHER · " + selected.getDisplayName();
    }

    @NonNull
    private static String ageLabel(long savedAt) {
        long age = Math.max(0L, System.currentTimeMillis() - savedAt);
        String time = WeatherFormatter.updatedTime(savedAt);
        if (savedAt <= 0L) return "Saved weather";
        if (age <= FRESH_MILLIS) return "Live · updated " + time;
        if (age <= STALE_MILLIS) return "Saved · updated " + time;
        return "Stale · updated " + time;
    }

    @NonNull
    private static String shortAgeLabel(long savedAt) {
        long age = Math.max(0L, System.currentTimeMillis() - savedAt);
        String time = WeatherFormatter.updatedTime(savedAt);
        if (savedAt <= 0L) return "Saved";
        if (age <= FRESH_MILLIS) return "Live " + time;
        if (age <= STALE_MILLIS) return "Saved " + time;
        return "Stale " + time;
    }

    @NonNull
    private static String windLabel(@Nullable WeatherResponse.CurrentWeather current) {
        if (current == null) return "—";
        String speed = WeatherFormatter.wind(current.getWindSpeed10m());
        String direction = WeatherFormatter.windDirection(current.getWindDirection10m());
        if ("—".equals(speed)) return direction;
        if ("—".equals(direction)) return speed;
        return speed + " " + direction;
    }

    private static boolean contains(@NonNull int[] ids, int target) {
        for (int id : ids) if (id == target) return true;
        return false;
    }
}
