package com.tridev.liveweather.domain.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import org.junit.Test;

public final class AtmosphericObscurationPolicyTest {

    private final Gson gson = new Gson();

    @Test
    public void pollutedDryAirDoesNotBecomeFog() {
        WeatherResponse response = gson.fromJson(
                "{\"current\":{\"relative_humidity_2m\":42,\"temperature_2m\":31," +
                        "\"dew_point_2m\":15,\"visibility\":3500}}",
                WeatherResponse.class
        );
        AtmosphericObscurationPolicy.State state = AtmosphericObscurationPolicy.resolve(
                response.getCurrent(), 3, 0.9d, 0.22d);

        assertEquals(0d, state.getFogIntensity(), 0.0001d);
        assertEquals(0.9d, state.getHazeIntensity(), 0.0001d);
    }

    @Test
    public void saturatedLowVisibilityAirCanResolveMist() {
        WeatherResponse response = gson.fromJson(
                "{\"current\":{\"relative_humidity_2m\":97,\"temperature_2m\":18," +
                        "\"dew_point_2m\":17.5,\"visibility\":2500}}",
                WeatherResponse.class
        );
        AtmosphericObscurationPolicy.State state = AtmosphericObscurationPolicy.resolve(
                response.getCurrent(), 3, 0d, 0.25d);

        assertTrue(state.getFogIntensity() > 0.35d);
        assertTrue(state.getMistIntensity() > 0.25d);
    }

    @Test
    public void explicitWmoFogRemainsAuthoritative() {
        WeatherResponse response = gson.fromJson(
                "{\"current\":{\"relative_humidity_2m\":90,\"visibility\":900}}",
                WeatherResponse.class
        );
        AtmosphericObscurationPolicy.State state = AtmosphericObscurationPolicy.resolve(
                response.getCurrent(), 45, 0.35d, 0.12d);

        assertTrue(state.getFogIntensity() >= 0.52d);
    }
}
