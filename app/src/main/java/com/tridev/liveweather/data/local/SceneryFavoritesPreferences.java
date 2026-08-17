package com.tridev.liveweather.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.tridev.liveweather.domain.scene.SceneryMode;
import com.tridev.liveweather.domain.scene.SceneryVariantRuntimeState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small ordered store for scenery + variation favorites.
 *
 * Favorites are presentation-only. They never include or mutate weather state.
 * Storage work happens only from the Wallpaper selector UI, never from the GL frame loop.
 */
public final class SceneryFavoritesPreferences {

    public static final int MAX_FAVORITES = 3;

    private static final String PREFS_NAME = "live_weather_wallpaper_preferences";
    private static final String KEY_FAVORITES = "scenery_favorites";
    private static final String ITEM_SEPARATOR = ",";
    private static final String VALUE_SEPARATOR = ":";

    private final SharedPreferences preferences;

    public SceneryFavoritesPreferences(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public List<Favorite> load() {
        String raw = preferences.getString(KEY_FAVORITES, "");
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String[] tokens = raw.split(ITEM_SEPARATOR);
        ArrayList<Favorite> result = new ArrayList<>(Math.min(tokens.length, MAX_FAVORITES));
        for (String token : tokens) {
            Favorite favorite = parse(token);
            if (favorite != null && !contains(result, favorite) && result.size() < MAX_FAVORITES) {
                result.add(favorite);
            }
        }
        return result;
    }

    public boolean contains(@NonNull SceneryMode mode, int variant) {
        return contains(load(), new Favorite(mode, variant));
    }

    /**
     * Toggles one favorite. When the list is full, a newly added favorite replaces the
     * oldest entry so the most recently saved three stay available as quick shortcuts.
     */
    public boolean toggle(@NonNull SceneryMode mode, int variant) {
        Favorite target = new Favorite(mode, variant);
        ArrayList<Favorite> favorites = new ArrayList<>(load());
        for (int index = 0; index < favorites.size(); index++) {
            if (favorites.get(index).sameAs(target)) {
                favorites.remove(index);
                persist(favorites);
                return false;
            }
        }

        if (favorites.size() >= MAX_FAVORITES) {
            favorites.remove(0);
        }
        favorites.add(target);
        persist(favorites);
        return true;
    }

    public void remove(@NonNull Favorite favorite) {
        ArrayList<Favorite> favorites = new ArrayList<>(load());
        for (int index = favorites.size() - 1; index >= 0; index--) {
            if (favorites.get(index).sameAs(favorite)) {
                favorites.remove(index);
            }
        }
        persist(favorites);
    }

    private void persist(@NonNull List<Favorite> favorites) {
        StringBuilder builder = new StringBuilder();
        for (Favorite favorite : favorites) {
            if (builder.length() > 0) builder.append(ITEM_SEPARATOR);
            builder.append(favorite.mode.getStorageKey())
                    .append(VALUE_SEPARATOR)
                    .append(favorite.variant);
        }
        preferences.edit().putString(KEY_FAVORITES, builder.toString()).apply();
    }

    private Favorite parse(@NonNull String token) {
        int separator = token.lastIndexOf(VALUE_SEPARATOR);
        if (separator <= 0 || separator >= token.length() - 1) return null;

        SceneryMode mode = SceneryMode.fromStorage(token.substring(0, separator));
        if (mode == SceneryMode.AUTO) return null;

        try {
            int variant = Integer.parseInt(token.substring(separator + 1));
            return new Favorite(mode, variant);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean contains(@NonNull List<Favorite> favorites, @NonNull Favorite target) {
        for (Favorite favorite : favorites) {
            if (favorite.sameAs(target)) return true;
        }
        return false;
    }

    public static final class Favorite {
        @NonNull private final SceneryMode mode;
        private final int variant;

        public Favorite(@NonNull SceneryMode mode, int variant) {
            this.mode = mode == SceneryMode.AUTO ? SceneryMode.NATURAL_HILLS : mode;
            this.variant = Math.max(
                    0,
                    Math.min(SceneryVariantRuntimeState.VARIANT_COUNT - 1, variant)
            );
        }

        @NonNull
        public SceneryMode getMode() {
            return mode;
        }

        public int getVariant() {
            return variant;
        }

        private boolean sameAs(@NonNull Favorite other) {
            return mode == other.mode && variant == other.variant;
        }
    }
}
