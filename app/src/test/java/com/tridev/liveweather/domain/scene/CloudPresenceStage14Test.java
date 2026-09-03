package com.tridev.liveweather.domain.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;

import org.junit.Test;

public final class CloudPresenceStage14Test {

    private final Gson gson = new Gson();

    @Test
    public void upcomingRainDeepensFarCloudsWithoutDeclaringCurrentRain() {
        WeatherResponse baseline = weather(false);
        WeatherResponse approaching = weather(true);

        CloudPresenceState baselineState = resolve(baseline);
        CloudPresenceState approachingState = resolve(approaching);

        assertNotEquals(CloudPresenceState.Mode.PRECIPITATION, approachingState.getMode());
        assertNotEquals(CloudPresenceState.Mode.STORM, approachingState.getMode());
        assertTrue(approachingState.getFarLayer() > baselineState.getFarLayer() + 0.05d);
        assertTrue(approachingState.getMidLayer() > baselineState.getMidLayer());
        assertEquals(baselineState.getStormCeiling(), approachingState.getStormCeiling(), 0.0001d);
        assertTrue(approachingState.getBrightness() < baselineState.getBrightness());
    }

    private WeatherResponse weather(boolean futureRain) {
        String futurePrecip = futureRain ? "0.9" : "0";
        String futureCode = futureRain ? "61" : "1";
        return gson.fromJson(
                "{\"current\":{\"time\":\"2026-09-03T12:00\",\"weather_code\":1,\"cloud_cover\":18," +
                        "\"cloud_cover_low\":15,\"cloud_cover_mid\":12,\"cloud_cover_high\":10,\"visibility\":18000}," +
                        "\"minutely_15\":{\"time\":[\"2026-09-03T11:00\",\"2026-09-03T11:15\",\"2026-09-03T11:30\",\"2026-09-03T11:45\",\"2026-09-03T12:00\",\"2026-09-03T12:15\"]," +
                        "\"precipitation\":[0,0,0,0,0," + futurePrecip + "],\"rain\":[0,0,0,0,0," + futurePrecip + "]," +
                        "\"weather_code\":[1,1,1,1,1," + futureCode + "],\"cloud_cover\":[18,18,18,18,18,18],\"visibility\":[18000,18000,18000,18000,18000,10000]}}",
                WeatherResponse.class
        );
    }

    private CloudPresenceState resolve(WeatherResponse weather) {
        return CloudPresenceResolver.resolve(
                weather,
                LiveConditionResolver.resolve(weather),
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                1d
        );
    }
}
