package com.tridev.liveweather.ui.gl;

import androidx.annotation.NonNull;

/**
 * GPU-facing normalized scene values.
 *
 * Snapshots produced by GlRealityAdapter are treated as immutable weather truth.
 * The shared GL pipeline may also create private reusable copies whose primitive
 * fields are updated in-place for temporal smoothing. This avoids allocating a
 * new Java snapshot on every animation frame.
 */
public final class GlSceneSnapshot {

    public float topR;
    public float topG;
    public float topB;
    public float midR;
    public float midG;
    public float midB;
    public float horizonR;
    public float horizonG;
    public float horizonB;

    public float sunX;
    public float sunY;
    public float sunVisibility;
    public float sunAltitude;

    public float moonX;
    public float moonY;
    public float moonVisibility;
    public float moonIllumination;
    public float moonPhaseAngleRadians;
    public float moonAltitude;

    public float starVisibility;

    /** Observer latitude and local sidereal angle used by the real-time star projection. */
    public float observerLatitudeRadians;
    public float localSiderealRadians;

    public float cloudCover;
    public float cloudDensity;
    public float cloudFarLayer;
    public float cloudMidLayer;
    public float cloudNearLayer;
    public float cloudStormCeiling;
    public float cloudBrightness;
    public float rainIntensity;
    public float drizzleIntensity;
    public float snowIntensity;
    public float fogIntensity;
    public float stormIntensity;
    public float airHazeIntensity;
    public float windStrength;
    public float windDirectionRadians;
    public float sceneLight;

    /**
     * Current apparent-temperature signal in the range -1..1.
     * Negative means genuinely cold-feeling air, positive means genuinely hot-feeling air.
     * This is presentation context only; it never changes the resolved weather condition.
     */
    public float thermalBias;

    public float visibilityFactor;
    public float parallax;

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
            float observerLatitudeRadians,
            float localSiderealRadians,
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
            float thermalBias,
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
        this.observerLatitudeRadians = observerLatitudeRadians;
        this.localSiderealRadians = localSiderealRadians;
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
        this.thermalBias = thermalBias;
        this.visibilityFactor = visibilityFactor;
        this.parallax = parallax;
    }

    /** Creates one reusable internal copy; never call this from the per-frame hot path. */
    @NonNull
    static GlSceneSnapshot reusableCopyOf(@NonNull GlSceneSnapshot source) {
        return new GlSceneSnapshot(
                source.topR, source.topG, source.topB,
                source.midR, source.midG, source.midB,
                source.horizonR, source.horizonG, source.horizonB,
                source.sunX, source.sunY, source.sunVisibility, source.sunAltitude,
                source.moonX, source.moonY, source.moonVisibility, source.moonIllumination,
                source.moonPhaseAngleRadians, source.moonAltitude,
                source.starVisibility,
                source.observerLatitudeRadians,
                source.localSiderealRadians,
                source.cloudCover,
                source.cloudDensity,
                source.cloudFarLayer,
                source.cloudMidLayer,
                source.cloudNearLayer,
                source.cloudStormCeiling,
                source.cloudBrightness,
                source.rainIntensity,
                source.drizzleIntensity,
                source.snowIntensity,
                source.fogIntensity,
                source.stormIntensity,
                source.airHazeIntensity,
                source.windStrength,
                source.windDirectionRadians,
                source.sceneLight,
                source.thermalBias,
                source.visibilityFactor,
                source.parallax
        );
    }

    void copyFrom(@NonNull GlSceneSnapshot source) {
        topR = source.topR;
        topG = source.topG;
        topB = source.topB;
        midR = source.midR;
        midG = source.midG;
        midB = source.midB;
        horizonR = source.horizonR;
        horizonG = source.horizonG;
        horizonB = source.horizonB;
        sunX = source.sunX;
        sunY = source.sunY;
        sunVisibility = source.sunVisibility;
        sunAltitude = source.sunAltitude;
        moonX = source.moonX;
        moonY = source.moonY;
        moonVisibility = source.moonVisibility;
        moonIllumination = source.moonIllumination;
        moonPhaseAngleRadians = source.moonPhaseAngleRadians;
        moonAltitude = source.moonAltitude;
        starVisibility = source.starVisibility;
        observerLatitudeRadians = source.observerLatitudeRadians;
        localSiderealRadians = source.localSiderealRadians;
        cloudCover = source.cloudCover;
        cloudDensity = source.cloudDensity;
        cloudFarLayer = source.cloudFarLayer;
        cloudMidLayer = source.cloudMidLayer;
        cloudNearLayer = source.cloudNearLayer;
        cloudStormCeiling = source.cloudStormCeiling;
        cloudBrightness = source.cloudBrightness;
        rainIntensity = source.rainIntensity;
        drizzleIntensity = source.drizzleIntensity;
        snowIntensity = source.snowIntensity;
        fogIntensity = source.fogIntensity;
        stormIntensity = source.stormIntensity;
        airHazeIntensity = source.airHazeIntensity;
        windStrength = source.windStrength;
        windDirectionRadians = source.windDirectionRadians;
        sceneLight = source.sceneLight;
        thermalBias = source.thermalBias;
        visibilityFactor = source.visibilityFactor;
        parallax = source.parallax;
    }

    /**
     * Reuses this object as a renderer-specific view of the shared smoothed scene.
     * The behavior matches withVisualOptions(), but without allocating a new object.
     */
    void copyVisualOptionsFrom(
            @NonNull GlSceneSnapshot source,
            boolean clouds,
            boolean rain,
            boolean lightning,
            boolean snow,
            boolean fog,
            boolean stars
    ) {
        copyFrom(source);

        if (!clouds) {
            cloudCover = 0f;
            cloudDensity = 0f;
            cloudFarLayer = 0f;
            cloudMidLayer = 0f;
            cloudNearLayer = 0f;
            cloudStormCeiling = 0f;
        }
        if (!rain) {
            rainIntensity = 0f;
            drizzleIntensity = 0f;
        }
        if (!lightning) {
            stormIntensity = 0f;
        }
        if (!snow) {
            snowIntensity = 0f;
        }
        if (!fog) {
            fogIntensity = 0f;
        }
        if (!stars) {
            starVisibility = 0f;
        }
    }

    /**
     * Compatibility helper for non-hot-path callers. The active shared pipeline
     * uses reusable copies instead so animation does not allocate every frame.
     */
    public GlSceneSnapshot withVisualOptions(
            boolean clouds,
            boolean rain,
            boolean lightning,
            boolean snow,
            boolean fog,
            boolean stars
    ) {
        GlSceneSnapshot copy = reusableCopyOf(this);
        copy.copyVisualOptionsFrom(this, clouds, rain, lightning, snow, fog, stars);
        return copy;
    }
}
