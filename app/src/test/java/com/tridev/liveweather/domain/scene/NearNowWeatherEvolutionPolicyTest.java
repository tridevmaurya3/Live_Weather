package com.tridev.liveweather.domain.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import org.junit.Test;

public final class NearNowWeatherEvolutionPolicyTest {

    private final Gson gson = new Gson();

    @Test
    public void futureRainCreatesBoundedApproachWithoutRecentTail() {
        WeatherResponse weather = parse(
                "{\"current\":{\"time\":\"2026-09-03T12:00\",\"weather_code\":2,\"cloud_cover\":25,\"visibility\":18000}," +
                        "\"minutely_15\":{\"time\":[\"2026-09-03T11:00\",\"2026-09-03T11:15\",\"2026-09-03T11:30\",\"2026-09-03T11:45\",\"2026-09-03T12:00\",\"2026-09-03T12:15\",\"2026-09-03T12:30\"]," +
                        "\"precipitation\":[0,0,0,0,0,0.9,0.4],\"rain\":[0,0,0,0,0,0.9,0.4]," +
                        "\"weather_code\":[2,2,2,2,2,61,61],\"cloud_cover\":[25,25,25,25,25,72,80],\"visibility\":[18000,18000,18000,18000,18000,9000,7000]}}"
        );

        NearNowWeatherEvolutionPolicy.State state = NearNowWeatherEvolutionPolicy.resolve(weather);
        assertTrue(state.getApproachEnvelope() > 0.45d);
        assertTrue(state.getHorizonVeil() > 0.25d);
        assertEquals(0d, state.getRecentExitEnvelope(), 0d);
        assertTrue(state.getApproachEnvelope() <= 1d);
    }

    @Test
    public void recentRainLeavesSmallExitTailWhenCurrentTurnsDry() {
        WeatherResponse weather = parse(
                "{\"current\":{\"time\":\"2026-09-03T12:00\",\"weather_code\":2,\"cloud_cover\":45}," +
                        "\"minutely_15\":{\"time\":[\"2026-09-03T11:30\",\"2026-09-03T11:45\",\"2026-09-03T12:00\",\"2026-09-03T12:15\"]," +
                        "\"precipitation\":[0.2,0.8,0,0],\"rain\":[0.2,0.8,0,0],\"weather_code\":[61,61,2,2],\"cloud_cover\":[70,65,45,40]}}"
        );

        NearNowWeatherEvolutionPolicy.State state = NearNowWeatherEvolutionPolicy.resolve(weather);
        assertTrue(state.getRecentExitEnvelope() > 0.30d);
        assertEquals(0d, state.getApproachEnvelope(), 0.0001d);
    }

    @Test
    public void futureThunderstormIsLeftToStage8SeverePolicy() {
        WeatherResponse weather = parse(
                "{\"current\":{\"time\":\"2026-09-03T12:00\",\"weather_code\":3,\"cloud_cover\":90}," +
                        "\"minutely_15\":{\"time\":[\"2026-09-03T12:00\",\"2026-09-03T12:15\"]," +
                        "\"precipitation\":[0,2.0],\"rain\":[0,2.0],\"weather_code\":[3,95],\"cloud_cover\":[90,90]}}"
        );

        NearNowWeatherEvolutionPolicy.State state = NearNowWeatherEvolutionPolicy.resolve(weather);
        assertEquals(0d, state.getApproachEnvelope(), 0d);
        assertEquals(0d, state.getHorizonVeil(), 0d);
        assertTrue(SevereWeatherVisualPolicy.cloudTransitionEnvelope(weather) > 0d);
    }

    @Test
    public void currentPrecipitationDisablesForecastAdjacentCue() {
        WeatherResponse weather = parse(
                "{\"current\":{\"time\":\"2026-09-03T12:00\",\"weather_code\":61,\"rain\":0.5,\"cloud_cover\":90}," +
                        "\"minutely_15\":{\"time\":[\"2026-09-03T12:00\",\"2026-09-03T12:15\"],\"precipitation\":[0.5,1.0],\"rain\":[0.5,1.0],\"weather_code\":[61,61],\"cloud_cover\":[90,95]}}"
        );

        NearNowWeatherEvolutionPolicy.State state = NearNowWeatherEvolutionPolicy.resolve(weather);
        assertEquals(0d, state.getApproachEnvelope(), 0d);
        assertEquals(0d, state.getRecentExitEnvelope(), 0d);
        assertEquals(0d, state.getHorizonVeil(), 0d);
    }

    @Test
    public void unsupportedVisibilityDropCannotInventFogCue() {
        WeatherResponse weather = parse(
                "{\"current\":{\"time\":\"2026-09-03T12:00\",\"weather_code\":1,\"cloud_cover\":10,\"visibility\":20000}," +
                        "\"minutely_15\":{\"time\":[\"2026-09-03T12:00\",\"2026-09-03T12:15\"],\"weather_code\":[1,1],\"cloud_cover\":[10,10],\"visibility\":[20000,3000]}}"
        );

        NearNowWeatherEvolutionPolicy.State state = NearNowWeatherEvolutionPolicy.resolve(weather);
        assertEquals(0d, state.getHorizonVeil(), 0d);
    }

    private WeatherResponse parse(String json) {
        return gson.fromJson(json, WeatherResponse.class);
    }
}
