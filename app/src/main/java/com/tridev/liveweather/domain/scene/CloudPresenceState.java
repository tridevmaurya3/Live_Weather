package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;

/**
 * Normalized cloud scene contract shared by the weather reality composer and
 * renderers. Values are 0..1 and describe presence, not pixel appearance.
 */
public final class CloudPresenceState {

    public enum Mode {
        CLEAR,
        WISPS,
        SCATTERED,
        BROKEN,
        OVERCAST,
        PRECIPITATION,
        STORM
    }

    @NonNull
    private final Mode mode;
    private final double cloudAmount;
    private final double density;
    private final double farLayer;
    private final double midLayer;
    private final double nearLayer;
    private final double stormCeiling;
    private final double brightness;

    public CloudPresenceState(
            @NonNull Mode mode,
            double cloudAmount,
            double density,
            double farLayer,
            double midLayer,
            double nearLayer,
            double stormCeiling,
            double brightness
    ) {
        this.mode = mode;
        this.cloudAmount = clamp01(cloudAmount);
        this.density = clamp01(density);
        this.farLayer = clamp01(farLayer);
        this.midLayer = clamp01(midLayer);
        this.nearLayer = clamp01(nearLayer);
        this.stormCeiling = clamp01(stormCeiling);
        this.brightness = clamp01(brightness);
    }

    @NonNull
    public Mode getMode() {
        return mode;
    }

    public double getCloudAmount() {
        return cloudAmount;
    }

    public double getDensity() {
        return density;
    }

    public double getFarLayer() {
        return farLayer;
    }

    public double getMidLayer() {
        return midLayer;
    }

    public double getNearLayer() {
        return nearLayer;
    }

    public double getStormCeiling() {
        return stormCeiling;
    }

    public double getBrightness() {
        return brightness;
    }

    private static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }
}
