package com.tridev.liveweather;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.tridev.liveweather.core.ActiveWeatherRequestPolicy;

import org.junit.Test;

public final class ActiveWeatherRequestPolicyTest {

    @Test
    public void exactCurrentTokenIsAccepted() {
        assertTrue(ActiveWeatherRequestPolicy.isCurrent(
                8L, "25.317_83.010", 8L, "25.317_83.010"
        ));
    }

    @Test
    public void olderSameLocationGenerationIsRejected() {
        assertFalse(ActiveWeatherRequestPolicy.isCurrent(
                7L, "25.317_83.010", 8L, "25.317_83.010"
        ));
    }

    @Test
    public void oldLocationIsRejectedEvenAtSameGeneration() {
        assertFalse(ActiveWeatherRequestPolicy.isCurrent(
                8L, "26.847_80.946", 8L, "25.317_83.010"
        ));
    }

    @Test
    public void missingGenerationCannotBecomeAuthoritative() {
        assertFalse(ActiveWeatherRequestPolicy.isCurrent(
                0L, "25.317_83.010", 0L, "25.317_83.010"
        ));
    }
}
