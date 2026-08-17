package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class UnifiedWindControllerTest {

    @Test
    public void calmWindRemainsCalm() {
        UnifiedWindController controller = new UnifiedWindController();
        controller.sample(0f, -1.2f, 1f, 42f);

        assertEquals(0f, controller.getBaseStrength(), 0.0001f);
        assertEquals(0f, controller.getEffectiveStrength(), 0.0001f);
        assertEquals(1f, controller.getGustFactor(), 0.0001f);
        assertEquals(0f, controller.getTurbulence(), 0.0001f);
    }

    @Test
    public void sameInputsAndTimeProduceSameSample() {
        UnifiedWindController first = new UnifiedWindController();
        UnifiedWindController second = new UnifiedWindController();

        first.sample(0.68f, 5.7f, 0.42f, 137.25f);
        second.sample(0.68f, 5.7f, 0.42f, 137.25f);

        assertEquals(first.getEffectiveStrength(), second.getEffectiveStrength(), 0.000001f);
        assertEquals(first.getDirectionRadians(), second.getDirectionRadians(), 0.000001f);
        assertEquals(first.getSide(), second.getSide(), 0.000001f);
        assertEquals(first.getForward(), second.getForward(), 0.000001f);
        assertEquals(first.getGustFactor(), second.getGustFactor(), 0.000001f);
        assertEquals(first.getTurbulence(), second.getTurbulence(), 0.000001f);
    }

    @Test
    public void sharedRendererClockIsNonNegativeAndMonotonic() {
        float first = UnifiedWindController.sharedMonotonicSeconds();
        float second = UnifiedWindController.sharedMonotonicSeconds();

        assertTrue(first >= 0f);
        assertTrue(second >= first);
    }

    @Test
    public void rainAndSnowEquivalentConsumersReceiveSameMacroWind() {
        UnifiedWindController rain = new UnifiedWindController();
        UnifiedWindController snow = new UnifiedWindController();
        float sharedTime = 246.75f;

        rain.sample(0.74f, 1.85f, 0.58f, sharedTime);
        snow.sample(0.74f, 1.85f, 0.58f, sharedTime);

        assertEquals(rain.getEffectiveStrength(), snow.getEffectiveStrength(), 0.000001f);
        assertEquals(rain.getDirectionRadians(), snow.getDirectionRadians(), 0.000001f);
        assertEquals(rain.getTurbulence(), snow.getTurbulence(), 0.000001f);
    }

    @Test
    public void strongStormWindHasVisibleButBoundedGustVariation() {
        UnifiedWindController controller = new UnifiedWindController();
        float min = 1f;
        float max = 0f;

        for (int second = 0; second <= 180; second += 3) {
            controller.sample(0.82f, 1.1f, 0.90f, second);
            min = Math.min(min, controller.getEffectiveStrength());
            max = Math.max(max, controller.getEffectiveStrength());
            assertTrue(controller.getEffectiveStrength() >= 0f);
            assertTrue(controller.getEffectiveStrength() <= 1f);
            assertTrue(controller.getGustFactor() > 0f);
        }

        assertTrue(max - min > 0.18f);
    }

    @Test
    public void directionStaysCloseToResolvedHeading() {
        UnifiedWindController controller = new UnifiedWindController();
        float resolved = (float) Math.toRadians(278d);

        for (int second = 0; second <= 120; second += 5) {
            controller.sample(0.95f, resolved, 1f, second);
            float delta = shortestAngle(controller.getDirectionRadians() - resolved);
            assertTrue(Math.abs(delta) < Math.toRadians(6d));
        }
    }

    @Test
    public void sideAndForwardFormUnitDirectionVector() {
        UnifiedWindController controller = new UnifiedWindController();
        controller.sample(0.55f, (float) Math.toRadians(315d), 0.2f, 23f);

        float magnitude = (float) Math.sqrt(
                controller.getSide() * controller.getSide()
                        + controller.getForward() * controller.getForward()
        );
        assertEquals(1f, magnitude, 0.0001f);
        assertTrue(controller.getDirectionRadians() >= 0f);
        assertTrue(controller.getDirectionRadians() < Math.PI * 2f);
    }

    @Test
    public void strongerWindProducesMoreTurbulenceThanLightWindAtSameMoment() {
        UnifiedWindController light = new UnifiedWindController();
        UnifiedWindController strong = new UnifiedWindController();

        light.sample(0.18f, 2.2f, 0.10f, 91f);
        strong.sample(0.88f, 2.2f, 0.10f, 91f);

        assertTrue(strong.getTurbulence() > light.getTurbulence());
    }

    private static float shortestAngle(float value) {
        float twoPi = (float) (Math.PI * 2d);
        value %= twoPi;
        if (value > Math.PI) value -= twoPi;
        if (value < -Math.PI) value += twoPi;
        return value;
    }
}
