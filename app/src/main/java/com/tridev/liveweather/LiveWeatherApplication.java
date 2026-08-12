package com.tridev.liveweather;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.local.UnitPreferences;
import com.tridev.liveweather.ui.settings.SettingsCardBinder;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

/**
 * Application-level presentation configuration for Phase 16.
 *
 * Unit formatting is configured before Activities, widgets, or background work
 * render cached weather. MainActivity's existing More-page cards are then bound
 * when the Activity becomes visible, avoiding a large risky Activity rewrite.
 */
public final class LiveWeatherApplication extends Application
        implements Application.ActivityLifecycleCallbacks {

    @Override
    public void onCreate() {
        super.onCreate();
        WeatherFormatter.configure(new UnitPreferences(this).load());
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity instanceof MainActivity) {
            SettingsCardBinder.bind(activity);
        }
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) { }
    @Override public void onActivityStarted(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) { }
    @Override public void onActivityDestroyed(@NonNull Activity activity) { }
}
