package com.tridev.liveweather.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.cosinekitty.astronomy.Astronomy;
import io.github.cosinekitty.astronomy.Body;
import io.github.cosinekitty.astronomy.Direction;
import io.github.cosinekitty.astronomy.IlluminationInfo;
import io.github.cosinekitty.astronomy.Observer;
import io.github.cosinekitty.astronomy.Time;

/**
 * Daily observer-relative Sun/Moon events and lunar phase progression.
 */
public final class CelestialForecastEngine {

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEE d", Locale.getDefault());

    private CelestialForecastEngine() {
    }

    @NonNull
    public static List<CelestialDayState> build(
            @NonNull WeatherResponse weather,
            double latitude,
            double longitude,
            int dayCount,
            long epochMillis
    ) {
        int count = Math.max(1, Math.min(14, dayCount));
        ZoneId zone = resolveZone(weather.getTimezone());
        LocalDate today = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate();
        double elevation = weather.getElevation() == null ? 0d : weather.getElevation();
        Observer observer = new Observer(latitude, longitude, elevation);

        List<CelestialDayState> result = new ArrayList<>();
        for (int offset = 0; offset < count; offset++) {
            LocalDate date = today.plusDays(offset);
            ZonedDateTime noon = date.atTime(12, 0).atZone(zone);
            Time noonTime = Time.fromMillisecondsSince1970(noon.toInstant().toEpochMilli());

            double phaseAngle = normalize(Astronomy.moonPhase(noonTime));
            IlluminationInfo illuminationInfo = Astronomy.illumination(Body.Moon, noonTime);
            double illumination = clamp(illuminationInfo.getPhaseFraction() * 100d, 0d, 100d);
            boolean waxing = phaseAngle > 0d && phaseAngle < 180d;

            long startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli();
            Time startTime = Time.fromMillisecondsSince1970(startMillis);

            String sunrise = eventTime(
                    Astronomy.searchRiseSet(Body.Sun, observer, Direction.Rise, startTime, 1.1d),
                    date,
                    zone
            );
            String sunset = eventTime(
                    Astronomy.searchRiseSet(Body.Sun, observer, Direction.Set, startTime, 1.1d),
                    date,
                    zone
            );
            String moonrise = eventTime(
                    Astronomy.searchRiseSet(Body.Moon, observer, Direction.Rise, startTime, 1.1d),
                    date,
                    zone
            );
            String moonset = eventTime(
                    Astronomy.searchRiseSet(Body.Moon, observer, Direction.Set, startTime, 1.1d),
                    date,
                    zone
            );

            result.add(new CelestialDayState(
                    dayLabel(date, today, offset),
                    phaseName(phaseAngle),
                    illumination,
                    sunrise,
                    sunset,
                    moonrise,
                    moonset,
                    waxing
            ));
        }
        return result;
    }

    @NonNull
    public static String phaseSymbol(@NonNull CelestialDayState day) {
        double illumination = day.getIlluminationPercent();
        String phase = day.getPhaseName();
        if (phase.contains("New")) {
            return "●";
        }
        if (phase.contains("Full")) {
            return "○";
        }
        if (illumination < 35d) {
            return day.isWaxing() ? "◔" : "◕";
        }
        if (illumination < 65d) {
            return day.isWaxing() ? "◑" : "◐";
        }
        return day.isWaxing() ? "◕" : "◔";
    }

    @NonNull
    private static String dayLabel(LocalDate date, LocalDate today, int offset) {
        if (offset == 0) {
            return "Today";
        }
        if (offset == 1) {
            return "Tomorrow";
        }
        return date.format(DAY);
    }

    @NonNull
    private static String phaseName(double angle) {
        if (angle < 22.5d || angle >= 337.5d) {
            return "New Moon";
        }
        if (angle < 67.5d) {
            return "Waxing Crescent";
        }
        if (angle < 112.5d) {
            return "First Quarter";
        }
        if (angle < 157.5d) {
            return "Waxing Gibbous";
        }
        if (angle < 202.5d) {
            return "Full Moon";
        }
        if (angle < 247.5d) {
            return "Waning Gibbous";
        }
        if (angle < 292.5d) {
            return "Third Quarter";
        }
        return "Waning Crescent";
    }

    @NonNull
    private static String eventTime(
            @Nullable Time event,
            @NonNull LocalDate requestedDate,
            @NonNull ZoneId zone
    ) {
        if (event == null) {
            return "—";
        }
        ZonedDateTime local = Instant.ofEpochMilli(event.toMillisecondsSince1970()).atZone(zone);
        if (!local.toLocalDate().equals(requestedDate)) {
            return "—";
        }
        return local.format(CLOCK);
    }

    @NonNull
    private static ZoneId resolveZone(@Nullable String timezone) {
        if (timezone != null && !timezone.trim().isEmpty()) {
            try {
                return ZoneId.of(timezone);
            } catch (ZoneRulesException ignored) {
                // Fall through to device zone.
            }
        }
        return ZoneId.systemDefault();
    }

    private static double normalize(double degrees) {
        double value = degrees % 360d;
        return value < 0d ? value + 360d : value;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
