package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorldLightingControllerTest {

    @Test
    public void clearDaylightStaysBrightAndStable() {
        double lightA = resolve(
                0.80d, 42d, 1d,
                0.02d, 0.04d, 0.02d, 0.01d, 0d, 0.95d,
                0d, 0d, 0d, 0.25d, 0.4d,
                10_000L
        );
        double lightB = resolve(
                0.80d, 42d, 1d,
                0.02d, 0.04d, 0.02d, 0.01d, 0d, 0.95d,
                0d, 0d, 0d, 0.25d, 0.4d,
                180_000L
        );

        assertTrue(lightA >= 0.79d);
        assertTrue(lightA <= 0.83d);
        assertEquals(lightA, lightB, 0.000001d);
    }

    @Test
    public void fullOvercastDimsDiffuseWorldWithoutPulsing() {
        double lightA = resolve(
                0.82d, 38d, 0.22d,
                0.96d, 0.91d, 0.88d, 0.76d, 0.45d, 0.48d,
                0.18d, 0.05d, 0.08d, 0.55d, 1.8d,
                20_000L
        );
        double lightB = resolve(
                0.82d, 38d, 0.22d,
                0.96d, 0.91d, 0.88d, 0.76d, 0.45d, 0.48d,
                0.18d, 0.05d, 0.08d, 0.55d, 1.8d,
                220_000L
        );

        assertTrue(lightA < 0.72d);
        assertTrue(lightA > 0.45d);
        assertEquals(lightA, lightB, 0.000001d);
    }

    @Test
    public void brokenCloudsCreateSlowBoundedPassingShade() {
        double lightA = resolve(
                0.82d, 46d, 0.92d,
                0.52d, 0.62d, 0.60d, 0.48d, 0.08d, 0.78d,
                0d, 0.02d, 0.03d, 0.65d, 1.1d,
                0L
        );
        double lightB = resolve(
                0.82d, 46d, 0.92d,
                0.52d, 0.62d, 0.60d, 0.48d, 0.08d, 0.78d,
                0d, 0.02d, 0.03d, 0.65d, 1.1d,
                60_000L
        );

        assertTrue(Math.abs(lightA - lightB) > 0.002d);
        assertTrue(lightA >= 0.58d && lightA <= 0.86d);
        assertTrue(lightB >= 0.58d && lightB <= 0.86d);
    }

    @Test
    public void nightLeavesExistingSceneLightUntouched() {
        double base = 0.17d;
        double resolved = resolve(
                base, -12d, 0d,
                0.58d, 0.70d, 0.65d, 0.54d, 0.15d, 0.55d,
                0.20d, 0.10d, 0.12d, 0.75d, 2.4d,
                90_000L
        );

        assertEquals(base, resolved, 0.000001d);
    }

    @Test
    public void brokenCloudSignalRejectsClearAndSolidOvercast() {
        double clear = WorldLightingController.brokenCloudSignal(0.02d);
        double broken = WorldLightingController.brokenCloudSignal(0.50d);
        double overcast = WorldLightingController.brokenCloudSignal(0.98d);

        assertTrue(broken > clear + 0.70d);
        assertTrue(broken > overcast + 0.70d);
    }

    private static double resolve(
            double baseSceneLight,
            double sunAltitudeDegrees,
            double sunVisibility,
            double cloudCover,
            double cloudDensity,
            double cloudMidLayer,
            double cloudNearLayer,
            double cloudStormCeiling,
            double cloudBrightness,
            double stormIntensity,
            double fogIntensity,
            double airHazeIntensity,
            double windStrength,
            double windDirectionRadians,
            long epochMillis
    ) {
        return WorldLightingController.resolveSceneLight(
                baseSceneLight,
                sunAltitudeDegrees,
                sunVisibility,
                cloudCover,
                cloudDensity,
                cloudMidLayer,
                cloudNearLayer,
                cloudStormCeiling,
                cloudBrightness,
                stormIntensity,
                fogIntensity,
                airHazeIntensity,
                windStrength,
                windDirectionRadians,
                epochMillis
        );
    }
}
