package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;

import com.tridev.liveweather.domain.LiveConditionResolver;
import com.tridev.liveweather.domain.SkyRealityState;

public final class SceneState {

    private final SkyRealityState sky;
    private final LiveConditionResolver.ResolvedCondition condition;
    private final CloudPresenceState cloudPresence;
    private final double rainIntensity;
    private final double drizzleIntensity;
    private final double snowIntensity;
    private final double fogIntensity;
    private final double stormIntensity;
    private final double airHazeIntensity;
    private final double windSpeedKmh;
    private final double windDirectionDegrees;
    private final double windStrength;
    private final double visibilityFactor;
    private final double sunVisibility;
    private final double moonVisibility;
    private final double starVisibility;
    private final double sceneLight;

    /**
     * Primary ODM-1B constructor. New render paths should pass the complete
     * CloudPresenceState so amount, density and depth-layer information stay
     * available to the OpenGL cloud engine.
     */
    public SceneState(
            @NonNull SkyRealityState sky,
            @NonNull LiveConditionResolver.ResolvedCondition condition,
            @NonNull CloudPresenceState cloudPresence,
            double rainIntensity,
            double drizzleIntensity,
            double snowIntensity,
            double fogIntensity,
            double stormIntensity,
            double airHazeIntensity,
            double windSpeedKmh,
            double windDirectionDegrees,
            double windStrength,
            double visibilityFactor,
            double sunVisibility,
            double moonVisibility,
            double starVisibility,
            double sceneLight
    ) {
        this.sky = sky;
        this.condition = condition;
        this.cloudPresence = cloudPresence;
        this.rainIntensity = rainIntensity;
        this.drizzleIntensity = drizzleIntensity;
        this.snowIntensity = snowIntensity;
        this.fogIntensity = fogIntensity;
        this.stormIntensity = stormIntensity;
        this.airHazeIntensity = airHazeIntensity;
        this.windSpeedKmh = windSpeedKmh;
        this.windDirectionDegrees = windDirectionDegrees;
        this.windStrength = windStrength;
        this.visibilityFactor = visibilityFactor;
        this.sunVisibility = sunVisibility;
        this.moonVisibility = moonVisibility;
        this.starVisibility = starVisibility;
        this.sceneLight = sceneLight;
    }

    /**
     * Backward-compatible constructor for legacy/Canvas transition code that
     * still interpolates only a normalized cloud amount.
     *
     * This keeps old callers compiling during the OpenGL migration while the
     * active GPU path continues to use the full CloudPresenceState constructor.
     */
    public SceneState(
            @NonNull SkyRealityState sky,
            @NonNull LiveConditionResolver.ResolvedCondition condition,
            double cloudCover,
            double rainIntensity,
            double drizzleIntensity,
            double snowIntensity,
            double fogIntensity,
            double stormIntensity,
            double airHazeIntensity,
            double windSpeedKmh,
            double windDirectionDegrees,
            double windStrength,
            double visibilityFactor,
            double sunVisibility,
            double moonVisibility,
            double starVisibility,
            double sceneLight
    ) {
        this(
                sky,
                condition,
                legacyCloudPresence(cloudCover, rainIntensity, drizzleIntensity, snowIntensity, stormIntensity),
                rainIntensity,
                drizzleIntensity,
                snowIntensity,
                fogIntensity,
                stormIntensity,
                airHazeIntensity,
                windSpeedKmh,
                windDirectionDegrees,
                windStrength,
                visibilityFactor,
                sunVisibility,
                moonVisibility,
                starVisibility,
                sceneLight
        );
    }

    @NonNull
    public SkyRealityState getSky() {
        return sky;
    }

    @NonNull
    public LiveConditionResolver.ResolvedCondition getCondition() {
        return condition;
    }

    @NonNull
    public CloudPresenceState getCloudPresence() {
        return cloudPresence;
    }

    /**
     * Backward-compatible normalized cloud amount used by existing renderers.
     */
    public double getCloudCover() {
        return cloudPresence.getCloudAmount();
    }

    public double getRainIntensity() {
        return rainIntensity;
    }

    public double getDrizzleIntensity() {
        return drizzleIntensity;
    }

    public double getSnowIntensity() {
        return snowIntensity;
    }

    public double getFogIntensity() {
        return fogIntensity;
    }

    public double getStormIntensity() {
        return stormIntensity;
    }

    public double getAirHazeIntensity() {
        return airHazeIntensity;
    }

    /**
     * Renderer-facing wind speed is float because Android Canvas motion math is
     * float-based. The state keeps double precision internally and only narrows
     * at this presentation boundary.
     */
    public float getWindSpeedKmh() {
        return (float) windSpeedKmh;
    }

    public double getWindDirectionDegrees() {
        return windDirectionDegrees;
    }

    public double getWindStrength() {
        return windStrength;
    }

    public double getVisibilityFactor() {
        return visibilityFactor;
    }

    public double getSunVisibility() {
        return sunVisibility;
    }

    public double getMoonVisibility() {
        return moonVisibility;
    }

    public double getStarVisibility() {
        return starVisibility;
    }

    public double getSceneLight() {
        return sceneLight;
    }

    @NonNull
    private static CloudPresenceState legacyCloudPresence(
            double cloudCover,
            double rainIntensity,
            double drizzleIntensity,
            double snowIntensity,
            double stormIntensity
    ) {
        double amount = clamp01(cloudCover);
        double precipitation = clamp01(Math.max(
                Math.max(rainIntensity, drizzleIntensity),
                snowIntensity
        ));
        double storm = clamp01(stormIntensity);

        CloudPresenceState.Mode mode;
        if (storm > 0.08d) {
            mode = CloudPresenceState.Mode.STORM;
        } else if (precipitation > 0.06d) {
            mode = CloudPresenceState.Mode.PRECIPITATION;
        } else if (amount >= 0.82d) {
            mode = CloudPresenceState.Mode.OVERCAST;
        } else if (amount >= 0.58d) {
            mode = CloudPresenceState.Mode.BROKEN;
        } else if (amount >= 0.25d) {
            mode = CloudPresenceState.Mode.SCATTERED;
        } else if (amount >= 0.06d) {
            mode = CloudPresenceState.Mode.WISPS;
        } else {
            mode = CloudPresenceState.Mode.CLEAR;
        }

        double density = clamp01(amount * 0.72d + precipitation * 0.18d + storm * 0.28d);
        double far = amount < 0.05d ? 0d : clamp01((amount - 0.02d) / 0.78d * 0.74d);
        double mid = amount < 0.11d ? 0d : clamp01((amount - 0.09d) / 0.72d);
        double near = amount < 0.27d
                ? 0d
                : clamp01((amount - 0.24d) / 0.76d + precipitation * 0.20d + storm * 0.22d);
        double ceiling = clamp01(
                storm * 0.86d
                        + Math.max(0d, amount - 0.72d) * 0.58d
                        + precipitation * 0.18d
        );
        double brightness = clamp01(1d - storm * 0.58d - precipitation * 0.20d);

        return new CloudPresenceState(
                mode,
                amount,
                density,
                far,
                mid,
                near,
                ceiling,
                brightness
        );
    }

    private static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }
}
