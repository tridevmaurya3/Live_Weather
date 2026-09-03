package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;

/**
 * Provider-backed vertical cloud structure for the shared app/wallpaper scene.
 *
 * Open-Meteo low/mid/high percentages are used when present. Older cached payloads
 * remain compatible through a conservative total-cloud fallback, so Stage 8 does
 * not invalidate existing WeatherCache snapshots.
 */
public final class CloudLayerProfile {

    private final double high;
    private final double mid;
    private final double low;
    private final boolean providerBacked;

    private CloudLayerProfile(double high, double mid, double low, boolean providerBacked) {
        this.high = clamp01(high);
        this.mid = clamp01(mid);
        this.low = clamp01(low);
        this.providerBacked = providerBacked;
    }

    @NonNull
    public static CloudLayerProfile resolve(
            @Nullable WeatherResponse.CurrentWeather current,
            double totalCloudAmount,
            double precipitation,
            double stormIntensity
    ) {
        double total = clamp01(totalCloudAmount);
        Double highPercent = current == null ? null : current.getCloudCoverHigh();
        Double midPercent = current == null ? null : current.getCloudCoverMid();
        Double lowPercent = current == null ? null : current.getCloudCoverLow();
        boolean providerBacked = highPercent != null || midPercent != null || lowPercent != null;

        double high = highPercent == null ? total * 0.52d : percent(highPercent);
        double mid = midPercent == null ? total * 0.66d : percent(midPercent);
        double low = lowPercent == null ? total * 0.72d : percent(lowPercent);

        double precip = clamp01(precipitation);
        double storm = clamp01(stormIntensity);

        // Current precipitation requires a physically plausible low/mid cloud source.
        low = Math.max(low, precip * 0.68d);
        mid = Math.max(mid, precip * 0.40d);

        // A current thunderstorm may vertically connect several cloud layers, but this
        // never creates storm truth by itself; stormIntensity is already WMO-gated.
        low = Math.max(low, storm * 0.86d);
        mid = Math.max(mid, storm * 0.76d);
        high = Math.max(high, storm * 0.48d);

        return new CloudLayerProfile(high, mid, low, providerBacked);
    }

    public double getHigh() { return high; }
    public double getMid() { return mid; }
    public double getLow() { return low; }
    public boolean isProviderBacked() { return providerBacked; }

    private static double percent(@NonNull Double value) {
        return clamp(value / 100d, 0d, 1d);
    }

    private static double clamp01(double value) {
        return clamp(value, 0d, 1d);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
