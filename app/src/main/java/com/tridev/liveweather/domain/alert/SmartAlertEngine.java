package com.tridev.liveweather.domain.alert;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;
import com.tridev.liveweather.ui.weather.WeatherFormatter;
import com.tridev.liveweather.ui.weather.WeatherIntelligence2;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SmartAlertEngine {

    private SmartAlertEngine() {
    }

    @NonNull
    public static List<WeatherAlert> build(@Nullable WeatherResponse weather) {
        List<WeatherAlert> alerts = new ArrayList<>();
        if (weather == null) return alerts;

        WeatherResponse.CurrentWeather current = weather.getCurrent();
        WeatherResponse.DailyWeather daily = weather.getDaily();
        long now = System.currentTimeMillis();
        WeatherIntelligence2.Report intelligence = WeatherIntelligence2.analyze(weather);
        LiveConditionResolver.ResolvedCondition condition = intelligence.getResolvedCondition();
        int code = condition.getWeatherCode() == null ? 0 : condition.getWeatherCode();

        if (code >= 95 && intelligence.isPrecipitationNowConfirmed()) {
            WeatherAlert.Severity severity = code >= 97
                    ? WeatherAlert.Severity.RED
                    : WeatherAlert.Severity.ORANGE;
            add(alerts, "smart-thunderstorm", "Thunderstorm risk now",
                    "The current weather model classifies thunderstorm conditions now. Check official local warnings and Radar for local context.",
                    severity, "Right now", now, now + 3 * 60 * 60 * 1000L);
        }

        double precip = condition.getPrecipitationSignalMm();
        if (intelligence.isPrecipitationNowConfirmed()
                && isRainOrShowerCode(code)
                && precip >= 1.5d) {
            add(alerts, "smart-heavy-rain-now", "Heavy rain signal now",
                    "Current/corroborated precipitation signal is "
                            + WeatherFormatter.precipitation(precip)
                            + " in the active model interval.",
                    precip >= 3.0d ? WeatherAlert.Severity.ORANGE : WeatherAlert.Severity.YELLOW,
                    "Current conditions", now, now + 2 * 60 * 60 * 1000L);
        }

        if (current != null) {
            double gust = value(current.getWindGusts10m());
            if (gust >= 60d) {
                add(alerts, "smart-wind", "Strong wind risk",
                        "Wind gusts are around " + WeatherFormatter.wind(gust) + ".",
                        gust >= 90d ? WeatherAlert.Severity.RED : WeatherAlert.Severity.ORANGE,
                        "Current conditions", now, now + 3 * 60 * 60 * 1000L);
            }

            double visibility = value(current.getVisibility());
            if (visibility > 0d && visibility <= 1000d) {
                add(alerts, "smart-visibility", "Very low visibility",
                        "Visibility is about " + WeatherFormatter.visibilityDistance(visibility)
                                + ". The model does not by itself prove the exact cause of the reduction.",
                        visibility <= 500d ? WeatherAlert.Severity.ORANGE : WeatherAlert.Severity.YELLOW,
                        "Current conditions", now, now + 3 * 60 * 60 * 1000L);
            }
        }

        if (daily != null) {
            Double rainProbability = at(daily.getPrecipitationProbabilityMax(), 0);
            Double rainSum = at(daily.getPrecipitationSum(), 0);
            if (value(rainProbability) >= 80d && value(rainSum) >= 25d) {
                String nowQualifier = intelligence.isPrecipitationNowConfirmed()
                        && isRainOrShowerCode(code)
                        ? " Current rain/showers are also indicated now."
                        : " This is a forecast risk for today, not a claim that rain is falling now.";
                add(alerts, "smart-rain-today", "Heavy rain potential today",
                        String.format(
                                Locale.getDefault(),
                                "Forecast precipitation %s with %.0f%% peak probability.%s",
                                WeatherFormatter.precipitation(value(rainSum)),
                                value(rainProbability),
                                nowQualifier
                        ),
                        value(rainSum) >= 50d ? WeatherAlert.Severity.ORANGE : WeatherAlert.Severity.YELLOW,
                        "Today", now, endOfRiskWindow(now, 24));
            }

            Double maxGust = at(daily.getWindGusts10mMax(), 0);
            if (value(maxGust) >= 70d) {
                add(alerts, "smart-gust-today", "Strong gust potential today",
                        "Forecast peak gusts may reach " + WeatherFormatter.wind(value(maxGust)) + ".",
                        value(maxGust) >= 90d ? WeatherAlert.Severity.ORANGE : WeatherAlert.Severity.YELLOW,
                        "Today", now, endOfRiskWindow(now, 24));
            }

            Double uv = at(daily.getUvIndexMax(), 0);
            if (value(uv) >= 8d) {
                add(alerts, "smart-uv", "Very high UV today",
                        String.format(Locale.getDefault(), "Forecast UV index may reach %.1f.", value(uv)),
                        value(uv) >= 11d ? WeatherAlert.Severity.ORANGE : WeatherAlert.Severity.YELLOW,
                        "Today", now, endOfRiskWindow(now, 24));
            }

            Double maxTemp = at(daily.getTemperature2mMax(), 0);
            if (value(maxTemp) >= 40d) {
                add(alerts, "smart-heat", "High heat risk today",
                        "Forecast maximum temperature is around "
                                + WeatherFormatter.temperature(value(maxTemp)) + ".",
                        value(maxTemp) >= 45d ? WeatherAlert.Severity.ORANGE : WeatherAlert.Severity.YELLOW,
                        "Today", now, endOfRiskWindow(now, 24));
            }
        }

        return alerts;
    }

    private static boolean isRainOrShowerCode(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82);
    }

    private static void add(
            List<WeatherAlert> alerts,
            String id,
            String title,
            String message,
            WeatherAlert.Severity severity,
            String valid,
            long issuedAt,
            long expiresAt
    ) {
        alerts.add(new WeatherAlert(
                id, title, message, null, valid, severity,
                WeatherAlert.Source.SMART_FORECAST, issuedAt, expiresAt
        ));
    }

    private static long endOfRiskWindow(long now, int hours) {
        return now + hours * 60L * 60L * 1000L;
    }

    private static double value(@Nullable Double value) {
        return value == null ? 0d : value;
    }

    @Nullable
    private static Double at(@Nullable List<Double> values, int index) {
        return values == null || index < 0 || index >= values.size() ? null : values.get(index);
    }
}
