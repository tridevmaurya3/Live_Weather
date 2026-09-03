package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;

import java.util.Calendar;

/**
 * Deterministic presentation-only policy used when the user selects Auto scenery.
 *
 * S10 can consider authoritative current-condition context when choosing only the
 * background scenery identity. It never creates, suppresses or reinterprets weather.
 * Resolution happens outside the OpenGL frame hot path, so there is no per-frame
 * clock, disk or network work.
 *
 * Stage 9 keeps meteorological fog and pollution/dust haze separate all the way
 * through Auto scenery selection. Haze may choose a lower-contrast exposed scene,
 * but it can no longer masquerade as fog and force fog-specific scenery.
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

    private static final SceneryMode[] STORM_POOL = {
            SceneryMode.URBAN_BUILDINGS,
            SceneryMode.NATURAL_HILLS,
            SceneryMode.OPEN_SKY
    };

    private static final SceneryMode[] RAIN_POOL = {
            SceneryMode.RIVER_LAKE,
            SceneryMode.VILLAGE,
            SceneryMode.URBAN_BUILDINGS,
            SceneryMode.NATURAL_HILLS
    };

    private static final SceneryMode[] SNOW_POOL = {
            SceneryMode.NATURAL_HILLS,
            SceneryMode.VILLAGE,
            SceneryMode.OPEN_SKY
    };

    private static final SceneryMode[] FOG_POOL = {
            SceneryMode.NATURAL_HILLS,
            SceneryMode.RIVER_LAKE,
            SceneryMode.VILLAGE
    };

    private static final SceneryMode[] HAZE_POOL = {
            SceneryMode.OPEN_SKY,
            SceneryMode.URBAN_BUILDINGS,
            SceneryMode.NATURAL_HILLS
    };

    private static final SceneryMode[] CLOUDY_POOL = {
            SceneryMode.NATURAL_HILLS,
            SceneryMode.RIVER_LAKE,
            SceneryMode.VILLAGE,
            SceneryMode.URBAN_BUILDINGS
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
     * Current-condition-aware resolver used by the shared app/wallpaper pipeline when
     * fresh observed truth arrives. Inputs are already-resolved current values, never
     * forecast probability. The result is always a concrete manual scenery mode.
     */
    @NonNull
    public static SceneryMode resolveNowForCurrentTruth(
            int variant,
            float cloudCover,
            float rainIntensity,
            float drizzleIntensity,
            float snowIntensity,
            float stormIntensity,
            float fogIntensity,
            float hazeIntensity
    ) {
        Calendar calendar = Calendar.getInstance();
        return resolveForCurrentTruth(
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.DAY_OF_YEAR),
                variant,
                cloudCover,
                rainIntensity,
                drizzleIntensity,
                snowIntensity,
                stormIntensity,
                fogIntensity,
                hazeIntensity
        );
    }

    /**
     * Pure current-condition resolver kept public for unit tests. Condition context only
     * changes presentation scenery choice; it does not change any weather intensity.
     */
    @NonNull
    public static SceneryMode resolveForCurrentTruth(
            int hourOfDay,
            int dayOfYear,
            int variant,
            float cloudCover,
            float rainIntensity,
            float drizzleIntensity,
            float snowIntensity,
            float stormIntensity,
            float fogIntensity,
            float hazeIntensity
    ) {
        int day = Math.max(1, dayOfYear);
        int boundedVariant = boundVariant(variant);
        float storm = clamp01(stormIntensity);
        float snow = clamp01(snowIntensity);
        float rain = Math.max(clamp01(rainIntensity), clamp01(drizzleIntensity) * 0.72f);
        float fog = clamp01(fogIntensity);
        float haze = clamp01(hazeIntensity);
        float cloud = clamp01(cloudCover);

        if (storm >= 0.20f) {
            return choose(STORM_POOL, day, boundedVariant, 5);
        }
        if (snow >= 0.08f) {
            return choose(SNOW_POOL, day, boundedVariant, 11);
        }
        if (fog >= 0.30f) {
            return choose(FOG_POOL, day, boundedVariant, 17);
        }
        if (rain >= 0.10f) {
            return choose(RAIN_POOL, day, boundedVariant, 23);
        }
        if (haze >= 0.42f) {
            return choose(HAZE_POOL, day, boundedVariant, 27);
        }
        if (cloud >= 0.76f) {
            return choose(CLOUDY_POOL, day, boundedVariant, 29);
        }
        return resolve(hourOfDay, day, boundedVariant);
    }

    /**
     * Pure day-part resolver kept public so the fallback policy can be unit-tested
     * without a device clock. The result is always a concrete manual scenery mode.
     */
    @NonNull
    public static SceneryMode resolve(int hourOfDay, int dayOfYear, int variant) {
        int hour = Math.max(0, Math.min(23, hourOfDay));
        int day = Math.max(1, dayOfYear);
        int boundedVariant = boundVariant(variant);

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

        return choose(pool, day, boundedVariant, 0);
    }

    @NonNull
    private static SceneryMode choose(
            @NonNull SceneryMode[] pool,
            int dayOfYear,
            int variant,
            int salt
    ) {
        int index = Math.floorMod(dayOfYear + variant + salt, pool.length);
        return pool[index];
    }

    private static int boundVariant(int variant) {
        return Math.max(
                0,
                Math.min(SceneryVariantRuntimeState.VARIANT_COUNT - 1, variant)
        );
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
