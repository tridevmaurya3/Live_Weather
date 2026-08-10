package com.tridev.liveweather.ui.forecast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.util.List;
import java.util.Locale;

/**
 * Converts raw hourly/daily arrays into concise forecast decisions and summaries.
 */
public final class ForecastIntelligence {

    private ForecastIntelligence() {
    }

    @NonNull
    public static String next24Hours(@NonNull WeatherResponse response) {
        WeatherResponse.HourlyWeather hourly = response.getHourly();
        if (hourly == null || hourly.getTime() == null || hourly.getTime().isEmpty()) {
            return "Waiting for the next 24 hours of forecast data.";
        }

        int start = WeatherFormatter.findCurrentHourlyIndex(response);
        int end = Math.min(hourly.getTime().size(), start + 24);

        Double minTemp = null;
        Double maxTemp = null;
        Double maxRainChance = null;
        String maxRainTime = null;
        Double maxWind = null;

        int rainStart = -1;
        int rainEnd = -1;

        for (int index = start; index < end; index++) {
            Double temp = WeatherFormatter.valueAt(hourly.getTemperature2m(), index);
            if (temp != null) {
                minTemp = minTemp == null ? temp : Math.min(minTemp, temp);
                maxTemp = maxTemp == null ? temp : Math.max(maxTemp, temp);
            }

            Double chance = WeatherFormatter.valueAt(hourly.getPrecipitationProbability(), index);
            Double precipitation = WeatherFormatter.valueAt(hourly.getPrecipitation(), index);
            boolean wetHour = (chance != null && chance >= 40.0)
                    || (precipitation != null && precipitation >= 0.1);

            if (wetHour) {
                if (rainStart < 0) {
                    rainStart = index;
                }
                rainEnd = index;
            }

            if (chance != null && (maxRainChance == null || chance > maxRainChance)) {
                maxRainChance = chance;
                maxRainTime = WeatherFormatter.hourLabel(
                        WeatherFormatter.valueAt(hourly.getTime(), index)
                );
            }

            Double wind = WeatherFormatter.valueAt(hourly.getWindSpeed10m(), index);
            if (wind != null) {
                maxWind = maxWind == null ? wind : Math.max(maxWind, wind);
            }
        }

        String temperaturePart = minTemp == null || maxTemp == null
                ? "temperature range unavailable"
                : WeatherFormatter.temperature(minTemp) + " to " + WeatherFormatter.temperature(maxTemp);

        String rainPart;
        if (rainStart >= 0) {
            String startLabel = WeatherFormatter.hourLabel(
                    WeatherFormatter.valueAt(hourly.getTime(), rainStart)
            );
            String endLabel = WeatherFormatter.hourLabel(
                    WeatherFormatter.valueAt(hourly.getTime(), rainEnd)
            );
            rainPart = rainStart == rainEnd
                    ? "rain signal near " + startLabel
                    : "rain window " + startLabel + "–" + endLabel;
            if (maxRainChance != null && maxRainTime != null) {
                rainPart += " · peak " + Math.round(maxRainChance) + "% at " + maxRainTime;
            }
        } else {
            rainPart = "no strong rain window detected";
        }

        String windPart = maxWind == null
                ? "wind peak unavailable"
                : "wind up to " + WeatherFormatter.wind(maxWind);

        return "Next 24h · " + temperaturePart + " · " + rainPart + " · " + windPart + ".";
    }

