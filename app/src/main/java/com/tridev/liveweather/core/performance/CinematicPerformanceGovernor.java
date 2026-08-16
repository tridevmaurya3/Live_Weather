package com.tridev.liveweather.core.performance;

import android.content.Context;

import androidx.annotation.NonNull;

import com.tridev.liveweather.data.local.PerformancePreferences;

/**
 * Mobile-first governor for the shared Hero / Live Wallpaper renderer.
 *
 * Weather truth is never reduced by this class. It only chooses frame pacing
 * and secondary shader detail so realistic scenes remain smooth on phones.
 */
public final class CinematicPerformanceGovernor {

    public enum Surface {
        APP_HERO,
        LIVE_WALLPAPER
    }

    public enum Tier {
        CINEMATIC,
        BALANCED,
        ECO
    }

    private CinematicPerformanceGovernor() {
    }

    @NonNull
    public static Profile resolve(
            @NonNull Context context,
            @NonNull PerformancePreferences.Mode mode,
            boolean batteryAdaptive,
            @NonNull Surface surface
    ) {
        boolean powerSave = PerformancePolicy.isPowerSave(context);
        boolean lowBattery = PerformancePolicy.isLowBattery(context);

        if (mode == PerformancePreferences.Mode.BATTERY) {
            return eco(surface);
        }

        if (mode == PerformancePreferences.Mode.SMOOTH) {
            if (batteryAdaptive && powerSave) {
                return balanced(surface);
            }
            return cinematic(surface);
        }

        if (batteryAdaptive && powerSave) {
            return eco(surface);
        }
        if (batteryAdaptive && lowBattery) {
            return balanced(surface);
        }
        return balanced(surface);
    }

    @NonNull
    private static Profile cinematic(@NonNull Surface surface) {
        return new Profile(
                Tier.CINEMATIC,
                surface == Surface.APP_HERO ? 20L : 24L,
                1.00f
        );
    }

    @NonNull
    private static Profile balanced(@NonNull Surface surface) {
        return new Profile(
                Tier.BALANCED,
                surface == Surface.APP_HERO ? 33L : 33L,
                0.82f
        );
    }

    @NonNull
    private static Profile eco(@NonNull Surface surface) {
        return new Profile(
                Tier.ECO,
                surface == Surface.APP_HERO ? 50L : 66L,
                0.58f
        );
    }

    public static final class Profile {
        @NonNull public final Tier tier;
        public final long frameIntervalMillis;
        public final float detailScale;

        private Profile(
                @NonNull Tier tier,
                long frameIntervalMillis,
                float detailScale
        ) {
            this.tier = tier;
            this.frameIntervalMillis = Math.max(16L, frameIntervalMillis);
            this.detailScale = clamp(detailScale, 0.50f, 1.00f);
        }

        @NonNull
        public String label() {
            return tier.name();
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
