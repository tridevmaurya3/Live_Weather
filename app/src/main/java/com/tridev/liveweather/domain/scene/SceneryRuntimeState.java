package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;

/**
 * Allocation-free process-local bridge between persisted wallpaper scenery preference
 * and the shared OpenGL world renderer.
 *
 * SharedPreferences is never read from the frame hot path. WallpaperPreferences updates
 * this state when options are loaded/saved; App Hero and Wallpaper then observe the same
 * immutable enum value through one volatile read.
 */
public final class SceneryRuntimeState {

    @NonNull
    private static volatile SceneryMode current = SceneryMode.NATURAL_HILLS;

    private SceneryRuntimeState() {
    }

    public static void set(@NonNull SceneryMode mode) {
        current = mode;
    }

    @NonNull
    public static SceneryMode get() {
        return current;
    }
}
