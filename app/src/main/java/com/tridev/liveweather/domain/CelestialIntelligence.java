package com.tridev.liveweather.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.cosinekitty.astronomy.Astronomy;
import io.github.cosinekitty.astronomy.MoonQuarterInfo;
import io.github.cosinekitty.astronomy.Time;

public final class CelestialIntelligence {

    private static final DateTimeFormatter EVENT_FORMAT =
            DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.getDefault());

    private CelestialIntelligence() {
    }

    @NonNull
    public static String sunNow(
            @NonNull WeatherResponse weather,
            double latitude,
            double longitude,
            long epochMillis
    ) {
        SkyRealityState sky = SkyRealityEngine.calculate(weather, latitude, longitude, epochMillis);
        return String.format(
                Locale.getDefault(),
                "Sun %.1f° altitude · %.0f° azimuth · %s",
                sky.getSunAltitude(),
                sky.getSunAzimuth(),
                sky.getSunAltitude() >= 0d ? "above horizon" : "below horizon"
        );
    }

    @NonNull
    public static String moonNow(
            @NonNull WeatherResponse weather,
            double latitude,
            double longitude,
            long epochMillis
    ) {
        SkyRealityState sky = SkyRealityEngine.calculate(weather, latitude, longitude, epochMillis);
        return String.format(
                Locale.getDefault(),
                "%s · %.0f%% illuminated · %.1f° altitude · %.0f° azimuth",
                sky.getMoonPhaseName(),
                sky.getMoonIlluminationPercent(),
                sky.getMoonAltitude(),
                sky.getMoonAzimuth()
        );
    }

    @NonNull
    public static String daylightProgress(@NonNull WeatherResponse weather, long epochMillis) {
        WeatherResponse.DailyWeather daily = weather.getDaily();
        String sunriseText = daily == null ? null : WeatherFormatter.valueAt(daily.getSunrise(), 0);
        String sunsetText = daily == null ? null : WeatherFormatter.valueAt(daily.getSunset(), 0);
        if (sunriseText == null || sunsetText == null) {
            return "Daylight progress unavailable.";
        }
        try {
            ZoneId zone = resolveZone(weather.getTimezone());
            ZonedDateTime now = Instant.ofEpochMilli(epochMillis).atZone(zone);
            ZonedDateTime sunrise = LocalDateTime.parse(sunriseText).atZone(zone);
            ZonedDateTime sunset = LocalDateTime.parse(sunsetText).atZone(zone);
            long total = Math.max(1L, Duration.between(sunrise, sunset).toMinutes());
            long elapsed = Duration.between(sunrise, now).toMinutes();
            if (now.isBefore(sunrise)) {
                return "Before sunrise · daylight begins at " + sunrise.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()));
            }
            if (now.isAfter(sunset)) {
                return "After sunset · next daylight cycle begins tomorrow.";
            }
            int percent = (int) Math.round(Math.max(0d, Math.min(1d, elapsed / (double) total)) * 100d);
            ZonedDateTime solarMidpoint = sunrise.plusMinutes(total / 2L);
            return String.format(
                    Locale.getDefault(),
                    "Daylight %d%% complete · solar midpoint about %s",
                    percent,
                    solarMidpoint.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
            );
        } catch (DateTimeParseException exception) {
            return "Daylight progress unavailable.";
        }
    }

    @NonNull
    public static List<String> nextMoonQuarterEvents(
            @NonNull WeatherResponse weather,
            long epochMillis,
            int count
    ) {
        int eventCount = Math.max(1, Math.min(8, count));
        ZoneId zone = resolveZone(weather.getTimezone());
        Time start = Time.fromMillisecondsSince1970(epochMillis);
        MoonQuarterInfo quarter = Astronomy.searchMoonQuarter(start);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < eventCount; i++) {
            if (i > 0) {
                quarter = Astronomy.nextMoonQuarter(quarter);
            }
            ZonedDateTime local = Instant.ofEpochMilli(
                    quarter.getTime().toMillisecondsSince1970()
            ).atZone(zone);
            result.add(quarterName(quarter.getQuarter()) + " · " + local.format(EVENT_FORMAT));
        }
        return result;
    }

    @NonNull
    public static String visibilitySummary(
            @NonNull WeatherResponse weather,
            double latitude,
            double longitude,
            long epochMillis
    ) {
        SkyRealityState sky = SkyRealityEngine.calculate(weather, latitude, longitude, epochMillis);
        return String.format(
                Locale.getDefault(),
                "%s · stars %d%% visible estimate · scene light %d%%",
                sky.getSkyStage(),
                sky.getStarVisibilityPercent(),
                sky.getAmbientLightPercent()
        );
    }

    @NonNull
    private static String quarterName(int quarter) {
        switch (quarter) {
            case 0: return "New Moon";
            case 1: return "First Quarter";
            case 2: return "Full Moon";
            case 3: return "Third Quarter";
            default: return "Moon phase";
        }
    }

    @NonNull
    private static ZoneId resolveZone(@Nullable String timezone) {
        if (timezone != null && !timezone.trim().isEmpty()) {
            try {
                return ZoneId.of(timezone);
            } catch (ZoneRulesException ignored) {
            }
        }
        return ZoneId.systemDefault();
    }
}
