package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;

/**
 * Process-local cache of the latest authoritative current-condition values used only for
 * Auto Scene presentation resolution.
 *
 * The shared pipeline updates this state when a new resolved snapshot arrives. UI and
 * preference code can then re-resolve Auto after a variation/options change without
 * reading weather storage or performing work in the GL draw loop.
 */
public final class AutoSceneryContextState {

    private static volatile boolean hasTruth;
    private static volatile float cloudCover;
    private static volatile float rainIntensity;
    private static volatile float drizzleIntensity;
    private static volatile float snowIntensity;
    private static volatile float stormIntensity;
    private static volatile float fogIntensity;
    private static volatile float hazeIntensity;

    private AutoSceneryContextState() {
    }

    public static void update(
            float cloud,
            float rain,
            float drizzle,
            float snow,
            float storm,
            float fog,
            float haze
    ) {
        cloudCover = cloud;
        rainIntensity = rain;
        drizzleIntensity = drizzle;
        snowIntensity = snow;
        stormIntensity = storm;
        fogIntensity = fog;
        hazeIntensity = haze;
        hasTruth = true;
    }

    @NonNull
    public static SceneryMode resolve(int variant) {
        if (!hasTruth) {
            return AutoSceneryPolicy.resolveNow(variant);
        }
        return AutoSceneryPolicy.resolveNowForCurrentTruth(
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
}
