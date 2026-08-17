package com.tridev.liveweather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

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
    public void dayPartChangesCanResolveDifferentSceneFamilies() {
        SceneryMode dawn = AutoSceneryPolicy.resolve(7, 229, 0);
        SceneryMode day = AutoSceneryPolicy.resolve(12, 229, 0);
        SceneryMode evening = AutoSceneryPolicy.resolve(18, 229, 0);
        SceneryMode night = AutoSceneryPolicy.resolve(22, 229, 0);

        assertNotEquals(SceneryMode.AUTO, dawn);
        assertNotEquals(SceneryMode.AUTO, day);
        assertNotEquals(SceneryMode.AUTO, evening);
        assertNotEquals(SceneryMode.AUTO, night);
    }
}
