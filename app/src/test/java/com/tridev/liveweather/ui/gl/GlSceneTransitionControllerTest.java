package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GlSceneTransitionControllerTest {

    @Test
    public void centralizedWindNeverMutatesResolvedWeatherTruth() {
        GlSceneSnapshot truth = snapshot(0.74f, 1.85f, 0.58f);
        float truthStrength = truth.windStrength;
        float truthDirection = truth.windDirectionRadians;

        GlSceneTransitionController controller = new GlSceneTransitionController();
        controller.setTarget(truth);

        GlSceneSnapshot visual = controller.current();
        assertNotNull(visual);
        assertNotSame(truth, visual);
        assertEquals(truthStrength, truth.windStrength, 0.000001f);
        assertEquals(truthDirection, truth.windDirectionRadians, 0.000001f);
        assertTrue(visual.windStrength >= 0f && visual.windStrength <= 1f);
        assertTrue(visual.windDirectionRadians >= 0f);
        assertTrue(visual.windDirectionRadians < Math.PI * 2f);
    }

    @Test
    public void calmTruthRemainsCalmAfterCentralSampling() {
        GlSceneTransitionController controller = new GlSceneTransitionController();
        controller.setTarget(snapshot(0f, 5.4f, 1f));

        GlSceneSnapshot visual = controller.current();
        assertNotNull(visual);
        assertEquals(0f, visual.windStrength, 0.000001f);
    }

    @Test
    public void settledSceneStillPublishesPerFrameWindUpdates() {
        GlSceneTransitionController controller = new GlSceneTransitionController();
        controller.setTarget(snapshot(0.68f, 2.1f, 0.42f));

        assertTrue(controller.advance());
        assertTrue(controller.advance());

        GlSceneSnapshot visual = controller.current();
        assertNotNull(visual);
        assertTrue(visual.windStrength >= 0f && visual.windStrength <= 1f);
    }

    @Test
    public void newWindTargetRemainsAuthoritativeWhileVisualWindIsBounded() {
        GlSceneTransitionController controller = new GlSceneTransitionController();
        controller.setTarget(snapshot(0.82f, 0.7f, 0.90f));

        GlSceneSnapshot newTruth = snapshot(0.24f, 4.9f, 0.08f);
        controller.setTarget(newTruth);
        for (int i = 0; i < 12; i++) {
            controller.advance();
        }

        assertEquals(0.24f, newTruth.windStrength, 0.000001f);
        assertEquals(4.9f, newTruth.windDirectionRadians, 0.000001f);
        GlSceneSnapshot visual = controller.current();
        assertNotNull(visual);
        assertTrue(visual.windStrength >= 0f && visual.windStrength <= 1f);
        assertTrue(visual.windDirectionRadians >= 0f);
        assertTrue(visual.windDirectionRadians < Math.PI * 2f);
    }

    private static GlSceneSnapshot snapshot(float wind, float direction, float storm) {
        return new GlSceneSnapshot(
                0.10f, 0.16f, 0.24f,
                0.18f, 0.24f, 0.32f,
                0.28f, 0.34f, 0.40f,
                0.55f, 0.30f, 0.80f, 22f,
                0.25f, 0.32f, 0.35f, 0.70f,
                1.2f, 18f,
                0.25f,
                0.40f, 2.4f,
                0.55f, 0.58f,
                0.40f, 0.55f, 0.30f, 0.12f, 0.72f,
                0.38f, 0.10f, 0.0f, 0.18f, storm, 0.12f,
                wind, direction,
                0.72f,
                0.15f,
                0.78f,
                0.50f
        );
    }
}
