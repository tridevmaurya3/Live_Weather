package com.tridev.liveweather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;
import com.tridev.liveweather.data.remote.dto.RainViewerResponse;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.RealityFusionPolicy;

import org.junit.Test;

public final class RealityFusionPolicyTest {
    private final Gson gson = new Gson();

    @Test public void currentRainHasHighConfidence() {
        RealityFusionPolicy.RealityState state = RealityFusionPolicy.resolve(weather(61, 0.5,
                "[0.5,0.4,0.2]", "[61,61,61]"));
        assertEquals(RealityFusionPolicy.Confidence.HIGH, state.getConfidence());
        assertEquals(RealityFusionPolicy.Intensity.MODERATE, state.getIntensity());
    }

    @Test public void weakTraceNeverBecomesCurrentRain() {
        RealityFusionPolicy.RealityState state = RealityFusionPolicy.resolve(weather(1, 0.0,
                "[0.08,0.01,0.08]", "[61,1,61]"));
        assertEquals(Integer.valueOf(1), state.getCondition().getWeatherCode());
        assertEquals(RealityFusionPolicy.Confidence.LOW, state.getConfidence());
        assertEquals(RealityFusionPolicy.Intensity.NONE, state.getIntensity());
    }

    @Test public void futurePersistentRainReportsArrivalWithoutChangingNow() {
        RealityFusionPolicy.RealityState state = RealityFusionPolicy.resolve(weather(1, 0.0,
                "[0.0,0.0,0.2]", "[1,1,61]"));
        assertEquals(Integer.valueOf(1), state.getCondition().getWeatherCode());
        assertEquals(Integer.valueOf(15), state.getNextPrecipitationMinutes());
    }

    @Test public void radarMetadataDoesNotClaimLocalRain() {
        long now = 2_000_000L;
        RainViewerResponse radar = gson.fromJson(
                "{\"radar\":{\"past\":[{\"time\":1000,\"path\":\"/v2/radar/1000\"}]}}",
                RainViewerResponse.class);
        assertEquals(RealityFusionPolicy.RadarEvidence.OBSERVED_METADATA_AVAILABLE,
                RealityFusionPolicy.radarEvidence(radar, now - 60_000L, now));
        assertEquals(RealityFusionPolicy.RadarEvidence.STALE_METADATA,
                RealityFusionPolicy.radarEvidence(radar, now - 16 * 60_000L, now));
        assertNull(RealityFusionPolicy.resolve(null).getNextPrecipitationMinutes());
    }

    private WeatherResponse weather(int code, double currentWet, String slots, String codes) {
        String json = "{\"current\":{\"time\":\"2026-09-03T12:15\",\"is_day\":1,"
                + "\"weather_code\":" + code + ",\"precipitation\":" + currentWet + ","
                + "\"rain\":" + currentWet + ",\"showers\":0,\"snowfall\":0},"
                + "\"minutely_15\":{\"time\":[\"2026-09-03T12:00\",\"2026-09-03T12:15\","
                + "\"2026-09-03T12:30\"],\"precipitation\":" + slots + ",\"rain\":" + slots
                + ",\"showers\":[0,0,0],\"snowfall\":[0,0,0],\"weather_code\":" + codes + "}}";
        return gson.fromJson(json, WeatherResponse.class);
    }
}
