package com.tridev.liveweather.ui.weather;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.local.UnitPreferences;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Shared weather formatting and WMO interpretation helpers.
 *
 * Phase 16 keeps provider/cache values metric and converts only at the final
 * presentation boundary. The active unit set is configured once by the
 * Application and refreshed immediately when the user changes Units settings.
 */
public final class WeatherFormatter {

    private static final DateTimeFormatter HOUR_FORMATTER =
            DateTimeFormatter.ofPattern("h a", Locale.getDefault());
    private static final DateTimeFormatter DAY_FORMATTER =
            DateTimeFormatter.ofPattern("EEE", Locale.getDefault());
    private static final DateTimeFormatter UPDATED_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());

    @NonNull
    private static volatile UnitPreferences.Units activeUnits = UnitPreferences.metric();

    private WeatherFormatter() {
    }

    public static void configure(@NonNull UnitPreferences.Units units) {
        activeUnits = units;
    }

    @NonNull
    public static UnitPreferences.Units getActiveUnits() {
        return activeUnits;
    }

    @Nullable
    public static Double temperatureValue(@Nullable Double celsius) {
        if (celsius == null) return null;
        return activeUnits.getTemperature() == UnitPreferences.TemperatureUnit.FAHRENHEIT
                ? celsius * 9d / 5d + 32d
                : celsius;
    }

    public static String temperature(@Nullable Double value) {
        Double converted = temperatureValue(value);
        return converted == null ? "—°" : Math.round(converted) + "°";
    }

    public static String temperatureUnitSymbol() {
        return activeUnits.getTemperature() == UnitPreferences.TemperatureUnit.FAHRENHEIT
                ? "°F"
                : "°C";
    }

    public static String percent(@Nullable Double value) {
        return value == null ? "—" : Math.round(value) + "%";
    }

    @Nullable
    public static Double windValue(@Nullable Double kilometresPerHour) {
        if (kilometresPerHour == null) return null;
        return activeUnits.getWind() == UnitPreferences.WindUnit.MPH
                ? kilometresPerHour * 0.6213711922d
                : kilometresPerHour;
    }

    public static String wind(@Nullable Double value) {
        Double converted = windValue(value);
        if (converted == null) return "—";
        String suffix = activeUnits.getWind() == UnitPreferences.WindUnit.MPH
                ? "mph"
                : "km/h";
        return String.format(Locale.getDefault(), "%.0f %s", converted, suffix);
    }

    @Nullable
    public static Double precipitationValue(@Nullable Double millimetres) {
        if (millimetres == null) return null;
        return activeUnits.getPrecipitation() == UnitPreferences.PrecipitationUnit.INCH
                ? millimetres / 25.4d
                : millimetres;
    }

    public static String precipitation(@Nullable Double value) {
        Double converted = precipitationValue(value);
        if (converted == null) return "—";
        if (activeUnits.getPrecipitation() == UnitPreferences.PrecipitationUnit.INCH) {
            return String.format(Locale.getDefault(), "%.2f in", converted);
        }
        return String.format(Locale.getDefault(), "%.1f mm", converted);
    }

    @Nullable
    public static Double pressureValue(@Nullable Double hectopascals) {
        if (hectopascals == null) return null;
        return activeUnits.getPressure() == UnitPreferences.PressureUnit.INHG
                ? hectopascals * 0.0295299830714d
                : hectopascals;
    }

    public static String pressure(@Nullable Double hectopascals) {
        Double converted = pressureValue(hectopascals);
        if (converted == null) return "—";
        if (activeUnits.getPressure() == UnitPreferences.PressureUnit.INHG) {
            return String.format(Locale.getDefault(), "%.2f inHg", converted);
        }
        return String.format(Locale.getDefault(), "%.0f hPa", converted);
    }

    @Nullable
    public static Double distanceValueFromMeters(@Nullable Double meters) {
        if (meters == null) return null;
        double kilometres = meters / 1000d;
        return activeUnits.getDistance() == UnitPreferences.DistanceUnit.MILE
                ? kilometres * 0.6213711922d
                : kilometres;
    }

    public static String visibilityDistance(@Nullable Double meters) {
        Double converted = distanceValueFromMeters(meters);
        if (converted == null) return "—";
        String suffix = activeUnits.getDistance() == UnitPreferences.DistanceUnit.MILE
                ? "mi"
                : "km";
        return String.format(Locale.getDefault(), "%.1f %s", converted, suffix);
    }

    public static String coordinates(double latitude, double longitude) {
        return String.format(
                Locale.getDefault(),
                "Current location · %.3f, %.3f",
                latitude,
                longitude
        );
    }

    public static String savedCoordinates(double latitude, double longitude) {
        return String.format(
                Locale.getDefault(),
                "Last saved location · %.3f, %.3f",
                latitude,
                longitude
        );
    }

    public static String condition(@Nullable Integer code) {
        if (code == null) {
            return "Weather unavailable";
        }

        switch (code) {
            case 0:
                return "Clear sky";
            case 1:
                return "Mainly clear";
            case 2:
                return "Partly cloudy";
            case 3:
                return "Overcast";
            case 45:
            case 48:
                return "Fog";
            case 51:
                return "Light drizzle";
            case 53:
                return "Drizzle";
            case 55:
                return "Dense drizzle";
            case 56:
            case 57:
                return "Freezing drizzle";
            case 61:
                return "Light rain";
            case 63:
                return "Rain";
            case 65:
                return "Heavy rain";
            case 66:
            case 67:
                return "Freezing rain";
            case 71:
                return "Light snow";
            case 73:
                return "Snow";
            case 75:
                return "Heavy snow";
            case 77:
                return "Snow grains";
            case 80:
                return "Light rain showers";
            case 81:
                return "Rain showers";
            case 82:
                return "Heavy rain showers";
            case 85:
            case 86:
                return "Snow showers";
            case 95:
                return "Thunderstorm";
            case 96:
            case 99:
                return "Thunderstorm with hail";
            default:
                return "Weather code " + code;
        }
    }

    public static String symbol(@Nullable Integer code, @Nullable Integer isDay) {
        if (code == null) {
            return "•";
        }

        if (code == 0) {
            return isDay != null && isDay == 0 ? "☾" : "☀";
        }
        if (code == 1 || code == 2) {
            return "⛅";
        }
        if (code == 3) {
            return "☁";
        }
        if (code == 45 || code == 48) {
            return "≋";
        }
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
            return "☂";
        }
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return "❄";
        }
        if (code >= 95) {
            return "ϟ";
        }
        return "•";
    }

    public static String windDirection(@Nullable Double degrees) {
        if (degrees == null) {
            return "—";
        }

        String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) Math.round((((degrees % 360) + 360) % 360) / 45.0) % 8;
        return directions[index];
    }

    public static String hourLabel(@Nullable String isoTime) {
        if (isoTime == null) {
            return "—";
        }

        try {
            return LocalDateTime.parse(isoTime).format(HOUR_FORMATTER);
        } catch (DateTimeParseException exception) {
            return isoTime.length() >= 16 ? isoTime.substring(11, 16) : isoTime;
        }
    }

    public static String dayLabel(@Nullable String isoDate, int position) {
        if (position == 0) {
            return "Today";
        }
        if (position == 1) {
            return "Tomorrow";
        }
        if (isoDate == null) {
            return "Day " + (position + 1);
        }

        try {
            return LocalDate.parse(isoDate).format(DAY_FORMATTER);
        } catch (DateTimeParseException exception) {
            return isoDate;
        }
    }

    public static String updatedTime(long updatedAt) {
        if (updatedAt <= 0L) {
            return "—";
        }
        return Instant.ofEpochMilli(updatedAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(UPDATED_FORMATTER);
    }

    public static int findCurrentHourlyIndex(@Nullable WeatherResponse response) {
        if (response == null || response.getCurrent() == null || response.getHourly() == null) {
            return 0;
        }

        String currentTime = response.getCurrent().getTime();
        List<String> times = response.getHourly().getTime();
        if (currentTime == null || times == null || times.isEmpty()) {
            return 0;
        }

        String currentHour = currentTime.length() >= 13
                ? currentTime.substring(0, 13)
                : currentTime;

        for (int index = 0; index < times.size(); index++) {
            String hourlyTime = times.get(index);
            if (hourlyTime != null && hourlyTime.startsWith(currentHour)) {
                return index;
            }
        }

        return 0;
    }

    @Nullable
    public static <T> T valueAt(@Nullable List<T> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }
}