    @NonNull
    public static String tenDayOverview(@NonNull WeatherResponse response) {
        WeatherResponse.DailyWeather daily = response.getDaily();
        if (daily == null || daily.getTime() == null || daily.getTime().isEmpty()) {
            return "Waiting for the 10-day outlook.";
        }

        int count = Math.min(10, daily.getTime().size());
        int wetDays = 0;
        Double warmest = null;
        int warmestIndex = -1;
        Double strongestRainChance = null;
        int strongestRainIndex = -1;

        for (int index = 0; index < count; index++) {
            Double rainChance = WeatherFormatter.valueAt(daily.getPrecipitationProbabilityMax(), index);
            Double rainSum = WeatherFormatter.valueAt(daily.getPrecipitationSum(), index);
            if ((rainChance != null && rainChance >= 40.0) || (rainSum != null && rainSum >= 0.5)) {
                wetDays++;
            }
            if (rainChance != null
                    && (strongestRainChance == null || rainChance > strongestRainChance)) {
                strongestRainChance = rainChance;
                strongestRainIndex = index;
            }

            Double high = WeatherFormatter.valueAt(daily.getTemperature2mMax(), index);
            if (high != null && (warmest == null || high > warmest)) {
                warmest = high;
                warmestIndex = index;
            }
        }

        StringBuilder summary = new StringBuilder();
        summary.append(count).append(" days · ")
                .append(wetDays).append(wetDays == 1 ? " wetter day" : " wetter days");

        if (warmest != null && warmestIndex >= 0) {
            summary.append(" · warmest ")
                    .append(WeatherFormatter.dayLabel(
                            WeatherFormatter.valueAt(daily.getTime(), warmestIndex),
                            warmestIndex
                    ))
                    .append(" ")
                    .append(WeatherFormatter.temperature(warmest));
        }

        if (strongestRainChance != null && strongestRainIndex >= 0) {
            summary.append(" · highest rain chance ")
                    .append(Math.round(strongestRainChance))
                    .append("% on ")
                    .append(WeatherFormatter.dayLabel(
                            WeatherFormatter.valueAt(daily.getTime(), strongestRainIndex),
                            strongestRainIndex
                    ));
        }
        summary.append(".");
        return summary.toString();
    }

    @NonNull
    public static String dailyDetails(
            @NonNull WeatherResponse.DailyWeather daily,
            int index
    ) {
        Double feelsHigh = WeatherFormatter.valueAt(daily.getApparentTemperatureMax(), index);
        Double feelsLow = WeatherFormatter.valueAt(daily.getApparentTemperatureMin(), index);
        Double precipitation = WeatherFormatter.valueAt(daily.getPrecipitationSum(), index);
        Double precipitationHours = WeatherFormatter.valueAt(daily.getPrecipitationHours(), index);
        Double sunshineSeconds = WeatherFormatter.valueAt(daily.getSunshineDuration(), index);
        Double daylightSeconds = WeatherFormatter.valueAt(daily.getDaylightDuration(), index);
        Double uv = WeatherFormatter.valueAt(daily.getUvIndexMax(), index);
        Double gust = WeatherFormatter.valueAt(daily.getWindGusts10mMax(), index);
        Double direction = WeatherFormatter.valueAt(daily.getWindDirection10mDominant(), index);
        String sunrise = WeatherFormatter.valueAt(daily.getSunrise(), index);
        String sunset = WeatherFormatter.valueAt(daily.getSunset(), index);

        return String.format(
                Locale.getDefault(),
                "Feels H %s · L %s\nPrecip %s · Wet hours %s\nSunrise %s · Sunset %s\nSunshine %s · Daylight %s\nUV %s · Gusts %s %s",
                WeatherFormatter.temperature(feelsHigh),
                WeatherFormatter.temperature(feelsLow),
                WeatherFormatter.precipitation(precipitation),
                formatHours(precipitationHours),
                shortTime(sunrise),
                shortTime(sunset),
                formatSecondsAsHours(sunshineSeconds),
                formatSecondsAsHours(daylightSeconds),
                formatOneDecimal(uv),
                WeatherFormatter.wind(gust),
                WeatherFormatter.windDirection(direction)
        );
    }

    @NonNull
    public static String formatSecondsAsHours(@Nullable Double seconds) {
        if (seconds == null) {
            return "—";
        }
        return String.format(Locale.getDefault(), "%.1f h", Math.max(0.0, seconds) / 3600.0);
    }

    private static String formatHours(@Nullable Double hours) {
        if (hours == null) {
            return "—";
        }
        return String.format(Locale.getDefault(), "%.1f h", Math.max(0.0, hours));
    }

    private static String formatOneDecimal(@Nullable Double value) {
        return value == null ? "—" : String.format(Locale.getDefault(), "%.1f", value);
    }

    private static String shortTime(@Nullable String isoTime) {
        if (isoTime == null || isoTime.trim().isEmpty()) {
            return "—";
        }
        int separator = isoTime.indexOf('T');
        if (separator >= 0 && isoTime.length() >= separator + 6) {
            return isoTime.substring(separator + 1, separator + 6);
        }
        return isoTime;
    }
}
