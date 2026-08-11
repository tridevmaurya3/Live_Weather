package com.tridev.liveweather.ui.gl;

/**
 * Immutable GPU-facing snapshot. All values are normalized before they cross
 * into the OpenGL renderer so the render loop never needs network/location work.
 */
public final class GlSceneSnapshot {

    public final float topR;
    public final float topG;
    public final float topB;
    public final float midR;
    public final float midG;
    public final float midB;
    public final float horizonR;
    public final float horizonG;
    public final float horizonB;

    public final float sunX;
    public final float sunY;
    public final float sunVisibility;
    public final float sunAltitude;

    public final float moonX;
    public final float moonY;
    public final float moonVisibility;
    public final float moonIllumination;
    public final float moonPhaseAngleRadians;
    public final float moonAltitude;

    public final float starVisibility;
    public final float cloudCover;
    public final float cloudDensity;
    public final float cloudFarLayer;
    public final float cloudMidLayer;
    public final float cloudNearLayer;
    public final float cloudStormCeiling;
    public final float cloudBrightness;
    public final float rainIntensity;
    public final float drizzleIntensity;
    public final float snowIntensity;
    public final float fogIntensity;
    public final float stormIntensity;
    public final float airHazeIntensity;
    public final float windStrength;
    public final float windDirectionRadians;
    public final float sceneLight;
    public final float visibilityFactor;
    public final float parallax;

    public GlSceneSnapshot(
            float topR,
            float topG,
            float topB,
            float midR,
            float midG,
            float midB,
            float horizonR,
            float horizonG,
            float horizonB,
            float sunX,
            float sunY,
            float sunVisibility,
            float sunAltitude,
            float moonX,
            float moonY,
            float moonVisibility,
            float moonIllumination,
            float moonPhaseAngleRadians,
            float moonAltitude,
            float starVisibility,
            float cloudCover,
            float cloudDensity,
            float cloudFarLayer,
            float cloudMidLayer,
            float cloudNearLayer,
            float cloudStormCeiling,
            float cloudBrightness,
            float rainIntensity,
            float drizzleIntensity,
            float snowIntensity,
            float fogIntensity,
            float stormIntensity,
            float airHazeIntensity,
            float windStrength,
            float windDirectionRadians,
            float sceneLight,
            float visibilityFactor,
            float parallax
    ) {
        this.topR = topR;
        this.topG = topG;
        this.topB = topB;
        this.midR = midR;
        this.midG = midG;
        this.midB = midB;
        this.horizonR = horizonR;
        this.horizonG = horizonG;
        this.horizonB = horizonB;
        this.sunX = sunX;
        this.sunY = sunY;
        this.sunVisibility = sunVisibility;
        this.sunAltitude = sunAltitude;
        this.moonX = moonX;
        this.moonY = moonY;
        this.moonVisibility = moonVisibility;
        this.moonIllumination = moonIllumination;
        this.moonPhaseAngleRadians = moonPhaseAngleRadians;
        this.moonAltitude = moonAltitude;
        this.starVisibility = starVisibility;
        this.cloudCover = cloudCover;
        this.cloudDensity = cloudDensity;
        this.cloudFarLayer = cloudFarLayer;
        this.cloudMidLayer = cloudMidLayer;
        this.cloudNearLayer = cloudNearLayer;
        this.cloudStormCeiling = cloudStormCeiling;
        this.cloudBrightness = cloudBrightness;
        this.rainIntensity = rainIntensity;
        this.drizzleIntensity = drizzleIntensity;
        this.snowIntensity = snowIntensity;
        this.fogIntensity = fogIntensity;
        this.stormIntensity = stormIntensity;
        this.airHazeIntensity = airHazeIntensity;
        this.windStrength = windStrength;
        this.windDirectionRadians = windDirectionRadians;
        this.sceneLight = sceneLight;
        this.visibilityFactor = visibilityFactor;
        this.parallax = parallax;
    }

    public GlSceneSnapshot withVisualOptions(
            boolean clouds,
            boolean rain,
            boolean lightning,
            boolean snow,
            boolean fog,
            boolean stars
    ) {
        float cloudAmount = clouds ? cloudCover : 0f;
        float cloudLayerDensity = clouds ? cloudDensity : 0f;
        float far = clouds ? cloudFarLayer : 0f;
        float mid = clouds ? cloudMidLayer : 0f;
        float near = clouds ? cloudNearLayer : 0f;
        float ceiling = clouds ? cloudStormCeiling : 0f;

        return new GlSceneSnapshot(
                topR, topG, topB,
                midR, midG, midB,
                horizonR, horizonG, horizonB,
                sunX, sunY, sunVisibility, sunAltitude,
                moonX, moonY, moonVisibility, moonIllumination,
                moonPhaseAngleRadians, moonAltitude,
                stars ? starVisibility : 0f,
                cloudAmount,
                cloudLayerDensity,
                far,
                mid,
                near,
                ceiling,
                cloudBrightness,
                rain ? rainIntensity : 0f,
                rain ? drizzleIntensity : 0f,
                snow ? snowIntensity : 0f,
                fog ? fogIntensity : 0f,
                lightning ? stormIntensity : 0f,
                airHazeIntensity,
                windStrength,
                windDirectionRadians,
                sceneLight,
                visibilityFactor,
                parallax
        );
    }
}
