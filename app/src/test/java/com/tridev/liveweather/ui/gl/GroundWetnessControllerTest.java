package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GroundWetnessControllerTest {

    @Test
    public void heavyRainBuildsWetnessAndPuddles() {
        GroundWetnessController controller = new GroundWetnessController();

        for (int second = 0; second < 90; second++) {
            controller.advance(1f, 0f, 0.65f, 0f, 0.35f, 1f);
        }

        assertTrue(controller.getWetness() > 0.85f);
        assertTrue(controller.getPuddleCoverage() > 0.55f);
    }

    @Test
    public void recentRainDoesNotDisappearWhenRainStops() {
        GroundWetnessController controller = new GroundWetnessController();

        for (int second = 0; second < 60; second++) {
            controller.advance(0.85f, 0f, 0.25f, 0f, 0.20f, 1f);
        }

        float wetBeforeDrying = controller.getWetness();
        float puddlesBeforeDrying = controller.getPuddleCoverage();

        for (int second = 0; second < 10; second++) {
            controller.advance(0f, 0f, 0f, 0f, 0.20f, 1f);
        }

        assertTrue(controller.getWetness() > wetBeforeDrying * 0.98f);
        assertTrue(controller.getPuddleCoverage() > puddlesBeforeDrying * 0.96f);
    }

    @Test
    public void warmWindDriesFasterThanCalmCoolAir() {
        GroundWetnessController coolCalm = new GroundWetnessController();
        GroundWetnessController warmWindy = new GroundWetnessController();
        coolCalm.reset(0.90f, 0.65f);
        warmWindy.reset(0.90f, 0.65f);

        for (int second = 0; second < 600; second++) {
            coolCalm.advance(0f, 0f, 0f, -0.35f, 0.05f, 1f);
            warmWindy.advance(0f, 0f, 0f, 0.90f, 0.95f, 1f);
        }

        assertTrue(warmWindy.getWetness() < coolCalm.getWetness());
        assertTrue(warmWindy.getPuddleCoverage() < coolCalm.getPuddleCoverage());
    }

    @Test
    public void valuesAlwaysStayNormalized() {
        GroundWetnessController controller = new GroundWetnessController();
        controller.reset(4f, 7f);

        for (int i = 0; i < 20; i++) {
            controller.advance(5f, 3f, 2f, 4f, 5f, 9f);
        }

        assertTrue(controller.getWetness() >= 0f && controller.getWetness() <= 1f);
        assertTrue(controller.getPuddleCoverage() >= 0f && controller.getPuddleCoverage() <= 1f);
        assertTrue(controller.getPuddleCoverage() <= controller.getWetness() + 0.0001f);
    }
}
