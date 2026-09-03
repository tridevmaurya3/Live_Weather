package com.tridev.liveweather.domain.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import org.junit.Test;

public final class SevereWeatherVisualPolicyTest {

    private final Gson gson = new Gson();

    @Test
    public void currentThunderstormOwnsFullEnvelope() {
        WeatherResponse response = gson.fromJson(
                "{\"current\":{\"time\":\"2026-09-03T12:00\",\"weather_code\":95}}",
                WeatherResponse.class
        );
        assertEquals(1d, SevereWeatherVisualPolicy.cloudTransitionEnvelope(response), 0d);
    }

    @Test
    public void approachingStormOnlyAddsBoundedCloudCue() {
        WeatherResponse response = gson.fromJson(
                "{\"current\":{\"time\":\"2026-09-03T12:00\",\"weather_code\":3}," +
                        "\"minutely_15\":{\"time\":[\"2026-09-03T11:45\",\"2026-09-03T12:00\"," +
                        "\"2026-09-03T12:15\",\"2026-09-03T12:30\"]," +
                        "\"weather_code\":[3,3,95,95]}}",
                WeatherResponse.class
        );
        double envelope = SevereWeatherVisualPolicy.cloudTransitionEnvelope(response);
        assertEquals(0.48d, envelope, 0.0001d);
        assertTrue(envelope < 1d);
    }

    @Test
    public void clearNeighbourhoodHasNoSevereCue() {
        WeatherResponse response = gson.fromJson(
                "{\"current\":{\"time\":\"2026-09-03T12:00\",\"weather_code\":2}," +
                        "\"minutely_15\":{\"time\":[\"2026-09-03T11:45\",\"2026-09-03T12:00\"," +
                        "\"2026-09-03T12:15\"],\"weather_code\":[2,2,3]}}",
                WeatherResponse.class
        );
        assertEquals(0d, SevereWeatherVisualPolicy.cloudTransitionEnvelope(response), 0d);
    }
}
