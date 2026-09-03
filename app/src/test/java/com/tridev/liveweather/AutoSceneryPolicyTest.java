package com.tridev.liveweather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.tridev.liveweather.domain.scene.AutoSceneryPolicy;
import com.tridev.liveweather.domain.scene.SceneryMode;

import org.junit.Test;

public class AutoSceneryPolicyTest {

    @Test
    public void autoPolicyNeverReturnsAutoMode() {
        for (int hour = 0; hour < 24; hour++) {
            for (int variant = 0; variant < 4; variant++) {
                assertNotEquals(
                        SceneryMode.AUTO,
                        AutoSceneryPolicy.resolve(hour, 229, variant)
                );
            }
        }
    }

    @Test
    public void autoPolicyIsDeterministicForSameInputs() {
        SceneryMode first = AutoSceneryPolicy.resolve(11, 229, 2);
        SceneryMode second = AutoSceneryPolicy.resolve(11, 229, 2);
        assertEquals(first, second);
    }

    @Test
    public void dayPartChangesAlwaysResolveConcreteScenes() {
        SceneryMode dawn = AutoSceneryPolicy.resolve(7, 229, 0);
        SceneryMode day = AutoSceneryPolicy.resolve(12, 229, 0);
        SceneryMode evening = AutoSceneryPolicy.resolve(18, 229, 0);
        SceneryMode night = AutoSceneryPolicy.resolve(22, 229, 0);

        assertNotEquals(SceneryMode.AUTO, dawn);
        assertNotEquals(SceneryMode.AUTO, day);
        assertNotEquals(SceneryMode.AUTO, evening);
        assertNotEquals(SceneryMode.AUTO, night);
    }

    @Test
    public void currentStormTruthUsesStormFriendlyScenePool() {
        SceneryMode result = AutoSceneryPolicy.resolveForCurrentTruth(
                12, 229, 1,
                0.92f, 0.35f, 0.0f, 0.0f, 0.72f, 0.0f, 0.0f
        );

        assertTrue(
                result == SceneryMode.URBAN_BUILDINGS
                        || result == SceneryMode.NATURAL_HILLS
                        || result == SceneryMode.OPEN_SKY
        );
    }

    @Test
    public void currentSnowTruthUsesSnowFriendlyScenePool() {
        SceneryMode result = AutoSceneryPolicy.resolveForCurrentTruth(
                12, 229, 2,
                0.80f, 0.0f, 0.0f, 0.65f, 0.0f, 0.12f, 0.0f
        );

        assertTrue(
                result == SceneryMode.NATURAL_HILLS
                        || result == SceneryMode.VILLAGE
                        || result == SceneryMode.OPEN_SKY
        );
    }

    @Test
    public void currentFogTruthUsesFogFriendlyScenePool() {
        SceneryMode result = AutoSceneryPolicy.resolveForCurrentTruth(
                7, 229, 0,
                0.55f, 0.0f, 0.0f, 0.0f, 0.0f, 0.70f, 0.0f
        );

        assertTrue(
                result == SceneryMode.NATURAL_HILLS
                        || result == SceneryMode.RIVER_LAKE
                        || result == SceneryMode.VILLAGE
        );
    }

    @Test
    public void dryHazeDoesNotMasqueradeAsFog() {
        SceneryMode result = AutoSceneryPolicy.resolveForCurrentTruth(
                12, 229, 1,
                0.22f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.82f
        );

        assertTrue(
                result == SceneryMode.OPEN_SKY
                        || result == SceneryMode.URBAN_BUILDINGS
                        || result == SceneryMode.NATURAL_HILLS
        );
        assertNotEquals(SceneryMode.RIVER_LAKE, result);
        assertNotEquals(SceneryMode.VILLAGE, result);
    }

    @Test
    public void clearCurrentTruthFallsBackToDayPartPolicy() {
        SceneryMode expected = AutoSceneryPolicy.resolve(12, 229, 3);
        SceneryMode actual = AutoSceneryPolicy.resolveForCurrentTruth(
                12, 229, 3,
                0.18f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f
        );
        assertEquals(expected, actual);
    }
}
