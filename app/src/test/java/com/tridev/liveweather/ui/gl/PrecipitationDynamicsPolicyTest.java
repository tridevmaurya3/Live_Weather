package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PrecipitationDynamicsPolicyTest {

    @Test public void heavyWindDrivenRainFallsFasterThanDrizzle() {
        float drizzle = PrecipitationDynamicsPolicy.fallSpeedScale(0f, 0.3f, 0.1f);
        float downpour = PrecipitationDynamicsPolicy.fallSpeedScale(1f, 0f, 0.8f);
        assertTrue(downpour > drizzle);
        assertTrue(downpour <= 1.56f);
    }

    @Test public void calmRainKeepsNearVerticalLean() {
        assertEquals(0.018f, PrecipitationDynamicsPolicy.leanScale(0f, 0f), 0.0001f);
        assertTrue(PrecipitationDynamicsPolicy.leanScale(1f, 1f) > 0.20f);
    }

    @Test public void performanceProfileCanRemoveNearRainAndWetGlass() {
        assertEquals(0f, PrecipitationDynamicsPolicy.nearLayerStrength(1f, 0.5f), 0f);
        assertEquals(0f, PrecipitationDynamicsPolicy.wetGlassStrength(1f, 0f, 0.5f), 0f);
        assertTrue(PrecipitationDynamicsPolicy.nearLayerStrength(1f, 1f) > 0f);
    }

    @Test public void drizzleDoesNotCreateHeavyWetGlass() {
        float light = PrecipitationDynamicsPolicy.wetGlassStrength(0f, 0.35f, 1f);
        float heavy = PrecipitationDynamicsPolicy.wetGlassStrength(0.95f, 0f, 1f);
        assertEquals(0f, light, 0.0001f);
        assertTrue(heavy > 0.8f);
    }

    @Test public void snowTurbulenceIsBounded() {
        assertEquals(0f, PrecipitationDynamicsPolicy.snowTurbulence(0f, 0f, 0f), 0f);
        assertTrue(PrecipitationDynamicsPolicy.snowTurbulence(1f, 1f, 1f) <= 1f);
    }
}
