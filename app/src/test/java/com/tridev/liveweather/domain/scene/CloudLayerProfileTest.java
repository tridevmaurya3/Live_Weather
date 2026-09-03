package com.tridev.liveweather.domain.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import org.junit.Test;

public final class CloudLayerProfileTest {

    private final Gson gson = new Gson();

    @Test
    public void providerLayersMapHighMidLowIndependently() {
        WeatherResponse response = gson.fromJson(
                "{\"current\":{\"cloud_cover\":78,\"cloud_cover_low\":68," +
                        "\"cloud_cover_mid\":42,\"cloud_cover_high\":17}}",
                WeatherResponse.class
        );
        CloudLayerProfile profile = CloudLayerProfile.resolve(response.getCurrent(), 0.78d, 0d, 0d);

        assertTrue(profile.isProviderBacked());
        assertEquals(0.17d, profile.getHigh(), 0.0001d);
        assertEquals(0.42d, profile.getMid(), 0.0001d);
        assertEquals(0.68d, profile.getLow(), 0.0001d);
    }

    @Test
    public void oldCachedPayloadFallsBackWithoutBreaking() {
        WeatherResponse response = gson.fromJson(
                "{\"current\":{\"cloud_cover\":60}}",
                WeatherResponse.class
        );
        CloudLayerProfile profile = CloudLayerProfile.resolve(response.getCurrent(), 0.60d, 0d, 0d);

        assertTrue(!profile.isProviderBacked());
        assertTrue(profile.getHigh() > 0d);
        assertTrue(profile.getMid() > profile.getHigh());
        assertTrue(profile.getLow() >= profile.getMid());
    }

    @Test
    public void currentStormVerticallyConnectsCloudLayers() {
        CloudLayerProfile profile = CloudLayerProfile.resolve(null, 0.30d, 0d, 1d);
        assertTrue(profile.getLow() >= 0.86d);
        assertTrue(profile.getMid() >= 0.76d);
        assertTrue(profile.getHigh() >= 0.48d);
    }
}
