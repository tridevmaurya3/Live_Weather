package com.tridev.liveweather;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.local.UnitPreferences;
import com.tridev.liveweather.ui.forecast.ForecastProBinder;
import com.tridev.liveweather.ui.settings.SettingsCardBinder;
import com.tridev.liveweather.ui.weather.WeatherFormatter;
import com.tridev.liveweather.widget.WeatherWidgetUpdater;
import com.tridev.liveweather.widget.WidgetRefreshScheduler;

/** Shared presentation + lightweight widget / forecast startup configuration. */
public final class LiveWeatherApplication extends Application
        implements Application.ActivityLifecycleCallbacks {

    @Override
    public void onCreate() {
        super.onCreate();
        WeatherFormatter.configure(new UnitPreferences(this).load());
        WidgetRefreshScheduler.schedule(this);
        WeatherWidgetUpdater.updateAll(this);
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity instanceof MainActivity) {
            SettingsCardBinder.bind(activity);
            ForecastProBinder.bind((MainActivity) activity);
        }
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) { }
    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) { }
    @Override public void onActivityDestroyed(@NonNull Activity activity) { }
}
