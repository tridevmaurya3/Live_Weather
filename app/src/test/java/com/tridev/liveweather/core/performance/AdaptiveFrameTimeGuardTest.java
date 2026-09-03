package com.tridev.liveweather.core.performance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AdaptiveFrameTimeGuardTest {

    @Test
    public void sustainedPressureReducesOnlySecondaryDetail() {
        AdaptiveFrameTimeGuard guard = new AdaptiveFrameTimeGuard();
        guard.setBaseProfile(33L, 0.82f);

        for (int i = 0; i < 120; i++) {
            guard.observeFrameNanos(50_000_000L);
        }

        assertTrue(guard.getEffectiveDetailScale() < 0.82f);
        assertTrue(guard.getEffectiveDetailScale() >= 0.50f);
    }

    @Test
    public void stableFramesRestoreSelectedQualityWithoutOvershoot() {
        AdaptiveFrameTimeGuard guard = new AdaptiveFrameTimeGuard();
        guard.setBaseProfile(33L, 0.82f);

        for (int i = 0; i < 120; i++) {
            guard.observeFrameNanos(50_000_000L);
        }
        float reduced = guard.getEffectiveDetailScale();
        assertTrue(reduced < 0.82f);

        for (int i = 0; i < 500; i++) {
            guard.observeFrameNanos(8_000_000L);
        }

        assertEquals(0.82f, guard.getEffectiveDetailScale(), 0.0001f);
    }

    @Test
    public void ecoProfileNeverFallsBelowReadableFloor() {
        AdaptiveFrameTimeGuard guard = new AdaptiveFrameTimeGuard();
        guard.setBaseProfile(66L, 0.58f);

        for (int i = 0; i < 600; i++) {
            guard.observeFrameNanos(150_000_000L);
        }

        assertTrue(guard.getEffectiveDetailScale() >= 0.50f);
        assertTrue(guard.getEffectiveDetailScale() <= 0.58f);
    }

    @Test
    public void profileChangeResetsAdaptivePenalty() {
        AdaptiveFrameTimeGuard guard = new AdaptiveFrameTimeGuard();
        guard.setBaseProfile(33L, 0.82f);

        for (int i = 0; i < 120; i++) {
            guard.observeFrameNanos(50_000_000L);
        }
        assertTrue(guard.getEffectiveDetailScale() < 0.82f);

        float reset = guard.setBaseProfile(50L, 0.58f);
        assertEquals(0.58f, reset, 0.0001f);
        assertEquals(0.58f, guard.getEffectiveDetailScale(), 0.0001f);
    }

    @Test
    public void invalidTimingSamplesDoNotChangeQuality() {
        AdaptiveFrameTimeGuard guard = new AdaptiveFrameTimeGuard();
        guard.setBaseProfile(33L, 0.82f);

        for (int i = 0; i < 200; i++) {
            guard.observeFrameNanos(0L);
            guard.observeFrameNanos(-1L);
        }

        assertEquals(0.82f, guard.getEffectiveDetailScale(), 0.0001f);
    }
}
