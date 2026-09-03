package com.tridev.liveweather;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.ActiveWeatherSnapshot;

import org.junit.Test;

public final class ActiveWeatherSnapshotTest {

    private final Gson gson = new Gson();

    @Test
    public void carriesObservationTimezoneAndActiveIdentityTogether() {
        WeatherResponse weather = sampleWeather();
        long now = 2_000_000L;
        ActiveWeatherSnapshot snapshot = new ActiveWeatherSnapshot(
                weather,
                25.3171d,
                83.0099d,
                now - 60_000L,
                12L,
                ActiveWeatherSnapshot.Scope.ACTIVE,
                "Chandauli",
                now
        );

        assertEquals("2026-09-03T20:00", snapshot.getObservationTime());
        assertEquals("Asia/Kolkata", snapshot.getTimezone());
        assertEquals("25.317_83.010", snapshot.getIdentityKey());
        assertEquals(12L, snapshot.getGeneration());
        assertEquals(ActiveWeatherSnapshot.Freshness.LIVE, snapshot.getFreshness());
    }

    @Test
    public void freshnessUsesOneSharedLiveCachedStaleContract() {
        long now = 20L * 60L * 60L * 1000L;
        assertEquals(
                ActiveWeatherSnapshot.Freshness.LIVE,
                ActiveWeatherSnapshot.resolveFreshness(now - 30L * 60L * 1000L, now)
        );
        assertEquals(
                ActiveWeatherSnapshot.Freshness.CACHED,
                ActiveWeatherSnapshot.resolveFreshness(now - 60L * 60L * 1000L, now)
        );
        assertEquals(
                ActiveWeatherSnapshot.Freshness.STALE,
                ActiveWeatherSnapshot.resolveFreshness(now - 4L * 60L * 60L * 1000L, now)
        );
    }

    @Test
    public void fixedCitySnapshotRetainsIsolationScope() {
        long now = 2_000_000L;
        ActiveWeatherSnapshot snapshot = new ActiveWeatherSnapshot(
                sampleWeather(),
                26.8467d,
                80.9462d,
                now,
                0L,
                ActiveWeatherSnapshot.Scope.FIXED_CITY,
                "Lucknow",
                now
        );
        assertEquals(ActiveWeatherSnapshot.Scope.FIXED_CITY, snapshot.getScope());
        assertEquals("Lucknow", snapshot.getLocationLabel());
    }

    private WeatherResponse sampleWeather() {
        return gson.fromJson(
                "{\"timezone\":\"Asia/Kolkata\",\"current\":{" +
                        "\"time\":\"2026-09-03T20:00\",\"weather_code\":0,\"is_day\":0}}",
                WeatherResponse.class
        );
    }
}
