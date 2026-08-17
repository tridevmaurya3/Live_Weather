package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;

import java.util.Calendar;

/**
 * Deterministic presentation-only policy used when the user selects Auto scenery.
 *
 * The policy changes only the scenery identity. It never creates, suppresses or
 * reinterprets weather. Resolution happens outside the OpenGL frame hot path when
 * wallpaper preferences are loaded/saved, so there is no per-frame clock, disk or
 * network work.
 */
public final class AutoSceneryPolicy {

    private static final SceneryMode[] DAWN_POOL = {
            SceneryMode.NATURAL_HILLS,
            SceneryMode.RIVER_LAKE,
            SceneryMode.VILLAGE,
            SceneryMode.OPEN_SKY
    };

    private static final SceneryMode[] DAY_POOL = {
            SceneryMode.FARM_CROPS,
            SceneryMode.FLOWERS_GREENERY,
            SceneryMode.OPEN_SKY,
            SceneryMode.VILLAGE
    };

    private static final SceneryMode[] EVENING_POOL = {
            SceneryMode.RIVER_LAKE,
            SceneryMode.NATURAL_HILLS,
            SceneryMode.VILLAGE,
            SceneryMode.FARM_CROPS
    };

    private static final SceneryMode[] NIGHT_POOL = {
            SceneryMode.URBAN_BUILDINGS,
            SceneryMode.NATURAL_HILLS,
            SceneryMode.RIVER_LAKE,
            SceneryMode.OPEN_SKY
    };

    private AutoSceneryPolicy() {
    }

    @NonNull
    public static SceneryMode resolveNow(int variant) {
        Calendar calendar = Calendar.getInstance();
        return resolve(
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.DAY_OF_YEAR),
                variant
        );
    }

    /**
     * Pure resolver kept public so the day-part policy can be unit-tested without a
     * device clock. The result is always a concrete manual scenery mode, never AUTO.
     */
    @NonNull
    public static SceneryMode resolve(int hourOfDay, int dayOfYear, int variant) {
        int hour = Math.max(0, Math.min(23, hourOfDay));
        int day = Math.max(1, dayOfYear);
        int boundedVariant = Math.max(
                0,
                Math.min(SceneryVariantRuntimeState.VARIANT_COUNT - 1, variant)
        );

        SceneryMode[] pool;
        if (hour >= 5 && hour <= 8) {
            pool = DAWN_POOL;
        } else if (hour >= 9 && hour <= 15) {
            pool = DAY_POOL;
        } else if (hour >= 16 && hour <= 19) {
            pool = EVENING_POOL;
        } else {
            pool = NIGHT_POOL;
        }

        int index = Math.floorMod(day + boundedVariant, pool.length);
        return pool[index];
    }
}
