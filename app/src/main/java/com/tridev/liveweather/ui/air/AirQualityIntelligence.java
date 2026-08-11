package com.tridev.liveweather.ui.air;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.domain.AirQualityReality;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

public final class AirQualityIntelligence {

    private AirQualityIntelligence() {
    }

    @NonNull
    public static String usCategory(@Nullable Double aqi) {
        if (aqi == null) return "Unavailable";
        if (aqi <= 50d) return "Good";
        if (aqi <= 100d) return "Moderate";
        if (aqi <= 150d) return "Unhealthy for sensitive groups";
        if (aqi <= 200d) return "Unhealthy";
        if (aqi <= 300d) return "Very unhealthy";
        return "Hazardous";
    }

    @NonNull
    public static String euCategory(@Nullable Double aqi) {
        if (aqi == null) return "Unavailable";
        if (aqi <= 20d) return "Good";
        if (aqi <= 40d) return "Fair";
        if (aqi <= 60d) return "Moderate";
        if (aqi <= 80d) return "Poor";
        if (aqi <= 100d) return "Very poor";
        return "Extremely poor";
    }

    @NonNull
    public static String dominantPollutant(@Nullable AirQualityResponse.CurrentAirQuality current) {
        if (current == null) return "—";
        String name = "PM2.5";
        double max = value(current.getUsAqiPm25());
        if (value(current.getUsAqiPm10()) > max) { max = value(current.getUsAqiPm10()); name = "PM10"; }
        if (value(current.getUsAqiOzone()) > max) { max = value(current.getUsAqiOzone()); name = "O₃"; }
        if (value(current.getUsAqiNitrogenDioxide()) > max) { max = value(current.getUsAqiNitrogenDioxide()); name = "NO₂"; }
        if (value(current.getUsAqiSulphurDioxide()) > max) { max = value(current.getUsAqiSulphurDioxide()); name = "SO₂"; }
        if (value(current.getUsAqiCarbonMonoxide()) > max) { name = "CO"; }
        return name;
    }

    public static double hazeIntensity(@Nullable AirQualityResponse response) {
        return AirQualityReality.hazeIntensity(response);
    }

    @NonNull
    public static String currentSummary(@Nullable AirQualityResponse response) {
        if (response == null || response.getCurrent() == null) {
            return "Air-quality intelligence will appear after sync.";
        }
        AirQualityResponse.CurrentAirQuality c = response.getCurrent();
        return String.format(
                Locale.getDefault(),
                "US AQI %s · %s · dominant %s · PM2.5 %.1f µg/m³ · PM10 %.1f µg/m³",
                whole(c.getUsAqi()),
                usCategory(c.getUsAqi()),
                dominantPollutant(c),
                value(c.getPm25()),
                value(c.getPm10())
        );
    }

    @NonNull
    public static String pollutantLine(@Nullable AirQualityResponse.CurrentAirQuality c) {
        if (c == null) return "—";
        return String.format(
                Locale.getDefault(),
                "O₃ %.0f · NO₂ %.0f · SO₂ %.0f · CO %.0f µg/m³",
                value(c.getOzone()),
                value(c.getNitrogenDioxide()),
                value(c.getSulphurDioxide()),
                value(c.getCarbonMonoxide())
        );
    }

    @NonNull
    public static String hazeLine(@Nullable AirQualityResponse.CurrentAirQuality c) {
        if (c == null) return "—";
        return String.format(
                Locale.getDefault(),
                "Aerosol optical depth %.2f · Dust %.1f µg/m³",
                value(c.getAerosolOpticalDepth()),
                value(c.getDust())
        );
    }

    @NonNull
    public static String uvLine(@Nullable AirQualityResponse.CurrentAirQuality c) {
        if (c == null) return "—";
        double actual = value(c.getUvIndex());
        double clear = value(c.getUvIndexClearSky());
        return String.format(
                Locale.getDefault(),
                "UV %.1f · clear-sky %.1f · atmosphere reduction %.0f%%",
                actual,
                clear,
                clear <= 0d ? 0d : clamp((1d - actual / clear) * 100d, 0d, 100d)
        );
    }

    @NonNull
    public static String next24Hours(@Nullable AirQualityResponse response) {
        if (response == null || response.getHourly() == null
                || response.getHourly().getTime() == null
                || response.getHourly().getUsAqi() == null) {
            return "24-hour AQI trend unavailable.";
        }
        AirQualityResponse.HourlyAirQuality hourly = response.getHourly();
        int start = findCurrentIndex(response);
        int end = Math.min(start + 24, hourly.getUsAqi().size());
        double max = -1d;
        double min = Double.MAX_VALUE;
        int maxIndex = start;
        for (int i = start; i < end; i++) {
            Double value = WeatherFormatter.valueAt(hourly.getUsAqi(), i);
            if (value == null) continue;
            if (value > max) { max = value; maxIndex = i; }
            min = Math.min(min, value);
        }
        if (max < 0d || min == Double.MAX_VALUE) return "24-hour AQI trend unavailable.";
        String time = WeatherFormatter.hourLabel(WeatherFormatter.valueAt(hourly.getTime(), maxIndex));
        return String.format(
                Locale.getDefault(),
                "Next 24h US AQI %.0f–%.0f · highest near %s (%s)",
                min,
                max,
                time,
                usCategory(max)
        );
    }

    public static int findCurrentIndex(@Nullable AirQualityResponse response) {
        if (response == null || response.getCurrent() == null || response.getHourly() == null
                || response.getCurrent().getTime() == null || response.getHourly().getTime() == null) {
            return 0;
        }
        String currentText = response.getCurrent().getTime();
        try {
            LocalDateTime current = LocalDateTime.parse(currentText);
            long best = Long.MAX_VALUE;
            int bestIndex = 0;
            List<String> times = response.getHourly().getTime();
            for (int i = 0; i < times.size(); i++) {
                String text = times.get(i);
                if (text == null) continue;
                LocalDateTime t = LocalDateTime.parse(text);
                long distance = Math.abs(java.time.Duration.between(current, t).toMinutes());
                if (distance < best) { best = distance; bestIndex = i; }
            }
            return bestIndex;
        } catch (DateTimeParseException ignored) {
            return 0;
        }
    }

    @NonNull
    private static String whole(@Nullable Double value) {
        return value == null ? "—" : Long.toString(Math.round(value));
    }

    private static double value(@Nullable Double value) {
        return value == null ? 0d : Math.max(0d, value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
