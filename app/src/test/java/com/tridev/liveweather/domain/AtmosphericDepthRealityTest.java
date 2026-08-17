package com.tridev.liveweather.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AtmosphericDepthRealityTest {

    @Test
    public void clearDryAirKeepsFullDistanceClarity() {
        AtmosphericDepthReality.DepthState state = AtmosphericDepthReality.resolve(
                16_000d,
                45d,
                30d,
                17d,
                0d
        );

        assertEquals(1d, state.getObservedVisibilityFactor(), 0.000001d);
        assertEquals(1d, state.getVisibilityFactor(), 0.000001d);
        assertEquals(0d, state.getMoistureDepth(), 0.000001d);
    }

    @Test
    public void humidNearDewPointAirAddsOnlyMildDepth() {
        AtmosphericDepthReality.DepthState state = AtmosphericDepthReality.resolve(
                16_000d,
                95d,
                28d,
                27d,
                0d
        );

        assertTrue(state.getMoistureDepth() > 0.70d);
        assertTrue(state.getVisibilityFactor() < 1d);
        assertTrue(state.getVisibilityFactor() > 0.80d);
    }

    @Test
    public void lowMeasuredVisibilityDominatesAtmosphericDepth() {
        AtmosphericDepthReality.DepthState state = AtmosphericDepthReality.resolve(
                2_000d,
                45d,
                25d,
                12d,
                0d
        );

        assertEquals(0.125d, state.getObservedVisibilityFactor(), 0.000001d);
        assertTrue(state.getVisibilityFactor() <= 0.125d);
        assertTrue(state.getVisibilityFactor() >= 0.05d);
    }

    @Test
    public void airQualityHazeReducesLongDistanceTransmission() {
        AtmosphericDepthReality.DepthState clean = AtmosphericDepthReality.resolve(
                16_000d,
                50d,
                28d,
                15d,
                0d
        );
        AtmosphericDepthReality.DepthState hazy = AtmosphericDepthReality.resolve(
                16_000d,
                50d,
                28d,
                15d,
                0.80d
        );

        assertTrue(hazy.getVisibilityFactor() < clean.getVisibilityFactor());
        assertEquals(0.80d, hazy.getAirHazeIntensity(), 0.000001d);
    }

    @Test
    public void missingVisibilityDoesNotInventSevereOpacity() {
        AtmosphericDepthReality.DepthState state = AtmosphericDepthReality.resolve(
                null,
                96d,
                24d,
                23.5d,
                0d
        );

        assertEquals(1d, state.getObservedVisibilityFactor(), 0.000001d);
        assertTrue(state.getVisibilityFactor() > 0.80d);
    }

    @Test
    public void missingHumidityAndDewPointLeaveClearVisibilityUntouched() {
        AtmosphericDepthReality.DepthState state = AtmosphericDepthReality.resolve(
                16_000d,
                null,
                30d,
                null,
                0d
        );

        assertEquals(0d, state.getMoistureDepth(), 0.000001d);
        assertEquals(1d, state.getVisibilityFactor(), 0.000001d);
    }

    @Test
    public void visibilityFactorAlwaysRemainsBounded() {
        AtmosphericDepthReality.DepthState extreme = AtmosphericDepthReality.resolve(
                0d,
                100d,
                30d,
                30d,
                10d
        );
        AtmosphericDepthReality.DepthState clear = AtmosphericDepthReality.resolve(
                100_000d,
                0d,
                30d,
                0d,
                -5d
        );

        assertEquals(0.05d, extreme.getVisibilityFactor(), 0.000001d);
        assertEquals(1d, clear.getVisibilityFactor(), 0.000001d);
    }
}
