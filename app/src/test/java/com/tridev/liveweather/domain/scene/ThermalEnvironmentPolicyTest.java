package com.tridev.liveweather.domain.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ThermalEnvironmentPolicyTest {

    @Test
    public void missingTemperatureIsNeutral() {
        assertEquals(0d, ThermalEnvironmentPolicy.resolve(null, null, 50d, 10d), 0.0001d);
    }

    @Test
    public void comfortableTemperatureStaysNearNeutral() {
        double signal = ThermalEnvironmentPolicy.resolve(22d, 22d, 55d, 12d);
        assertEquals(0d, signal, 0.04d);
    }

    @Test
    public void humidHeatFeelsWarmerThanSameDryTemperature() {
        double humid = ThermalEnvironmentPolicy.resolve(31d, 35d, 84d, 25d);
        double dry = ThermalEnvironmentPolicy.resolve(31d, 32d, 28d, 10d);

        assertTrue(humid > dry);
        assertTrue(humid > 0.35d);
    }

    @Test
    public void apparentColdStrengthensColdMaterialSignal() {
        double calmCold = ThermalEnvironmentPolicy.resolve(6d, 6d, 70d, 2d);
        double feelsColder = ThermalEnvironmentPolicy.resolve(6d, 0d, 70d, 2d);

        assertTrue(feelsColder < calmCold);
        assertTrue(feelsColder < -0.35d);
    }

    @Test
    public void signalAlwaysRemainsBounded() {
        assertTrue(ThermalEnvironmentPolicy.resolve(60d, 80d, 100d, 40d) <= 1d);
        assertTrue(ThermalEnvironmentPolicy.resolve(-40d, -60d, 20d, -45d) >= -1d);
    }
}
