package com.tridev.liveweather.core.performance;

import android.content.Context;
import android.os.BatteryManager;
import android.os.PowerManager;

import androidx.annotation.NonNull;

import com.tridev.liveweather.data.local.PerformancePreferences;

/**
 * One battery / frame-rate policy shared by in-app GL and system wallpaper.
 */
public final class PerformancePolicy {

    public static final long SMOOTH_FRAME_MILLIS = 33L;
    public static final long LOW_BATTERY_FRAME_MILLIS = 50L;
    public static final long BATTERY_FRAME_MILLIS = 66L;

    private PerformancePolicy() {
    }

    public static long frameIntervalMillis(
            @NonNull Context context,
            @NonNull PerformancePreferences.Mode mode,
            boolean batteryAdaptive
    ) {
        if (mode == PerformancePreferences.Mode.BATTERY) {
            return BATTERY_FRAME_MILLIS;
        }

        if (!batteryAdaptive) {
            return SMOOTH_FRAME_MILLIS;
        }

        boolean powerSave = isPowerSave(context);
        boolean lowBattery = isLowBattery(context);

        if (mode == PerformancePreferences.Mode.SMOOTH) {
            return powerSave ? LOW_BATTERY_FRAME_MILLIS : SMOOTH_FRAME_MILLIS;
        }

        if (powerSave) return BATTERY_FRAME_MILLIS;
        if (lowBattery) return LOW_BATTERY_FRAME_MILLIS;
        return SMOOTH_FRAME_MILLIS;
    }

    public static boolean isPowerSave(@NonNull Context context) {
        PowerManager powerManager =
                (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager != null && powerManager.isPowerSaveMode();
    }

    public static boolean isLowBattery(@NonNull Context context) {
        BatteryManager batteryManager =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager == null) return false;
        int capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return capacity >= 0 && capacity <= 20;
    }
}
