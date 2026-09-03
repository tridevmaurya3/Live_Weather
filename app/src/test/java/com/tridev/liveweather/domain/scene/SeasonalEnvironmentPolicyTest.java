package com.tridev.liveweather.domain.scene;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SeasonalEnvironmentPolicyTest {

    @Test
    public void northernHemispherePeaksWarmInMidYear() {
        float july = SeasonalEnvironmentPolicy.resolveSeasonalBias(50d, 203);
        float january = SeasonalEnvironmentPolicy.resolveSeasonalBias(50d, 20);

        assertTrue(july > 0.90f);
        assertTrue(january < -0.90f);
    }

    @Test
    public void southernHemisphereInvertsNorthernPhase() {
        float northernJuly = SeasonalEnvironmentPolicy.resolveSeasonalBias(45d, 203);
        float southernJuly = SeasonalEnvironmentPolicy.resolveSeasonalBias(-45d, 203);

        assertTrue(northernJuly > 0.90f);
        assertTrue(southernJuly < -0.90f);
    }

    @Test
    public void tropicalLatitudeDoesNotPretendFourStrongSeasons() {
        float january = SeasonalEnvironmentPolicy.resolveSeasonalBias(5d, 20);
        float july = SeasonalEnvironmentPolicy.resolveSeasonalBias(5d, 203);

        assertTrue(Math.abs(january) < 0.01f);
        assertTrue(Math.abs(july) < 0.01f);
    }

    @Test
    public void seasonCannotInvertStrongLiveHeat() {
        float result = SeasonalEnvironmentPolicy.applyToThermal(0.85f, 50d, epochForDay(20));
        assertTrue(result > 0.70f);
    }

    @Test
    public void seasonCannotInvertStrongLiveCold() {
        float result = SeasonalEnvironmentPolicy.applyToThermal(-0.85f, 50d, epochForDay(203));
        assertTrue(result < -0.70f);
    }

    @Test
    public void materialSignalAlwaysRemainsBounded() {
        float hot = SeasonalEnvironmentPolicy.applyToThermal(1f, 70d, epochForDay(203));
        float cold = SeasonalEnvironmentPolicy.applyToThermal(-1f, 70d, epochForDay(20));

        assertTrue(hot <= 1f && hot >= -1f);
        assertTrue(cold <= 1f && cold >= -1f);
    }

    private static long epochForDay(int dayOfYear) {
        return (dayOfYear - 1L) * 86_400_000L;
    }
}
