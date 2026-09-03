package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WorldLightingSolarIntegrationTest {

    @Test
    public void weakObservedIrradianceDimsSameAtmosphereWithoutChangingWeatherTruth() {
        double neutral = resolve(1d, 1d, 0.5d, false, 0L);
        double weakSolar = resolve(0.66d, 0.10d, 0.82d, true, 0L);

        assertTrue(weakSolar < neutral - 0.025d);
        assertTrue(weakSolar > 0.45d);
    }

    @Test
    public void diffuseBrightSkyRetainsUsefulAmbientLight() {
        double diffuse = resolve(0.90d, 0.12d, 0.86d, true, 30_000L);

        assertTrue(diffuse > 0.58d);
        assertTrue(diffuse < 0.82d);
    }

    @Test
    public void strongerDirectBeamAllowsStrongerPassingCloudShadowResponse() {
        double lowDirectA = resolve(0.90d, 0.05d, 0.88d, true, 0L);
        double lowDirectB = resolve(0.90d, 0.05d, 0.88d, true, 60_000L);
        double strongDirectA = resolve(0.96d, 0.95d, 0.18d, true, 0L);
        double strongDirectB = resolve(0.96d, 0.95d, 0.18d, true, 60_000L);

        double lowVariation = Math.abs(lowDirectA - lowDirectB);
        double strongVariation = Math.abs(strongDirectA - strongDirectB);
        assertTrue(strongVariation > lowVariation);
    }

    private static double resolve(
            double solarGlobal,
            double solarDirect,
            double diffuseFraction,
            boolean hasSolar,
            long epochMillis
    ) {
        return WorldLightingController.resolveSceneLight(
                0.82d,
                46d,
                0.88d,
                0.52d,
                0.62d,
                0.60d,
                0.48d,
                0.08d,
                0.78d,
                0d,
                0.02d,
                0.03d,
                solarGlobal,
                solarDirect,
                diffuseFraction,
                hasSolar,
                0.65d,
                1.1d,
                epochMillis
        );
    }
}
