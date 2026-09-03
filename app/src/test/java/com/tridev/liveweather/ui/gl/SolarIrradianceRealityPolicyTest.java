package com.tridev.liveweather.ui.gl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import org.junit.Test;

public final class SolarIrradianceRealityPolicyTest {

    @Test
    public void missingSolarFieldsPreserveNeutralStage12Behavior() {
        WeatherResponse.CurrentWeather current = current("{\"temperature_2m\":28.0}");
        SolarIrradianceRealityPolicy.State state = SolarIrradianceRealityPolicy.resolve(current, 48d);

        assertFalse(state.hasObservation());
        assertTrue(Math.abs(state.getGlobalLightFactor() - 1d) < 0.000001d);
        assertTrue(Math.abs(state.getDirectionalVisibilityFactor() - 1d) < 0.000001d);
    }

    @Test
    public void strongDirectSunKeepsDirectionalLightAndGlobalExposureHigh() {
        WeatherResponse.CurrentWeather current = current(
                "{\"shortwave_radiation\":800.0,\"direct_radiation\":620.0,\"diffuse_radiation\":160.0,\"direct_normal_irradiance\":850.0}"
        );
        SolarIrradianceRealityPolicy.State state = SolarIrradianceRealityPolicy.resolve(current, 50d);

        assertTrue(state.hasObservation());
        assertTrue(state.getGlobalLightFactor() > 0.95d);
        assertTrue(state.getDirectLightFactor() > 0.90d);
        assertTrue(state.getDirectionalVisibilityFactor() > 0.94d);
        assertTrue(state.getDiffuseFraction() < 0.30d);
    }

    @Test
    public void diffuseOvercastKeepsAmbientLightButSuppressesSolarBeam() {
        WeatherResponse.CurrentWeather current = current(
                "{\"shortwave_radiation\":180.0,\"direct_radiation\":35.0,\"diffuse_radiation\":125.0,\"direct_normal_irradiance\":35.0}"
        );
        SolarIrradianceRealityPolicy.State state = SolarIrradianceRealityPolicy.resolve(current, 50d);

        assertTrue(state.getGlobalLightFactor() > 0.68d);
        assertTrue(state.getGlobalLightFactor() < 0.78d);
        assertTrue(state.getDirectLightFactor() < 0.08d);
        assertTrue(state.getDirectionalVisibilityFactor() < 0.30d);
        assertTrue(state.getDiffuseFraction() > 0.70d);
    }

    @Test
    public void nightSolarZeroCannotCreateVisibleSun() {
        WeatherResponse.CurrentWeather current = current(
                "{\"shortwave_radiation\":0.0,\"direct_radiation\":0.0,\"diffuse_radiation\":0.0,\"direct_normal_irradiance\":0.0}"
        );
        SolarIrradianceRealityPolicy.State state = SolarIrradianceRealityPolicy.resolve(current, -8d);

        assertTrue(state.hasObservation());
        assertTrue(state.getDirectLightFactor() == 0d);
        assertTrue(state.getDirectionalVisibilityFactor() == 0d);
    }

    private static WeatherResponse.CurrentWeather current(String json) {
        WeatherResponse response = new Gson().fromJson("{\"current\":" + json + "}", WeatherResponse.class);
        return response.getCurrent();
    }
}
