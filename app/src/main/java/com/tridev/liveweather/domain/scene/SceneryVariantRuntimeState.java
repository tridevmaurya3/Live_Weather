package com.tridev.liveweather.domain.scene;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Process-local + persisted scenery variation identity.
 *
 * Variations are presentation-only and never influence weather truth. The value is
 * initialized once from the same wallpaper preferences file used by scenery mode,
 * then read allocation-free by the OpenGL world renderer.
 */
public final class SceneryVariantRuntimeState {

    public static final int VARIANT_COUNT = 4;

    private static final String PREFS_NAME = "live_weather_wallpaper_preferences";
    private static final String KEY_SCENERY_VARIANT = "scenery_variant";

    private static volatile int currentVariant;

    private SceneryVariantRuntimeState() {
    }

    public static void initialize(@NonNull Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        currentVariant = clamp(preferences.getInt(KEY_SCENERY_VARIANT, 0));
    }

    public static int get() {
        return currentVariant;
    }

    public static int setAndPersist(@NonNull Context context, int variant) {
        int bounded = clamp(variant);
        currentVariant = bounded;
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_SCENERY_VARIANT, bounded)
                .apply();
        return bounded;
    }

    public static int nextAndPersist(@NonNull Context context) {
        return setAndPersist(context, (currentVariant + 1) % VARIANT_COUNT);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(VARIANT_COUNT - 1, value));
    }
}
