package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AtmosphericPerspectivePolicyTest {

    @Test
    public void clearVisibilityIsExactlyNeutral() {
        AtmosphericPerspectivePolicy.Sample sample = new AtmosphericPerspectivePolicy.Sample();
        AtmosphericPerspectivePolicy.resolveInto(sample, 1f);

        assertEquals(1f, sample.farTransmission, 0f);
        assertEquals(1f, sample.midTransmission, 0f);
        assertEquals(1f, sample.nearTransmission, 0f);
        assertEquals(1f, sample.microVisibility, 0f);
    }

    @Test
    public void reducedVisibilityAttenuatesDistanceInPhysicalOrder() {
        AtmosphericPerspectivePolicy.Sample sample = new AtmosphericPerspectivePolicy.Sample();
        AtmosphericPerspectivePolicy.resolveInto(sample, 0.25f);

        assertTrue(sample.farTransmission < sample.midTransmission);
        assertTrue(sample.midTransmission < sample.nearTransmission);
        assertTrue(sample.microVisibility < sample.nearTransmission);
    }

    @Test
    public void denseVisibilityLossStillKeepsNearbyWorldReadable() {
        AtmosphericPerspectivePolicy.Sample sample = new AtmosphericPerspectivePolicy.Sample();
        AtmosphericPerspectivePolicy.resolveInto(sample, 0.05f);

        assertTrue(sample.farTransmission <= 0.10f);
        assertTrue(sample.midTransmission >= 0.25f);
        assertTrue(sample.nearTransmission >= 0.62f);
        assertTrue(sample.microVisibility >= 0.34f);
    }

    @Test
    public void worseningVisibilityMonotonicallyReducesEveryChannel() {
        AtmosphericPerspectivePolicy.Sample clearer = new AtmosphericPerspectivePolicy.Sample();
        AtmosphericPerspectivePolicy.Sample murkier = new AtmosphericPerspectivePolicy.Sample();
        AtmosphericPerspectivePolicy.resolveInto(clearer, 0.75f);
        AtmosphericPerspectivePolicy.resolveInto(murkier, 0.35f);

        assertTrue(murkier.farTransmission < clearer.farTransmission);
        assertTrue(murkier.midTransmission < clearer.midTransmission);
        assertTrue(murkier.nearTransmission < clearer.nearTransmission);
        assertTrue(murkier.microVisibility < clearer.microVisibility);
    }

    @Test
    public void invalidVisibilityFailsNeutralInsteadOfBlackeningScene() {
        AtmosphericPerspectivePolicy.Sample nan = new AtmosphericPerspectivePolicy.Sample();
        AtmosphericPerspectivePolicy.Sample infinite = new AtmosphericPerspectivePolicy.Sample();
        AtmosphericPerspectivePolicy.resolveInto(nan, Float.NaN);
        AtmosphericPerspectivePolicy.resolveInto(infinite, Float.POSITIVE_INFINITY);

        assertEquals(1f, nan.farTransmission, 0f);
        assertEquals(1f, nan.nearTransmission, 0f);
        assertEquals(1f, infinite.farTransmission, 0f);
        assertEquals(1f, infinite.microVisibility, 0f);
    }

    @Test
    public void outOfRangeInputsRemainBounded() {
        AtmosphericPerspectivePolicy.Sample below = new AtmosphericPerspectivePolicy.Sample();
        AtmosphericPerspectivePolicy.Sample above = new AtmosphericPerspectivePolicy.Sample();
        AtmosphericPerspectivePolicy.resolveInto(below, -5f);
        AtmosphericPerspectivePolicy.resolveInto(above, 5f);

        assertTrue(below.farTransmission >= 0.08f && below.farTransmission <= 1f);
        assertTrue(below.nearTransmission >= 0.62f && below.nearTransmission <= 1f);
        assertEquals(1f, above.farTransmission, 0f);
        assertEquals(1f, above.midTransmission, 0f);
        assertEquals(1f, above.nearTransmission, 0f);
        assertEquals(1f, above.microVisibility, 0f);
    }
}
