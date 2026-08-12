package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/** Persistent user preference for app / wallpaper rendering performance. */
public final class PerformancePreferences {

    private static final String PREFS = "live_weather_performance";
    private static final String KEY_MODE = "mode";

    public enum Mode {
        AUTO,
        SMOOTH,
        BATTERY
    }

    private final SharedPreferences preferences;

    public PerformancePreferences(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @NonNull
    public Mode loadMode() {
        String stored = preferences.getString(KEY_MODE, Mode.AUTO.name());
        if (stored == null) return Mode.AUTO;
        try {
            return Mode.valueOf(stored);
        } catch (IllegalArgumentException ignored) {
            return Mode.AUTO;
        }
    }

    public void saveMode(@NonNull Mode mode) {
        preferences.edit().putString(KEY_MODE, mode.name()).apply();
    }
}
