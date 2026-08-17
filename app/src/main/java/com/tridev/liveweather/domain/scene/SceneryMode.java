package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Stable visual scenery identities for the shared Hero / Live Wallpaper world pass.
 *
 * Scenery is deliberately separate from weather truth. A village, farm, river or city
 * scene must never imply a weather condition and must never be inferred from forecast
 * probability. The persisted key is stable so future renderer upgrades can change the
 * artwork without breaking the user's selected scene.
 */
public enum SceneryMode {
    AUTO("auto", 0f),
    OPEN_SKY("open_sky", 1f),
    NATURAL_HILLS("natural_hills", 2f),
    VILLAGE("village", 3f),
    FARM_CROPS("farm_crops", 4f),
    RIVER_LAKE("river_lake", 5f),
    FLOWERS_GREENERY("flowers_greenery", 6f),
    URBAN_BUILDINGS("urban_buildings", 7f);

    private final String storageKey;
    private final float shaderId;

    SceneryMode(@NonNull String storageKey, float shaderId) {
        this.storageKey = storageKey;
        this.shaderId = shaderId;
    }

    @NonNull
    public String getStorageKey() {
        return storageKey;
    }

    public float getShaderId() {
        return shaderId;
    }

    @NonNull
    public static SceneryMode fromStorage(@Nullable String value) {
        if (value != null) {
            for (SceneryMode mode : values()) {
                if (mode.storageKey.equals(value)) {
                    return mode;
                }
            }
        }
        // Preserve the current pre-scenery look until the user explicitly selects a pack.
        return NATURAL_HILLS;
    }
}
