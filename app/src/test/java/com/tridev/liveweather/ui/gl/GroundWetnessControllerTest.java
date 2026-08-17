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
        assertTrue(controller.getSoilSaturation() > 0.85f);
        assertTrue(controller.getSurfaceWater() > 0.45f);
    }

    @Test
    public void longerRainAccumulatesMoreStandingWaterThanShortShower() {
        GroundWetnessController shortShower = new GroundWetnessController();
        GroundWetnessController longDownpour = new GroundWetnessController();

        for (int second = 0; second < 20; second++) {
            shortShower.advance(0.90f, 0f, 0.35f, 0f, 0.20f, 1f);
        }
        for (int second = 0; second < 180; second++) {
            longDownpour.advance(0.90f, 0f, 0.35f, 0f, 0.20f, 1f);
        }

        assertTrue(longDownpour.getSurfaceWater() > shortShower.getSurfaceWater() + 0.25f);
        assertTrue(longDownpour.getPuddleCoverage() > shortShower.getPuddleCoverage() + 0.25f);
    }

    @Test
    public void puddleDepthBuildsBeforeFootprintSpread() {
        GroundWetnessController controller = new GroundWetnessController();

        for (int second = 0; second < 60; second++) {
            controller.advance(1f, 0f, 0.65f, 0f, 0.25f, 1f);
        }

        assertTrue(controller.getPuddleDepth() > controller.getPuddleSpread());
        assertTrue(controller.getPuddleDepth() > 0.30f);
        assertTrue(controller.getPuddleSpread() > 0.05f);
    }

    @Test
    public void prolongedRainBroadensPuddleFootprint() {
        GroundWetnessController oneMinute = new GroundWetnessController();
        GroundWetnessController fiveMinutes = new GroundWetnessController();

        for (int second = 0; second < 60; second++) {
            oneMinute.advance(1f, 0f, 0.55f, 0f, 0.20f, 1f);
        }
        for (int second = 0; second < 300; second++) {
            fiveMinutes.advance(1f, 0f, 0.55f, 0f, 0.20f, 1f);
        }

        assertTrue(fiveMinutes.getPuddleSpread() > oneMinute.getPuddleSpread() + 0.45f);
        assertTrue(fiveMinutes.getPuddleCoverage() > oneMinute.getPuddleCoverage());
    }

    @Test
    public void drizzleWetsGroundButProducesLessStandingWaterThanRain() {
        GroundWetnessController drizzle = new GroundWetnessController();
        GroundWetnessController rain = new GroundWetnessController();

        for (int second = 0; second < 120; second++) {
            drizzle.advance(0f, 0.90f, 0f, 0f, 0.10f, 1f);
            rain.advance(0.90f, 0f, 0f, 0f, 0.10f, 1f);
        }

        assertTrue(drizzle.getWetness() > 0.45f);
        assertTrue(rain.getSurfaceWater() > drizzle.getSurfaceWater());
        assertTrue(rain.getPuddleCoverage() > drizzle.getPuddleCoverage());
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
        assertTrue(controller.getPuddleCoverage() > puddlesBeforeDrying * 0.95f);
    }

    @Test
    public void puddlesBecomeShallowerBeforeDampFootprintDisappears() {
        GroundWetnessController controller = new GroundWetnessController();

        for (int second = 0; second < 180; second++) {
            controller.advance(0.95f, 0f, 0.45f, 0f, 0.20f, 1f);
        }

        for (int second = 0; second < 900; second++) {
            controller.advance(0f, 0f, 0f, 0.45f, 0.55f, 1f);
        }

        assertTrue(controller.getPuddleSpread() > controller.getPuddleDepth());
        assertTrue(controller.getPuddleSpread() > 0f);
    }

    @Test
    public void puddlesDrainBeforeDeepGroundMoisture() {
        GroundWetnessController controller = new GroundWetnessController();
        controller.reset(0.95f, 0.75f);

        for (int second = 0; second < 900; second++) {
            controller.advance(0f, 0f, 0f, 0.45f, 0.55f, 1f);
        }

        assertTrue(controller.getSoilSaturation() > controller.getSurfaceWater());
        assertTrue(controller.getWetness() > controller.getPuddleCoverage());
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
        assertTrue(warmWindy.getSurfaceWater() < coolCalm.getSurfaceWater());
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
        assertTrue(controller.getSoilSaturation() >= 0f && controller.getSoilSaturation() <= 1f);
        assertTrue(controller.getSurfaceWater() >= 0f && controller.getSurfaceWater() <= 1f);
        assertTrue(controller.getPuddleDepth() >= 0f && controller.getPuddleDepth() <= 1f);
        assertTrue(controller.getPuddleSpread() >= 0f && controller.getPuddleSpread() <= 1f);
        assertTrue(controller.getPuddleCoverage() <= controller.getWetness() + 0.0001f);
    }
}
