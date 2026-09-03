package com.tridev.liveweather;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.tridev.liveweather.core.location.LocationContinuityPolicy;

import org.junit.Test;

public final class LocationContinuityPolicyTest {

    @Test
    public void smallGpsDriftDoesNotReloadWeather() {
        long now = 2_000_000L;
        assertFalse(LocationContinuityPolicy.shouldActivate(
                25.2677, 82.9913, now - 60_000L,
                25.2690, 82.9920, 20f, now));
    }

    @Test
    public void meaningfulMovementActivatesNewLocation() {
        long now = 2_000_000L;
        assertTrue(LocationContinuityPolicy.shouldActivate(
                25.2677, 82.9913, now - 60_000L,
                25.2770, 82.9913, 20f, now));
    }

    @Test
    public void staleSnapshotRefreshesEvenWithoutMovement() {
        long now = 2_000_000L;
        assertTrue(LocationContinuityPolicy.shouldActivate(
                25.2677, 82.9913,
                now - LocationContinuityPolicy.SNAPSHOT_STALE_MILLIS,
                25.2677, 82.9913, 25f, now));
    }

    @Test
    public void unusableAccuracyIsRejected() {
        assertFalse(LocationContinuityPolicy.isUsable(25.2, 83.0, 7_000f));
        assertFalse(LocationContinuityPolicy.isUsable(95.0, 83.0, 20f));
        assertTrue(LocationContinuityPolicy.isUsable(25.2, 83.0, Float.NaN));
    }

    @Test
    public void foregroundChecksArePowerBounded() {
        long now = 2_000_000L;
        assertFalse(LocationContinuityPolicy.shouldRecheck(
                now - LocationContinuityPolicy.FOREGROUND_RECHECK_MILLIS + 1L, now));
        assertTrue(LocationContinuityPolicy.shouldRecheck(
                now - LocationContinuityPolicy.FOREGROUND_RECHECK_MILLIS, now));
    }
}
