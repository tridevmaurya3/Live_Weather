package com.tridev.liveweather.domain;

/**
 * Shared astronomical/environment state for the app, widgets and live wallpaper.
 *
 * Renderer-facing values are normalized so the UI and wallpaper use one
 * astronomy/weather reality source.
 */
public final class SkyRealityState {

    private final String skyStage;
    private final double sunAltitude;
    private final double sunAzimuth;
    private final double moonAltitude;
    private final double moonAzimuth;
    private final double moonIlluminationPercent;
    private final double moonPhaseAngleDegrees;
    private final String moonPhaseName;
    private final int starVisibilityPercent;
    private final int ambientLightPercent;

    public SkyRealityState(
            String skyStage,
            double sunAltitude,
            double sunAzimuth,
            double moonAltitude,
            double moonAzimuth,
            double moonIlluminationPercent,
            double moonPhaseAngleDegrees,
            String moonPhaseName,
            int starVisibilityPercent,
            int ambientLightPercent
    ) {
        this.skyStage = skyStage;
        this.sunAltitude = sunAltitude;
        this.sunAzimuth = sunAzimuth;
        this.moonAltitude = moonAltitude;
        this.moonAzimuth = moonAzimuth;
        this.moonIlluminationPercent = moonIlluminationPercent;
        this.moonPhaseAngleDegrees = moonPhaseAngleDegrees;
        this.moonPhaseName = moonPhaseName;
        this.starVisibilityPercent = starVisibilityPercent;
        this.ambientLightPercent = ambientLightPercent;
    }

    public String getSkyStage() {
        return skyStage;
    }

    public double getSunAltitude() {
        return sunAltitude;
    }

    public double getSunAzimuth() {
        return sunAzimuth;
    }

    public double getMoonAltitude() {
        return moonAltitude;
    }

    public double getMoonAzimuth() {
        return moonAzimuth;
    }

    public double getMoonIlluminationPercent() {
        return moonIlluminationPercent;
    }

    public double getMoonPhaseAngleDegrees() {
        return moonPhaseAngleDegrees;
    }

    public String getMoonPhaseName() {
        return moonPhaseName;
    }

    public int getStarVisibilityPercent() {
        return starVisibilityPercent;
    }

    public int getAmbientLightPercent() {
        return ambientLightPercent;
    }
}
