package com.tridev.liveweather.ui.forecast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.ui.weather.WeatherFormatter;
import com.tridev.liveweather.ui.weather.WeatherIntelligence2;

import java.util.Locale;

/**
 * Converts raw hourly/daily arrays into concise forecast decisions and rich
 * Phase 19 detail summaries.
 *
 * Forecast probability remains explicitly separate from current-condition
 * evidence supplied by WeatherIntelligence2.
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
        Double maxGust = null;
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
            boolean forecastWetHour = (chance != null && chance >= 40.0)
                    || (precipitation != null && precipitation >= 0.1);

            if (forecastWetHour) {
                if (rainStart < 0) rainStart = index;
                rainEnd = index;
            }

            if (chance != null && (maxRainChance == null || chance > maxRainChance)) {
                maxRainChance = chance;
                maxRainTime = WeatherFormatter.hourLabel(
                        WeatherFormatter.valueAt(hourly.getTime(), index)
                );
            }

            Double wind = WeatherFormatter.valueAt(hourly.getWindSpeed10m(), index);
            if (wind != null) maxWind = maxWind == null ? wind : Math.max(maxWind, wind);

            Double gust = WeatherFormatter.valueAt(hourly.getWindGusts10m(), index);
            if (gust != null) maxGust = maxGust == null ? gust : Math.max(maxGust, gust);
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
                    ? "forecast rain-risk near " + startLabel
                    : "forecast rain-risk window " + startLabel + "–" + endLabel;
            if (maxRainChance != null && maxRainTime != null) {
                rainPart += " · peak probability " + Math.round(maxRainChance) + "% at " + maxRainTime;
            }
        } else {
            rainPart = "no strong forecast rain-risk window detected";
        }

        String windPart;
        if (maxWind == null && maxGust == null) {
            windPart = "wind peak unavailable";
        } else {
            windPart = "wind up to " + WeatherFormatter.wind(maxWind)
                    + " · gusts " + WeatherFormatter.wind(maxGust);
        }

        WeatherIntelligence2.Report intelligence = WeatherIntelligence2.analyze(response);
        return intelligence.getPrecipitationSummary()
                + " Next 24h · " + temperaturePart + " · " + rainPart + " · " + windPart + ".";
    }

    /** Rich details for one selected hourly forecast point. */
    @NonNull
    public static String hourlyDetails(
            @NonNull WeatherResponse.HourlyWeather hourly,
            int index,
            boolean isNow
    ) {
        String time = isNow
                ? "Now"
                : WeatherFormatter.hourLabel(WeatherFormatter.valueAt(hourly.getTime(), index));
        Integer code = WeatherFormatter.valueAt(hourly.getWeatherCode(), index);
        Integer isDay = WeatherFormatter.valueAt(hourly.getIsDay(), index);
        Double temperature = WeatherFormatter.valueAt(hourly.getTemperature2m(), index);
        Double apparent = WeatherFormatter.valueAt(hourly.getApparentTemperature(), index);
        Double humidity = WeatherFormatter.valueAt(hourly.getRelativeHumidity2m(), index);
        Double dewPoint = WeatherFormatter.valueAt(hourly.getDewPoint2m(), index);
        Double probability = WeatherFormatter.valueAt(hourly.getPrecipitationProbability(), index);
        Double precipitation = WeatherFormatter.valueAt(hourly.getPrecipitation(), index);
        Double rain = WeatherFormatter.valueAt(hourly.getRain(), index);
        Double showers = WeatherFormatter.valueAt(hourly.getShowers(), index);
        Double snowfall = WeatherFormatter.valueAt(hourly.getSnowfall(), index);
        Double cloudCover = WeatherFormatter.valueAt(hourly.getCloudCover(), index);
        Double visibility = WeatherFormatter.valueAt(hourly.getVisibility(), index);
        Double pressure = WeatherFormatter.valueAt(hourly.getPressureMsl(), index);
        Double wind = WeatherFormatter.valueAt(hourly.getWindSpeed10m(), index);
        Double direction = WeatherFormatter.valueAt(hourly.getWindDirection10m(), index);
        Double gust = WeatherFormatter.valueAt(hourly.getWindGusts10m(), index);

        return String.format(
                Locale.getDefault(),
                "%s · %s %s\nTemp %s · Feels %s · Humidity %s · Dew %s\nForecast precip probability %s · Model total %s · Rain %s · Showers %s · Snow %.2f cm\nWind %s %s · Gusts %s · Clouds %s\nVisibility %s · Pressure %s",
                time,
                WeatherFormatter.symbol(code, isDay),
                WeatherFormatter.condition(code),
                WeatherFormatter.temperature(temperature),
                WeatherFormatter.temperature(apparent),
                WeatherFormatter.percent(humidity),
                WeatherFormatter.temperature(dewPoint),
                WeatherFormatter.percent(probability),
                WeatherFormatter.precipitation(precipitation),
                WeatherFormatter.precipitation(rain),
                WeatherFormatter.precipitation(showers),
                snowfall == null ? 0d : snowfall,
                WeatherFormatter.wind(wind),
                WeatherFormatter.windDirection(direction),
                WeatherFormatter.wind(gust),
                WeatherFormatter.percent(cloudCover),
                WeatherFormatter.visibilityDistance(visibility),
                WeatherFormatter.pressure(pressure)
        );
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
            summary.append(" · highest precipitation probability ")
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
        Double high = WeatherFormatter.valueAt(daily.getTemperature2mMax(), index);
        Double low = WeatherFormatter.valueAt(daily.getTemperature2mMin(), index);
        Double feelsHigh = WeatherFormatter.valueAt(daily.getApparentTemperatureMax(), index);
        Double feelsLow = WeatherFormatter.valueAt(daily.getApparentTemperatureMin(), index);
        Double probability = WeatherFormatter.valueAt(daily.getPrecipitationProbabilityMax(), index);
        Double precipitation = WeatherFormatter.valueAt(daily.getPrecipitationSum(), index);
        Double rain = WeatherFormatter.valueAt(daily.getRainSum(), index);
        Double showers = WeatherFormatter.valueAt(daily.getShowersSum(), index);
        Double snowfall = WeatherFormatter.valueAt(daily.getSnowfallSum(), index);
        Double precipitationHours = WeatherFormatter.valueAt(daily.getPrecipitationHours(), index);
        Double sunshineSeconds = WeatherFormatter.valueAt(daily.getSunshineDuration(), index);
        Double daylightSeconds = WeatherFormatter.valueAt(daily.getDaylightDuration(), index);
        Double uv = WeatherFormatter.valueAt(daily.getUvIndexMax(), index);
        Double wind = WeatherFormatter.valueAt(daily.getWindSpeed10mMax(), index);
        Double gust = WeatherFormatter.valueAt(daily.getWindGusts10mMax(), index);
        Double direction = WeatherFormatter.valueAt(daily.getWindDirection10mDominant(), index);
        String sunrise = WeatherFormatter.valueAt(daily.getSunrise(), index);
        String sunset = WeatherFormatter.valueAt(daily.getSunset(), index);

        return String.format(
                Locale.getDefault(),
                "H %s · L %s · Feels H %s · L %s\nForecast precipitation probability %s · Total %s · Rain %s · Showers %s · Snow %.2f cm · Wet hours %s\nWind max %s %s · Gusts %s\nUV %s · Sunrise %s · Sunset %s\nSunshine %s · Daylight %s",
                WeatherFormatter.temperature(high),
                WeatherFormatter.temperature(low),
                WeatherFormatter.temperature(feelsHigh),
                WeatherFormatter.temperature(feelsLow),
                WeatherFormatter.percent(probability),
                WeatherFormatter.precipitation(precipitation),
                WeatherFormatter.precipitation(rain),
                WeatherFormatter.precipitation(showers),
                snowfall == null ? 0d : snowfall,
                formatHours(precipitationHours),
                WeatherFormatter.wind(wind),
                WeatherFormatter.windDirection(direction),
                WeatherFormatter.wind(gust),
                formatOneDecimal(uv),
                shortTime(sunrise),
                shortTime(sunset),
                formatSecondsAsHours(sunshineSeconds),
                formatSecondsAsHours(daylightSeconds)
        );
    }

    @NonNull
    public static String formatSecondsAsHours(@Nullable Double seconds) {
        if (seconds == null) return "—";
        return String.format(Locale.getDefault(), "%.1f h", Math.max(0.0, seconds) / 3600.0);
    }

    @NonNull
    public static String shortTime(@Nullable String isoTime) {
        if (isoTime == null || isoTime.trim().isEmpty()) return "—";
        int separator = isoTime.indexOf('T');
        if (separator >= 0 && isoTime.length() >= separator + 6) {
            return isoTime.substring(separator + 1, separator + 6);
        }
        return isoTime;
    }

    private static String formatHours(@Nullable Double hours) {
        if (hours == null) return "—";
        return String.format(Locale.getDefault(), "%.1f h", Math.max(0.0, hours));
    }

    private static String formatOneDecimal(@Nullable Double value) {
        return value == null ? "—" : String.format(Locale.getDefault(), "%.1f", value);
    }
}
