package com.tridev.liveweather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.tridev.liveweather.core.DataReliabilityPolicy;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;

import org.junit.Test;

/** Regression locks for the truth and freshness rules preserved by the upgrade. */
public final class LiveRealityBaselineContractTest {

    private static final long MINUTE = 60_000L;
    private final Gson gson = new Gson();

    @Test
    public void weakAdjacentTraceDoesNotBecomeCurrentRain() {
        WeatherResponse response = weather(
                1, 0.0, 0.0,
                new double[]{0.08, 0.01, 0.08},
                new int[]{61, 1, 61}
        );

        LiveConditionResolver.ResolvedCondition resolved = LiveConditionResolver.resolve(response);

        assertEquals(Integer.valueOf(1), resolved.getWeatherCode());
        assertEquals(0.0, resolved.getPrecipitationSignalMm(), 0.0001);
        assertTrue(resolved.getSource().contains("unconfirmed"));
    }

    @Test
    public void currentWeatherCodeKeepsCurrentRainTruth() {
        WeatherResponse response = weather(
                61, 0.0, 0.0,
                new double[]{0.0, 0.0, 0.0},
                new int[]{1, 1, 1}
        );

        LiveConditionResolver.ResolvedCondition resolved = LiveConditionResolver.resolve(response);

        assertEquals(Integer.valueOf(61), resolved.getWeatherCode());
        assertEquals("Current precipitation weather code", resolved.getSource());
    }

    @Test
    public void corroboratedNearestSignalCanBecomeRain() {
        WeatherResponse response = weather(
                1, 0.08, 0.08,
                new double[]{0.08, 0.08, 0.0},
                new int[]{61, 61, 1}
        );

        LiveConditionResolver.ResolvedCondition resolved = LiveConditionResolver.resolve(response);

        assertEquals(Integer.valueOf(61), resolved.getWeatherCode());
        assertEquals("Corroborated short-term rain signal", resolved.getSource());
        assertTrue(resolved.getPrecipitationSignalMm() > 0.0);
    }

    @Test
    public void freshnessBoundariesRemainTruthful() {
        long now = 1_000_000_000L;

        assertEquals(
                DataReliabilityPolicy.Freshness.RECENT,
                DataReliabilityPolicy.weatherFreshness(now - 45L * MINUTE, now)
        );
        assertEquals(
                DataReliabilityPolicy.Freshness.AGING,
                DataReliabilityPolicy.weatherFreshness(now - 46L * MINUTE, now)
        );
        assertEquals(
                DataReliabilityPolicy.Freshness.STALE,
                DataReliabilityPolicy.weatherFreshness(now - 4L * 60L * MINUTE, now)
        );
        assertEquals(
                DataReliabilityPolicy.Freshness.VERY_STALE,
                DataReliabilityPolicy.weatherFreshness(now - 13L * 60L * MINUTE, now)
        );
    }

    @Test
    public void locationIdentityUsesExistingTolerance() {
        assertTrue(DataReliabilityPolicy.sameLocation(25.000, 83.000, 25.009, 83.009));
        assertFalse(DataReliabilityPolicy.sameLocation(25.000, 83.000, 25.011, 83.000));
        assertFalse(DataReliabilityPolicy.sameLocation(Double.NaN, 83.000, 25.000, 83.000));
    }

    @Test
    public void unknownCacheAgeIsNeverRecent() {
        assertEquals(
                DataReliabilityPolicy.Freshness.UNKNOWN,
                DataReliabilityPolicy.weatherFreshness(0L, System.currentTimeMillis())
        );
    }

    private WeatherResponse weather(
            int currentCode,
            double currentPrecipitation,
            double currentRain,
            double[] minutelyRain,
            int[] minutelyCodes
    ) {
        String json = "{"
                + "\"current\":{"
                + "\"time\":\"2026-09-03T12:15\","
                + "\"is_day\":1,"
                + "\"weather_code\":" + currentCode + ","
                + "\"precipitation\":" + currentPrecipitation + ","
                + "\"rain\":" + currentRain + ","
                + "\"showers\":0.0,\"snowfall\":0.0},"
                + "\"minutely_15\":{"
                + "\"time\":[\"2026-09-03T12:00\",\"2026-09-03T12:15\",\"2026-09-03T12:30\"],"
                + "\"precipitation\":" + doubles(minutelyRain) + ","
                + "\"rain\":" + doubles(minutelyRain) + ","
                + "\"showers\":[0.0,0.0,0.0],"
                + "\"snowfall\":[0.0,0.0,0.0],"
                + "\"weather_code\":" + integers(minutelyCodes)
                + "}}";
        return gson.fromJson(json, WeatherResponse.class);
    }

    private String doubles(double[] values) {
        return "[" + values[0] + "," + values[1] + "," + values[2] + "]";
    }

    private String integers(int[] values) {
        return "[" + values[0] + "," + values[1] + "," + values[2] + "]";
    }
}
