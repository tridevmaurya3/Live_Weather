package com.tridev.liveweather.domain.scene;

import androidx.annotation.NonNull;

import com.tridev.liveweather.domain.LiveConditionResolver;
import com.tridev.liveweather.domain.SkyRealityState;

public final class SceneState {

    private final SkyRealityState sky;
    private final LiveConditionResolver.ResolvedCondition condition;
    private final double cloudCover;
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
        this.sky = sky;
        this.condition = condition;
        this.cloudCover = cloudCover;
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

    @NonNull public SkyRealityState getSky() { return sky; }
    @NonNull public LiveConditionResolver.ResolvedCondition getCondition() { return condition; }
    public double getCloudCover() { return cloudCover; }
    public double getRainIntensity() { return rainIntensity; }
    public double getDrizzleIntensity() { return drizzleIntensity; }
    public double getSnowIntensity() { return snowIntensity; }
    public double getFogIntensity() { return fogIntensity; }
    public double getStormIntensity() { return stormIntensity; }
    public double getAirHazeIntensity() { return airHazeIntensity; }
    public double getWindSpeedKmh() { return windSpeedKmh; }
    public double getWindDirectionDegrees() { return windDirectionDegrees; }
    public double getWindStrength() { return windStrength; }
    public double getVisibilityFactor() { return visibilityFactor; }
    public double getSunVisibility() { return sunVisibility; }
    public double getMoonVisibility() { return moonVisibility; }
    public double getStarVisibility() { return starVisibility; }
    public double getSceneLight() { return sceneLight; }
}
