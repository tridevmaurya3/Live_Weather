package com.tridev.liveweather.domain.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;

import org.junit.Test;

public final class CloudPresenceStage8Test {

    private final Gson gson = new Gson();

    @Test
    public void realVerticalLayersDriveExistingDepthChannels() {
        WeatherResponse weather = gson.fromJson(
                "{\"current\":{\"time\":\"2026-09-03T12:00\",\"weather_code\":2," +
                        "\"cloud_cover\":70,\"cloud_cover_low\":82,\"cloud_cover_mid\":46," +
                        "\"cloud_cover_high\":18},\"minutely_15\":{\"time\":[\"2026-09-03T12:00\"]," +
                        "\"cloud_cover\":[70],\"weather_code\":[2]}}",
                WeatherResponse.class
        );
        CloudPresenceState state = CloudPresenceResolver.resolve(
                weather,
                LiveConditionResolver.resolve(weather),
                0d, 0d, 0d, 0d, 0d, 0d, 1d
        );

        assertTrue(state.getNearLayer() > state.getMidLayer());
        assertTrue(state.getMidLayer() > state.getFarLayer());
    }

    @Test
    public void upcomingThunderstormDarkensCloudsWithoutDeclaringStorm() {
        WeatherResponse weather = gson.fromJson(
                "{\"current\":{\"time\":\"2026-09-03T12:00\",\"weather_code\":3," +
                        "\"cloud_cover\":90,\"cloud_cover_low\":88,\"cloud_cover_mid\":80," +
                        "\"cloud_cover_high\":55},\"minutely_15\":{\"time\":[\"2026-09-03T12:00\"," +
                        "\"2026-09-03T12:15\"],\"cloud_cover\":[90,95],\"weather_code\":[3,95]}}",
                WeatherResponse.class
        );
        CloudPresenceState state = CloudPresenceResolver.resolve(
                weather,
                LiveConditionResolver.resolve(weather),
                0d, 0d, 0d, 0d, 0d, 0d, 1d
        );

        assertEquals(CloudPresenceState.Mode.OVERCAST, state.getMode());
        assertTrue(state.getStormCeiling() > 0d);
        assertTrue(state.getBrightness() < 1d);
    }
}
