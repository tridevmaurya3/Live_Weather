package com.tridev.liveweather.domain;

public final class CelestialDayState {

    private final String dayLabel;
    private final String phaseName;
    private final double illuminationPercent;
    private final String sunrise;
    private final String sunset;
    private final String moonrise;
    private final String moonset;
    private final boolean waxing;

    public CelestialDayState(
            String dayLabel,
            String phaseName,
            double illuminationPercent,
            String sunrise,
            String sunset,
            String moonrise,
            String moonset,
            boolean waxing
    ) {
        this.dayLabel = dayLabel;
        this.phaseName = phaseName;
        this.illuminationPercent = illuminationPercent;
        this.sunrise = sunrise;
        this.sunset = sunset;
        this.moonrise = moonrise;
        this.moonset = moonset;
        this.waxing = waxing;
    }

    public String getDayLabel() {
        return dayLabel;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public double getIlluminationPercent() {
        return illuminationPercent;
    }

    public String getSunrise() {
        return sunrise;
    }

    public String getSunset() {
        return sunset;
    }

    public String getMoonrise() {
        return moonrise;
    }

    public String getMoonset() {
        return moonset;
    }

    public boolean isWaxing() {
        return waxing;
    }
}
