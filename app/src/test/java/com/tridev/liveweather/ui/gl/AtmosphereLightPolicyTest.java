package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AtmosphereLightPolicyTest {

    @Test public void twilightWarmsHorizonWithoutDayOrNightJump() {
        double night = AtmosphereLightPolicy.twilightWarmth(-15d, 0d);
        double twilight = AtmosphereLightPolicy.twilightWarmth(-1d, 0d);
        double day = AtmosphereLightPolicy.twilightWarmth(15d, 0d);
        assertTrue(twilight > night);
        assertTrue(twilight > day);
    }

    @Test public void overcastFogAndStormReduceButNeverBlackOutExposure() {
        double clear = AtmosphereLightPolicy.daylightExposure(30d, 0d, 0d, 0d, 0d);
        double severe = AtmosphereLightPolicy.daylightExposure(30d, 1d, 1d, 1d, 1d);
        assertTrue(clear > severe);
        assertTrue(severe >= 0.52d);
    }

    @Test public void starsAppearOnlyAfterAstronomicalDarkening() {
        assertEquals(0d, AtmosphereLightPolicy.starGate(-6d), 0.0001d);
        assertTrue(AtmosphereLightPolicy.starGate(-12d) > 0d);
        assertEquals(1d, AtmosphereLightPolicy.starGate(-18d), 0.0001d);
    }

    @Test public void hazeAndFogStayConcentratedAtHorizon() {
        assertEquals(0d, AtmosphereLightPolicy.horizonDepth(0d, 0d, 1d), 0.0001d);
        assertTrue(AtmosphereLightPolicy.horizonDepth(0.7d, 0.6d, 0.3d) > 0.7d);
    }

    @Test public void lightningLiftIsTruthGatedAndBounded() {
        assertEquals(0d, AtmosphereLightPolicy.lightningEnvironmentLift(0d, 1d, 1d, 1d), 0d);
        double lift = AtmosphereLightPolicy.lightningEnvironmentLift(1d, 1d, 1d, 1d);
        assertTrue(lift > 0d);
        assertTrue(lift <= 0.24d);
    }
}
