package com.tridev.liveweather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tridev.liveweather.core.LiveDataFreshnessPolicy;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import org.junit.Test;

public final class LiveDataFreshnessPolicyTest {

    private final Gson gson = new Gson();

    @Test
    public void rainAndStormUseFiveMinuteWindow() {
        assertEquals(5L * 60L * 1000L, interval(61));
        assertEquals(5L * 60L * 1000L, interval(95));
    }

    @Test
    public void clearAndCloudyUseTenMinuteWindow() {
        assertEquals(10L * 60L * 1000L, interval(0));
        assertEquals(10L * 60L * 1000L, interval(3));
    }

    @Test
    public void inFlightRequestNeverDuplicates() {
        assertFalse(LiveDataFreshnessPolicy.shouldRefresh(1L, 1_000_000L, 1L, true));
    }

    @Test
    public void missingTimestampRefreshesImmediately() {
        assertTrue(LiveDataFreshnessPolicy.shouldRefresh(0L, 1_000_000L, 600_000L, false));
    }

    @Test
    public void refreshesAtBoundaryButNotBefore() {
        long updatedAt = 1_000_000L;
        long interval = 600_000L;
        assertFalse(LiveDataFreshnessPolicy.shouldRefresh(
                updatedAt, updatedAt + interval - 1L, interval, false
        ));
        assertTrue(LiveDataFreshnessPolicy.shouldRefresh(
                updatedAt, updatedAt + interval, interval, false
        ));
    }

    private long interval(int code) {
        WeatherResponse response = gson.fromJson(
                "{\"current\":{\"time\":\"2026-09-03T12:00\",\"weather_code\":"
                        + code + ",\"is_day\":1}}",
                WeatherResponse.class
        );
        return LiveDataFreshnessPolicy.refreshIntervalMillis(response);
    }
}
