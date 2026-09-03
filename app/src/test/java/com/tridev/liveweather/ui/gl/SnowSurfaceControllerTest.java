package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SnowSurfaceControllerTest {

    @Test
    public void observedDepthCreatesSurfacePackWithoutCurrentSnowfall() {
        SnowSurfaceController controller = SnowSurfaceController.isolatedForTest();

        controller.advance(0.08f, 0f, -3f, 0f, 0f, 0.45f, 1f);

        assertTrue(controller.getPack() > 0.45f);
        assertTrue(controller.getCoverage() > 0.90f);
        assertEquals(0f, controller.getMeltWaterIntensity(), 0.000001f);
    }

    @Test
    public void coldCurrentSnowCanAccumulateBetweenProviderRefreshes() {
        SnowSurfaceController controller = SnowSurfaceController.isolatedForTest();

        for (int i = 0; i < 600; i++) {
            controller.advance(-1f, 0.80f, -4f, 0f, 0f, 0.35f, 1f);
        }

        assertTrue(controller.getPack() > 0.03f);
        assertTrue(controller.getCoverage() > 0.55f);
        assertTrue(controller.getFreshness() > 0.70f);
    }

    @Test
    public void aboveFreezingSurfaceMeltsRetainedPackAndProducesMeltSignal() {
        SnowSurfaceController controller = SnowSurfaceController.isolatedForTest();
        controller.seedForTest(0.80f, 0.90f, 0.70f);

        for (int i = 0; i < 600; i++) {
            controller.advance(-1f, 0f, 8f, 0f, 0f, 0.90f, 1f);
        }

        assertTrue(controller.getPack() < 0.78f);
        assertTrue(controller.getMeltWaterIntensity() > 0f);
    }

    @Test
    public void subFreezingDryWeatherDoesNotInventMelt() {
        SnowSurfaceController controller = SnowSurfaceController.isolatedForTest();
        controller.seedForTest(0.62f, 0.82f, 0.45f);

        for (int i = 0; i < 900; i++) {
            controller.advance(-1f, 0f, -7f, 0f, 0f, 0.80f, 1f);
        }

        assertEquals(0.62f, controller.getPack(), 0.0001f);
        assertEquals(0f, controller.getMeltWaterIntensity(), 0.0001f);
    }

    @Test
    public void warmRainAcceleratesThawWithoutBecomingSnowfallTruth() {
        SnowSurfaceController dryWarm = SnowSurfaceController.isolatedForTest();
        SnowSurfaceController rainyWarm = SnowSurfaceController.isolatedForTest();
        dryWarm.seedForTest(0.75f, 0.88f, 0.55f);
        rainyWarm.seedForTest(0.75f, 0.88f, 0.55f);

        for (int i = 0; i < 900; i++) {
            dryWarm.advance(-1f, 0f, 7f, 0f, 0f, 0.55f, 1f);
            rainyWarm.advance(-1f, 0f, 7f, 1f, 0f, 0.55f, 1f);
        }

        assertTrue(rainyWarm.getPack() < dryWarm.getPack());
        assertTrue(rainyWarm.getMeltWaterIntensity() >= dryWarm.getMeltWaterIntensity());
    }

    @Test
    public void outputsRemainStrictlyBoundedForExtremeInputs() {
        SnowSurfaceController controller = SnowSurfaceController.isolatedForTest();

        for (int i = 0; i < 500; i++) {
            controller.advance(10f, 8f, 80f, 9f, 9f, 5f, 2f);
        }

        assertBounded(controller.getPack());
        assertBounded(controller.getCoverage());
        assertBounded(controller.getFreshness());
        assertBounded(controller.getMeltWaterIntensity());
    }

    private static void assertBounded(float value) {
        assertTrue(value >= 0f);
        assertTrue(value <= 1f);
    }
}
