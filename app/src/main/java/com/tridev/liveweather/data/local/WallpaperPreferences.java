package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Shared visual preferences for the in-app animated preview and WallpaperService.
 * Reality sync (time, Sun/Moon position and weather state) remains always active.
 */
public final class WallpaperPreferences {

    private static final String PREFS_NAME = "live_weather_wallpaper_preferences";
    private static final String KEY_RAIN = "rain";
    private static final String KEY_CLOUDS = "clouds";
    private static final String KEY_LIGHTNING = "lightning";
    private static final String KEY_SNOW = "snow";
    private static final String KEY_FOG = "fog";
    private static final String KEY_STARS = "stars";
    private static final String KEY_BATTERY_ADAPTIVE = "battery_adaptive";

    private final SharedPreferences preferences;

    public WallpaperPreferences(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public Options load() {
        return new Options(
                preferences.getBoolean(KEY_RAIN, true),
                preferences.getBoolean(KEY_CLOUDS, true),
                preferences.getBoolean(KEY_LIGHTNING, true),
                preferences.getBoolean(KEY_SNOW, true),
                preferences.getBoolean(KEY_FOG, true),
                preferences.getBoolean(KEY_STARS, true),
                preferences.getBoolean(KEY_BATTERY_ADAPTIVE, true)
        );
    }

    public void save(@NonNull Options options) {
        preferences.edit()
                .putBoolean(KEY_RAIN, options.isRain())
                .putBoolean(KEY_CLOUDS, options.isClouds())
                .putBoolean(KEY_LIGHTNING, options.isLightning())
                .putBoolean(KEY_SNOW, options.isSnow())
                .putBoolean(KEY_FOG, options.isFog())
                .putBoolean(KEY_STARS, options.isStars())
                .putBoolean(KEY_BATTERY_ADAPTIVE, options.isBatteryAdaptive())
                .apply();
    }

    public static final class Options {
        private final boolean rain;
        private final boolean clouds;
        private final boolean lightning;
        private final boolean snow;
        private final boolean fog;
        private final boolean stars;
        private final boolean batteryAdaptive;

        public Options(
                boolean rain,
                boolean clouds,
                boolean lightning,
                boolean snow,
                boolean fog,
                boolean stars,
                boolean batteryAdaptive
        ) {
            this.rain = rain;
            this.clouds = clouds;
            this.lightning = lightning;
            this.snow = snow;
            this.fog = fog;
            this.stars = stars;
            this.batteryAdaptive = batteryAdaptive;
        }

        public boolean isRain() {
            return rain;
        }

        public boolean isClouds() {
            return clouds;
        }

        public boolean isLightning() {
            return lightning;
        }

        public boolean isSnow() {
            return snow;
        }

        public boolean isFog() {
            return fog;
        }

        public boolean isStars() {
            return stars;
        }

        public boolean isBatteryAdaptive() {
            return batteryAdaptive;
        }
    }
}
