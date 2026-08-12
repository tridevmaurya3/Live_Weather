package com.tridev.liveweather.ui.weather;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Converts raw weather values into compact, useful dashboard interpretations.
 *
 * Intelligence thresholds stay on provider metric values. Phase 16 converts
 * only the displayed units through WeatherFormatter. Phase 18 routes the main
 * insight through WeatherIntelligence2 so precipitation-now wording is shared
 * with advanced details and Smart Risk behavior.
 */
public final class DashboardIntelligence {

    private static final DateTimeFormatter CLOCK_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault());

    private DashboardIntelligence() {
    }

    @NonNull
    public static String insight(@Nullable WeatherResponse response) {
        return WeatherIntelligence2.analyze(response).getHeadline();
    }

    @NonNull
    public static String uv(@Nullable Double value) {
        if (value == null) {
            return "—";
        }
        String level;
        if (value < 3d) {
            level = "Low";
        } else if (value < 6d) {
            level = "Moderate";
        } else if (value < 8d) {
            level = "High";
        } else if (value < 11d) {
            level = "Very high";
        } else {
            level = "Extreme";
        }
        return String.format(Locale.getDefault(), "%.1f · %s", value, level);
    }

    @NonNull
    public static String visibility(@Nullable Double meters) {
        if (meters == null) {
            return "—";
        }
        double kilometres = meters / 1000d;
        String level;
        if (kilometres < 1d) {
            level = "Very low";
        } else if (kilometres < 5d) {
            level = "Limited";
        } else if (kilometres < 10d) {
            level = "Moderate";
        } else {
            level = "Good";
        }
        return WeatherFormatter.visibilityDistance(meters) + " · " + level;
    }

    @NonNull
    public static String pressure(@Nullable WeatherResponse response) {
        if (response == null || response.getCurrent() == null
                || response.getCurrent().getPressureMsl() == null) {
            return "—";
        }

        double currentPressure = response.getCurrent().getPressureMsl();
        String trend = pressureTrend(response, currentPressure);
        return WeatherFormatter.pressure(currentPressure) + " · " + trend;
    }

    @NonNull
    private static String pressureTrend(
            @NonNull WeatherResponse response,
            double currentPressure
    ) {
        WeatherResponse.HourlyWeather hourly = response.getHourly();
        if (hourly == null || hourly.getPressureMsl() == null) {
            return "steady";
        }

        int currentIndex = WeatherFormatter.findCurrentHourlyIndex(response);
        List<Double> pressures = hourly.getPressureMsl();
        int futureIndex = Math.min(currentIndex + 3, pressures.size() - 1);
        Double future = WeatherFormatter.valueAt(pressures, futureIndex);
        if (future == null) {
            return "steady";
        }

        double difference = future - currentPressure;
        if (difference >= 1.5d) {
            return "rising";
        }
        if (difference <= -1.5d) {
            return "falling";
        }
        return "steady";
    }

    @NonNull
    public static String dewPoint(@Nullable Double value) {
        if (value == null) {
            return "—";
        }
        String feel;
        if (value < 10d) {
            feel = "Dry";
        } else if (value < 16d) {
            feel = "Comfortable";
        } else if (value < 20d) {
            feel = "Humid";
        } else {
            feel = "Very humid";
        }
        return WeatherFormatter.temperature(value) + " · " + feel;
    }

    @NonNull
    public static String clouds(@Nullable Double value) {
        if (value == null) {
            return "—";
        }
        String level;
        if (value < 20d) {
            level = "Mostly clear";
        } else if (value < 50d) {
            level = "Partly cloudy";
        } else if (value < 80d) {
            level = "Cloudy";
        } else {
            level = "Overcast";
        }
        return Math.round(value) + "% · " + level;
    }

    @NonNull
    public static String gusts(@Nullable Double value) {
        if (value == null) {
            return "—";
        }
        String level;
        if (value < 20d) {
            level = "Light";
        } else if (value < 40d) {
            level = "Breezy";
        } else if (value < 60d) {
            level = "Strong";
        } else {
            level = "Very strong";
        }
        return WeatherFormatter.wind(value) + " · " + level;
    }

    @NonNull
    public static String rainChance(@Nullable WeatherResponse.DailyWeather daily) {
        Double value = daily == null
                ? null
                : WeatherFormatter.valueAt(daily.getPrecipitationProbabilityMax(), 0);
        if (value == null) {
            return "—";
        }
        String level;
        if (value < 20d) {
            level = "Low";
        } else if (value < 50d) {
            level = "Possible";
        } else if (value < 70d) {
            level = "Likely";
        } else {
            level = "High";
        }
        return Math.round(value) + "% · " + level;
    }

    @NonNull
    public static String sunriseSunset(@Nullable WeatherResponse.DailyWeather daily) {
        if (daily == null) {
            return "—";
        }
        String sunrise = formatClock(WeatherFormatter.valueAt(daily.getSunrise(), 0));
        String sunset = formatClock(WeatherFormatter.valueAt(daily.getSunset(), 0));
        return "↑ " + sunrise + "  ·  ↓ " + sunset;
    }

    @NonNull
    public static String daylight(@Nullable WeatherResponse.DailyWeather daily) {
        Double duration = daily == null
                ? null
                : WeatherFormatter.valueAt(daily.getDaylightDuration(), 0);
        if (duration == null) {
            return "—";
        }
        long totalMinutes = Math.round(duration / 60d);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return hours + "h " + minutes + "m daylight";
    }

    @NonNull
    private static String formatClock(@Nullable String isoDateTime) {
        if (isoDateTime == null || isoDateTime.trim().isEmpty()) {
            return "—";
        }
        try {
            return LocalDateTime.parse(isoDateTime).format(CLOCK_FORMATTER);
        } catch (DateTimeParseException exception) {
            return isoDateTime.length() >= 16
                    ? isoDateTime.substring(11, 16)
                    : isoDateTime;
        }
    }

    public enum HeroMode {
        CLEAR_DAY,
        CLEAR_NIGHT,
        CLOUDY,
        RAIN,
        STORM,
        SNOW,
        FOG
    }

    @NonNull
    public static HeroMode heroMode(@Nullable WeatherResponse response) {
        if (response == null || response.getCurrent() == null) {
            return HeroMode.CLOUDY;
        }
        LiveConditionResolver.ResolvedCondition resolved = LiveConditionResolver.resolve(response);
        return heroMode(resolved.getWeatherCode(), resolved.getIsDay());
    }

    @NonNull
    public static HeroMode heroMode(@Nullable WeatherResponse.CurrentWeather current) {
        if (current == null) {
            return HeroMode.CLOUDY;
        }
        return heroMode(current.getWeatherCode(), current.getIsDay());
    }

    @NonNull
    public static HeroMode heroMode(@Nullable Integer code, @Nullable Integer isDay) {
        if (code == null) {
            return HeroMode.CLOUDY;
        }
        if (code >= 95) {
            return HeroMode.STORM;
        }
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return HeroMode.SNOW;
        }
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
            return HeroMode.RAIN;
        }
        if (code == 45 || code == 48) {
            return HeroMode.FOG;
        }
        if (code == 0 || code == 1) {
            return isDay != null && isDay == 0
                    ? HeroMode.CLEAR_NIGHT
                    : HeroMode.CLEAR_DAY;
        }
        return HeroMode.CLOUDY;
    }
}
