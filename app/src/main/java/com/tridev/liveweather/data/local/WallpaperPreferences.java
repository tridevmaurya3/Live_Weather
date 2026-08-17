package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.tridev.liveweather.domain.scene.AutoSceneryContextState;
import com.tridev.liveweather.domain.scene.SceneryMode;
import com.tridev.liveweather.domain.scene.SceneryRuntimeState;
import com.tridev.liveweather.domain.scene.SceneryVariantRuntimeState;

/**
 * Shared visual preferences for the in-app animated preview and WallpaperService.
 * Reality sync (time, Sun/Moon position and weather state) remains always active.
 *
 * Scenery is a visual choice only. It is persisted separately from weather truth so
 * selecting Village, Farm, River, Urban or Auto can never fabricate a weather state.
 * Auto Scene resolves outside the OpenGL frame loop using the latest current-condition
 * presentation context, with day-part fallback before any current truth is available.
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
    private static final String KEY_SCENERY_MODE = "scenery_mode";

    private final SharedPreferences preferences;

    public WallpaperPreferences(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SceneryVariantRuntimeState.initialize(appContext);
    }

    @NonNull
    public Options load() {
        SceneryMode requestedMode = loadRequestedSceneryMode();
        SceneryMode resolvedMode = resolveRuntimeScenery(requestedMode);
        SceneryRuntimeState.setSelection(requestedMode, resolvedMode);

        return new Options(
                preferences.getBoolean(KEY_RAIN, true),
                preferences.getBoolean(KEY_CLOUDS, true),
                preferences.getBoolean(KEY_LIGHTNING, true),
                preferences.getBoolean(KEY_SNOW, true),
                preferences.getBoolean(KEY_FOG, true),
                preferences.getBoolean(KEY_STARS, true),
                preferences.getBoolean(KEY_BATTERY_ADAPTIVE, true),
                requestedMode
        );
    }

    public void save(@NonNull Options options) {
        SceneryMode requestedMode = options.getSceneryMode();
        SceneryMode resolvedMode = resolveRuntimeScenery(requestedMode);
        SceneryRuntimeState.setSelection(requestedMode, resolvedMode);

        preferences.edit()
                .putBoolean(KEY_RAIN, options.isRain())
                .putBoolean(KEY_CLOUDS, options.isClouds())
                .putBoolean(KEY_LIGHTNING, options.isLightning())
                .putBoolean(KEY_SNOW, options.isSnow())
                .putBoolean(KEY_FOG, options.isFog())
                .putBoolean(KEY_STARS, options.isStars())
                .putBoolean(KEY_BATTERY_ADAPTIVE, options.isBatteryAdaptive())
                .putString(KEY_SCENERY_MODE, requestedMode.getStorageKey())
                .apply();
    }

    /**
     * Re-resolves the currently persisted scene without changing any option.
     * Useful after changing variation while Auto Scene is selected.
     */
    @NonNull
    public SceneryMode refreshRuntimeScenery() {
        SceneryMode requestedMode = loadRequestedSceneryMode();
        SceneryMode resolvedMode = resolveRuntimeScenery(requestedMode);
        SceneryRuntimeState.setSelection(requestedMode, resolvedMode);
        return resolvedMode;
    }

    @NonNull
    private SceneryMode loadRequestedSceneryMode() {
        return SceneryMode.fromStorage(preferences.getString(KEY_SCENERY_MODE, null));
    }

    @NonNull
    private SceneryMode resolveRuntimeScenery(@NonNull SceneryMode requestedMode) {
        if (requestedMode != SceneryMode.AUTO) {
            return requestedMode;
        }
        return AutoSceneryContextState.resolve(SceneryVariantRuntimeState.get());
    }

    public static final class Options {
        private final boolean rain;
        private final boolean clouds;
        private final boolean lightning;
        private final boolean snow;
        private final boolean fog;
        private final boolean stars;
        private final boolean batteryAdaptive;
        @NonNull private final SceneryMode sceneryMode;

        /**
         * Compatibility constructor used by existing callers. It preserves what the user
         * requested, including AUTO, instead of copying Auto Scene's concrete render result.
         * Therefore changing rain/cloud/lightning/snow/fog/star/battery switches can never
         * silently turn Auto Scene into a manual scene.
         */
        public Options(
                boolean rain,
                boolean clouds,
                boolean lightning,
                boolean snow,
                boolean fog,
                boolean stars,
                boolean batteryAdaptive
        ) {
            this(
                    rain,
                    clouds,
                    lightning,
                    snow,
                    fog,
                    stars,
                    batteryAdaptive,
                    SceneryRuntimeState.getRequested()
            );
        }

        public Options(
                boolean rain,
                boolean clouds,
                boolean lightning,
                boolean snow,
                boolean fog,
                boolean stars,
                boolean batteryAdaptive,
                @NonNull SceneryMode sceneryMode
        ) {
            this.rain = rain;
            this.clouds = clouds;
            this.lightning = lightning;
            this.snow = snow;
            this.fog = fog;
            this.stars = stars;
            this.batteryAdaptive = batteryAdaptive;
            this.sceneryMode = sceneryMode;
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

        @NonNull
        public SceneryMode getSceneryMode() {
            return sceneryMode;
        }

        @NonNull
        public Options withSceneryMode(@NonNull SceneryMode mode) {
            return new Options(
                    rain,
                    clouds,
                    lightning,
                    snow,
                    fog,
                    stars,
                    batteryAdaptive,
                    mode
            );
        }
    }
}
