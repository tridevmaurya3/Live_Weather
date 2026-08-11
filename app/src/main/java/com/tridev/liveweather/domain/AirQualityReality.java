package com.tridev.liveweather.domain;

import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.AirQualityResponse;

/** Shared renderer-facing air-quality reality rules. */
public final class AirQualityReality {

    private AirQualityReality() {
    }

    public static double hazeIntensity(@Nullable AirQualityResponse response) {
        if (response == null || response.getCurrent() == null) {
            return 0d;
        }
        AirQualityResponse.CurrentAirQuality current = response.getCurrent();
        double aod = clamp(value(current.getAerosolOpticalDepth()) / 1.2d, 0d, 1d);
        double pm25 = clamp(value(current.getPm25()) / 120d, 0d, 1d);
        double dust = clamp(value(current.getDust()) / 180d, 0d, 1d);
        return clamp(Math.max(aod * 0.85d, Math.max(pm25 * 0.72d, dust * 0.78d)), 0d, 1d);
    }

    private static double value(Double value) {
        return value == null ? 0d : Math.max(0d, value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
