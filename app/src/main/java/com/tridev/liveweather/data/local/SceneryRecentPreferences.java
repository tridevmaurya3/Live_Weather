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
 * Small MRU store for recently used manual scenery + variation combinations.
 *
 * Recent history is presentation-only. It never stores weather state and is only read/written
 * from the Wallpaper selector UI, never from the GL frame loop or weather refresh workers.
 */
public final class SceneryRecentPreferences {

    public static final int MAX_RECENTS = 5;

    private static final String PREFS_NAME = "live_weather_wallpaper_preferences";
    private static final String KEY_RECENTS = "scenery_recents";
    private static final String ITEM_SEPARATOR = ",";
    private static final String VALUE_SEPARATOR = ":";

    private final SharedPreferences preferences;

    public SceneryRecentPreferences(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Returns recents newest-first.
     */
    @NonNull
    public List<Recent> load() {
        String raw = preferences.getString(KEY_RECENTS, "");
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String[] tokens = raw.split(ITEM_SEPARATOR);
        ArrayList<Recent> result = new ArrayList<>(Math.min(tokens.length, MAX_RECENTS));
        for (String token : tokens) {
            Recent recent = parse(token);
            if (recent != null && !contains(result, recent) && result.size() < MAX_RECENTS) {
                result.add(recent);
            }
        }
        return result;
    }

    /**
     * Records one concrete manual scene. Auto is intentionally ignored because its resolved
     * scenery can change with observed weather/day-part and should not become a misleading
     * fixed shortcut.
     */
    public void record(@NonNull SceneryMode mode, int variant) {
        if (mode == SceneryMode.AUTO) return;

        Recent target = new Recent(mode, variant);
        ArrayList<Recent> recents = new ArrayList<>(load());
        for (int index = recents.size() - 1; index >= 0; index--) {
            if (recents.get(index).sameAs(target)) {
                recents.remove(index);
            }
        }
        recents.add(0, target);
        while (recents.size() > MAX_RECENTS) {
            recents.remove(recents.size() - 1);
        }
        persist(recents);
    }

    private void persist(@NonNull List<Recent> recents) {
        StringBuilder builder = new StringBuilder();
        for (Recent recent : recents) {
            if (builder.length() > 0) builder.append(ITEM_SEPARATOR);
            builder.append(recent.mode.getStorageKey())
                    .append(VALUE_SEPARATOR)
                    .append(recent.variant);
        }
        preferences.edit().putString(KEY_RECENTS, builder.toString()).apply();
    }

    private Recent parse(@NonNull String token) {
        int separator = token.lastIndexOf(VALUE_SEPARATOR);
        if (separator <= 0 || separator >= token.length() - 1) return null;

        SceneryMode mode = SceneryMode.fromStorage(token.substring(0, separator));
        if (mode == SceneryMode.AUTO) return null;

        try {
            int variant = Integer.parseInt(token.substring(separator + 1));
            return new Recent(mode, variant);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean contains(@NonNull List<Recent> recents, @NonNull Recent target) {
        for (Recent recent : recents) {
            if (recent.sameAs(target)) return true;
        }
        return false;
    }

    public static final class Recent {
        @NonNull private final SceneryMode mode;
        private final int variant;

        public Recent(@NonNull SceneryMode mode, int variant) {
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

        private boolean sameAs(@NonNull Recent other) {
            return mode == other.mode && variant == other.variant;
        }
    }
}
