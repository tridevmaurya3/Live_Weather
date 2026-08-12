package com.tridev.liveweather.ui.weather;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.LiveConditionResolver;

import java.util.Locale;

/**
 * Phase 18 Weather Intelligence 2.0.
 *
 * CURRENT evidence and FORECAST probability are intentionally separate. A
 * probability value never becomes a claim that precipitation is physically
 * falling at the user's exact location right now.
 */
public final class WeatherIntelligence2 {

    private static final double LIKELY_RAIN_PROBABILITY = 60d;
    private static final double POSSIBLE_RAIN_PROBABILITY = 35d;
    private static final double MEANINGFUL_HOURLY_PRECIP_MM = 0.20d;

    private WeatherIntelligence2() {
    }

    public enum PrecipitationState {
        THUNDERSTORM_NOW,
        PRECIPITATION_NOW_CONFIRMED,
        WEAK_SIGNAL_UNCONFIRMED,
        RAIN_LIKELY_SOON,
        RAIN_POSSIBLE_SOON,
        RAIN_LIKELY_LATER,
        RAIN_POSSIBLE_LATER,
        DRY_NOW,
        UNKNOWN
    }

    public enum ConfidenceLevel {
        HIGHER,
        STANDARD,
        LIMITED
    }

    @NonNull
    public static Report analyze(@Nullable WeatherResponse response) {
        if (response == null || response.getCurrent() == null) {
            return Report.unavailable();
        }

        WeatherResponse.CurrentWeather current = response.getCurrent();
        LiveConditionResolver.ResolvedCondition resolved = LiveConditionResolver.resolve(response);
        ForecastPrecipitation forecast = findForecastRain(response);

        boolean thunderstormNow = resolved.getWeatherCode() != null
                && resolved.getWeatherCode() >= 95;
        boolean precipitationNow = isAnyPrecipitationCode(resolved.getWeatherCode())
                && (resolved.getPrecipitationSignalMm() > 0d || isCurrentPrecipitationCode(resolved));
        boolean weakSignal = resolved.getSource().toLowerCase(Locale.US)
                .contains("weak precipitation signal");

        PrecipitationState precipitationState;
        if (thunderstormNow) {
            precipitationState = PrecipitationState.THUNDERSTORM_NOW;
        } else if (precipitationNow) {
            precipitationState = PrecipitationState.PRECIPITATION_NOW_CONFIRMED;
        } else if (weakSignal) {
            precipitationState = PrecipitationState.WEAK_SIGNAL_UNCONFIRMED;
        } else if (forecast.likely && forecast.hoursFromNow <= 2) {
            precipitationState = PrecipitationState.RAIN_LIKELY_SOON;
        } else if (forecast.possible && forecast.hoursFromNow <= 2) {
            precipitationState = PrecipitationState.RAIN_POSSIBLE_SOON;
        } else if (forecast.likely) {
            precipitationState = PrecipitationState.RAIN_LIKELY_LATER;
        } else if (forecast.possible) {
            precipitationState = PrecipitationState.RAIN_POSSIBLE_LATER;
        } else {
            precipitationState = PrecipitationState.DRY_NOW;
        }

        ConfidenceLevel confidence = resolveConfidence(resolved, response);
        String precipitationSummary = precipitationSummary(
                precipitationState,
                resolved,
                forecast,
                current
        );
        String comfortSummary = comfortSummary(current);
        String windSummary = windSummary(current);
        String visibilitySummary = visibilitySummary(current, resolved.getWeatherCode());
        String pressureSummary = pressureSummary(response);
        String confidenceSummary = confidenceSummary(confidence, resolved, response);
        String headline = chooseHeadline(
                precipitationState,
                precipitationSummary,
                current,
                resolved,
                comfortSummary,
                windSummary,
                visibilitySummary,
                pressureSummary
        );

        return new Report(
                precipitationState,
                confidence,
                headline,
                precipitationSummary,
                comfortSummary,
                windSummary,
                visibilitySummary,
                pressureSummary,
                confidenceSummary,
                forecast.timeLabel,
                forecast.probability,
                forecast.amountMm,
                forecast.hoursFromNow,
                resolved
        );
    }

    private static boolean isCurrentPrecipitationCode(
            @NonNull LiveConditionResolver.ResolvedCondition resolved
    ) {
        Integer code = resolved.getWeatherCode();
        if (!isAnyPrecipitationCode(code)) return false;
        String source = resolved.getSource().toLowerCase(Locale.US);
        return source.contains("current precipitation weather code")
                || source.contains("corroborated")
                || source.contains("thunderstorm weather code");
    }

    @NonNull
    private static String chooseHeadline(
            @NonNull PrecipitationState precipitationState,
            @NonNull String precipitationSummary,
            @NonNull WeatherResponse.CurrentWeather current,
            @NonNull LiveConditionResolver.ResolvedCondition resolved,
            @NonNull String comfortSummary,
            @NonNull String windSummary,
            @NonNull String visibilitySummary,
            @NonNull String pressureSummary
    ) {
        switch (precipitationState) {
            case THUNDERSTORM_NOW:
            case PRECIPITATION_NOW_CONFIRMED:
            case WEAK_SIGNAL_UNCONFIRMED:
            case RAIN_LIKELY_SOON:
            case RAIN_POSSIBLE_SOON:
            case RAIN_LIKELY_LATER:
            case RAIN_POSSIBLE_LATER:
                return precipitationSummary;
            default:
                break;
        }

        double gust = value(current.getWindGusts10m());
        if (gust >= 45d) return windSummary;

        double visibility = value(current.getVisibility());
        if (visibility > 0d && visibility < 3000d) return visibilitySummary;

        double apparent = nullableValue(current.getApparentTemperature(), Double.NaN);
        if (!Double.isNaN(apparent) && (apparent >= 38d || apparent <= 5d)) {
            return comfortSummary;
        }

        if (resolved.getWeatherCode() != null && resolved.getWeatherCode() <= 1) {
            return "Mostly clear now. " + pressureSummary;
        }
        return "Current conditions are fairly steady. " + pressureSummary;
    }

    @NonNull
    private static String precipitationSummary(
            @NonNull PrecipitationState state,
            @NonNull LiveConditionResolver.ResolvedCondition resolved,
            @NonNull ForecastPrecipitation forecast,
            @NonNull WeatherResponse.CurrentWeather current
    ) {
        switch (state) {
            case THUNDERSTORM_NOW:
                return "Thunderstorm classification is active now in the weather model. Check official alerts and Radar for local context.";
            case PRECIPITATION_NOW_CONFIRMED:
                if (isSnowCode(resolved.getWeatherCode())) {
                    return resolved.getPrecipitationSignalMm() > 0d
                            ? "Snow is indicated now by the current or corroborated short-term model signal ("
                            + WeatherFormatter.precipitation(resolved.getPrecipitationSignalMm()) + " water-equivalent signal)."
                            : "Snow is classified now by the current weather model.";
                }
                return resolved.getPrecipitationSignalMm() > 0d
                        ? "Rain/showers are indicated now by the current or corroborated short-term model signal ("
                        + WeatherFormatter.precipitation(resolved.getPrecipitationSignalMm()) + ")."
                        : "Rain/showers are classified now by the current weather model.";
            case WEAK_SIGNAL_UNCONFIRMED:
                return "A weak precipitation signal exists near the current model interval, but it is not strong enough to say precipitation is falling at your exact location now.";
            case RAIN_LIKELY_SOON:
                return "It is not confirmed raining now. Rain becomes likely "
                        + relativeTime(forecast) + probabilitySuffix(forecast) + ".";
            case RAIN_POSSIBLE_SOON:
                return "It is not confirmed raining now. Rain is possible "
                        + relativeTime(forecast) + probabilitySuffix(forecast) + ".";
            case RAIN_LIKELY_LATER:
                return "No confirmed rain now. Rain becomes likely later "
                        + relativeTime(forecast) + probabilitySuffix(forecast) + ".";
            case RAIN_POSSIBLE_LATER:
                return "No confirmed rain now. There is a possible rain window later "
                        + relativeTime(forecast) + probabilitySuffix(forecast) + ".";
            case DRY_NOW:
                return value(current.getPrecipitation()) > 0d
                        ? "No confirmed precipitation at the current location; a small model amount is present but is not corroborated as precipitation now."
                        : "No confirmed precipitation now and no strong rain window is detected in the next several hours.";
            case UNKNOWN:
            default:
                return "Precipitation timing is unavailable until the next weather sync.";
        }
    }

    @NonNull
    private static String comfortSummary(@NonNull WeatherResponse.CurrentWeather current) {
        Double temperature = current.getTemperature2m();
        Double apparent = current.getApparentTemperature();
        Double humidity = current.getRelativeHumidity2m();
        Double dewPoint = current.getDewPoint2m();

        if (temperature == null && apparent == null) {
            return "Comfort interpretation is unavailable.";
        }

        StringBuilder builder = new StringBuilder();
        if (temperature != null && apparent != null) {
            double delta = apparent - temperature;
            if (delta >= 2d) {
                builder.append("Feels warmer than the measured air temperature: ")
                        .append(WeatherFormatter.temperature(apparent))
                        .append(" vs ")
                        .append(WeatherFormatter.temperature(temperature));
            } else if (delta <= -2d) {
                builder.append("Feels cooler than the measured air temperature: ")
                        .append(WeatherFormatter.temperature(apparent))
                        .append(" vs ")
                        .append(WeatherFormatter.temperature(temperature));
            } else {
                builder.append("Feels close to the measured air temperature at ")
                        .append(WeatherFormatter.temperature(apparent));
            }
        } else if (apparent != null) {
            builder.append("Feels like ").append(WeatherFormatter.temperature(apparent));
        } else {
            builder.append("Air temperature ").append(WeatherFormatter.temperature(temperature));
        }

        if (dewPoint != null) {
            if (dewPoint >= 24d) builder.append(" · very muggy moisture level");
            else if (dewPoint >= 20d) builder.append(" · humid/muggy moisture level");
            else if (dewPoint >= 16d) builder.append(" · moderately humid");
            else if (dewPoint < 10d) builder.append(" · relatively dry air");
        } else if (humidity != null && humidity >= 80d) {
            builder.append(" · high relative humidity");
        }

        if (humidity != null) {
            builder.append(" · humidity ").append(WeatherFormatter.percent(humidity));
        }
        builder.append(".");
        return builder.toString();
    }

    @NonNull
    private static String windSummary(@NonNull WeatherResponse.CurrentWeather current) {
        Double speedValue = current.getWindSpeed10m();
        Double gustValue = current.getWindGusts10m();
        double speedRaw = value(speedValue);
        double gustRaw = value(gustValue);

        String character;
        if (gustRaw >= 60d) character = "Strong gusts";
        else if (gustRaw - speedRaw >= 20d || (speedRaw > 0d && gustRaw / speedRaw >= 1.7d)) {
            character = "Gusty wind";
        } else if (speedRaw >= 30d) character = "Strong/breezy wind";
        else if (speedRaw >= 15d) character = "Breezy";
        else character = "Light wind";

        return character + " from " + WeatherFormatter.windDirection(current.getWindDirection10m())
                + " · sustained " + WeatherFormatter.wind(speedValue)
                + " · gusts " + WeatherFormatter.wind(gustValue) + ".";
    }

    @NonNull
    private static String visibilitySummary(
            @NonNull WeatherResponse.CurrentWeather current,
            @Nullable Integer resolvedCode
    ) {
        Double meters = current.getVisibility();
        if (meters == null) return "Visibility interpretation is unavailable.";

        String level;
        if (meters <= 1000d) level = "Very low visibility";
        else if (meters <= 3000d) level = "Low visibility";
        else if (meters <= 10000d) level = "Moderate visibility";
        else level = "Good visibility";

        boolean fogCode = resolvedCode != null && (resolvedCode == 45 || resolvedCode == 48);
        if (fogCode) {
            return level + " at " + WeatherFormatter.visibilityDistance(meters)
                    + " · the current weather classification supports fog.";
        }
        if (meters <= 3000d) {
            return level + " at " + WeatherFormatter.visibilityDistance(meters)
                    + " · haze, precipitation or near-surface moisture may contribute; the model does not prove the exact cause.";
        }
        return level + " at " + WeatherFormatter.visibilityDistance(meters) + ".";
    }

    @NonNull
    private static String pressureSummary(@NonNull WeatherResponse response) {
        WeatherResponse.CurrentWeather current = response.getCurrent();
        if (current == null || current.getPressureMsl() == null) {
            return "Pressure trend is unavailable.";
        }

        WeatherResponse.HourlyWeather hourly = response.getHourly();
        if (hourly == null || hourly.getPressureMsl() == null || hourly.getPressureMsl().isEmpty()) {
            return "Pressure is " + WeatherFormatter.pressure(current.getPressureMsl())
                    + "; a short-term trend is unavailable.";
        }

        int start = WeatherFormatter.findCurrentHourlyIndex(response);
        int futureIndex = Math.min(start + 3, hourly.getPressureMsl().size() - 1);
        Double future = WeatherFormatter.valueAt(hourly.getPressureMsl(), futureIndex);
        if (future == null) {
            return "Pressure is " + WeatherFormatter.pressure(current.getPressureMsl())
                    + "; a short-term trend is unavailable.";
        }

        double deltaHpa = future - current.getPressureMsl();
        String trend = deltaHpa >= 1.5d
                ? "rising"
                : deltaHpa <= -1.5d ? "falling" : "fairly steady";
        return "Pressure is " + WeatherFormatter.pressure(current.getPressureMsl())
                + " and is " + trend + " over roughly the next 3 hours; pressure alone does not determine the weather.";
    }

    @NonNull
    private static String confidenceSummary(
            @NonNull ConfidenceLevel confidence,
            @NonNull LiveConditionResolver.ResolvedCondition resolved,
            @NonNull WeatherResponse response
    ) {
        boolean hasMinutely = response.getMinutely15() != null
                && response.getMinutely15().getTime() != null
                && !response.getMinutely15().getTime().isEmpty();
        switch (confidence) {
            case HIGHER:
                return "Higher model consistency · " + resolved.getSource()
                        + (hasMinutely ? " · 15-minute cross-check available." : ".");
            case LIMITED:
                return "Limited confidence for precipitation-now wording · " + resolved.getSource()
                        + ". The app keeps this as an unconfirmed model signal.";
            case STANDARD:
            default:
                return "Standard model confidence · " + resolved.getSource()
                        + (hasMinutely ? " · 15-minute context available." : " · no 15-minute cross-check available.");
        }
    }

    @NonNull
    private static ConfidenceLevel resolveConfidence(
            @NonNull LiveConditionResolver.ResolvedCondition resolved,
            @NonNull WeatherResponse response
    ) {
        String source = resolved.getSource().toLowerCase(Locale.US);
        if (source.contains("weak precipitation signal unconfirmed")) return ConfidenceLevel.LIMITED;
        if (source.contains("corroborated")) return ConfidenceLevel.HIGHER;
        if (source.contains("current precipitation weather code")
                && response.getMinutely15() != null
                && response.getMinutely15().getTime() != null
                && !response.getMinutely15().getTime().isEmpty()) {
            return ConfidenceLevel.HIGHER;
        }
        return ConfidenceLevel.STANDARD;
    }

    @NonNull
    private static ForecastPrecipitation findForecastRain(@NonNull WeatherResponse response) {
        WeatherResponse.HourlyWeather hourly = response.getHourly();
        if (hourly == null || hourly.getTime() == null || hourly.getTime().isEmpty()) {
            return ForecastPrecipitation.NONE;
        }

        int start = WeatherFormatter.findCurrentHourlyIndex(response);
        int end = Math.min(hourly.getTime().size(), start + 13);
        ForecastPrecipitation firstPossible = ForecastPrecipitation.NONE;

        for (int index = start; index < end; index++) {
            Double probability = WeatherFormatter.valueAt(hourly.getPrecipitationProbability(), index);
            Double amount = WeatherFormatter.valueAt(hourly.getPrecipitation(), index);
            Integer code = WeatherFormatter.valueAt(hourly.getWeatherCode(), index);
            String time = WeatherFormatter.valueAt(hourly.getTime(), index);

            double chance = value(probability);
            double precip = value(amount);
            boolean codeWet = isRainForecastCode(code);
            boolean likely = chance >= LIKELY_RAIN_PROBABILITY
                    && (codeWet || precip >= 0.05d || chance >= 75d);
            boolean possible = chance >= POSSIBLE_RAIN_PROBABILITY
                    || (codeWet && precip >= 0.05d)
                    || (codeWet && precip >= MEANINGFUL_HOURLY_PRECIP_MM);

            if (!possible) continue;

            int hoursFromNow = Math.max(0, index - start);
            ForecastPrecipitation candidate = new ForecastPrecipitation(
                    true,
                    likely,
                    WeatherFormatter.hourLabel(time),
                    probability,
                    amount,
                    hoursFromNow
            );
            if (likely) return candidate;
            if (!firstPossible.possible) firstPossible = candidate;
        }
        return firstPossible;
    }

    @NonNull
    private static String relativeTime(@NonNull ForecastPrecipitation forecast) {
        if (forecast.hoursFromNow <= 0) return "in the current forecast hour";
        if (forecast.hoursFromNow == 1) return "within about 1 hour (" + forecast.timeLabel + ")";
        if (forecast.hoursFromNow == 2) return "within about 2 hours (" + forecast.timeLabel + ")";
        return "around " + forecast.timeLabel;
    }

    @NonNull
    private static String probabilitySuffix(@NonNull ForecastPrecipitation forecast) {
        if (forecast.probability != null) {
            return " with about " + Math.round(forecast.probability) + "% forecast probability";
        }
        if (forecast.amountMm != null && forecast.amountMm >= MEANINGFUL_HOURLY_PRECIP_MM) {
            return " with a model precipitation amount near "
                    + WeatherFormatter.precipitation(forecast.amountMm);
        }
        return "";
    }

    private static boolean isAnyPrecipitationCode(@Nullable Integer code) {
        return code != null && (
                (code >= 51 && code <= 67)
                        || (code >= 71 && code <= 77)
                        || (code >= 80 && code <= 86)
                        || code >= 95
        );
    }

    private static boolean isRainForecastCode(@Nullable Integer code) {
        return code != null && (
                (code >= 51 && code <= 67)
                        || (code >= 80 && code <= 82)
                        || code >= 95
        );
    }

    private static boolean isSnowCode(@Nullable Integer code) {
        return code != null && ((code >= 71 && code <= 77) || code == 85 || code == 86);
    }

    private static double value(@Nullable Double value) {
        return value == null ? 0d : Math.max(0d, value);
    }

    private static double nullableValue(@Nullable Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static final class ForecastPrecipitation {
        static final ForecastPrecipitation NONE = new ForecastPrecipitation(
                false, false, "", null, null, Integer.MAX_VALUE
        );

        final boolean possible;
        final boolean likely;
        final String timeLabel;
        final Double probability;
        final Double amountMm;
        final int hoursFromNow;

        ForecastPrecipitation(
                boolean possible,
                boolean likely,
                @NonNull String timeLabel,
                @Nullable Double probability,
                @Nullable Double amountMm,
                int hoursFromNow
        ) {
            this.possible = possible;
            this.likely = likely;
            this.timeLabel = timeLabel;
            this.probability = probability;
            this.amountMm = amountMm;
            this.hoursFromNow = hoursFromNow;
        }
    }

    public static final class Report {
        private final PrecipitationState precipitationState;
        private final ConfidenceLevel confidenceLevel;
        private final String headline;
        private final String precipitationSummary;
        private final String comfortSummary;
        private final String windSummary;
        private final String visibilitySummary;
        private final String pressureSummary;
        private final String confidenceSummary;
        private final String nextPrecipitationTime;
        private final Double nextPrecipitationProbability;
        private final Double nextPrecipitationAmountMm;
        private final int nextPrecipitationHours;
        private final LiveConditionResolver.ResolvedCondition resolvedCondition;

        Report(
                @NonNull PrecipitationState precipitationState,
                @NonNull ConfidenceLevel confidenceLevel,
                @NonNull String headline,
                @NonNull String precipitationSummary,
                @NonNull String comfortSummary,
                @NonNull String windSummary,
                @NonNull String visibilitySummary,
                @NonNull String pressureSummary,
                @NonNull String confidenceSummary,
                @NonNull String nextPrecipitationTime,
                @Nullable Double nextPrecipitationProbability,
                @Nullable Double nextPrecipitationAmountMm,
                int nextPrecipitationHours,
                @NonNull LiveConditionResolver.ResolvedCondition resolvedCondition
        ) {
            this.precipitationState = precipitationState;
            this.confidenceLevel = confidenceLevel;
            this.headline = headline;
            this.precipitationSummary = precipitationSummary;
            this.comfortSummary = comfortSummary;
            this.windSummary = windSummary;
            this.visibilitySummary = visibilitySummary;
            this.pressureSummary = pressureSummary;
            this.confidenceSummary = confidenceSummary;
            this.nextPrecipitationTime = nextPrecipitationTime;
            this.nextPrecipitationProbability = nextPrecipitationProbability;
            this.nextPrecipitationAmountMm = nextPrecipitationAmountMm;
            this.nextPrecipitationHours = nextPrecipitationHours;
            this.resolvedCondition = resolvedCondition;
        }

        @NonNull
        static Report unavailable() {
            LiveConditionResolver.ResolvedCondition resolved = LiveConditionResolver.resolve(null);
            return new Report(
                    PrecipitationState.UNKNOWN,
                    ConfidenceLevel.LIMITED,
                    "Live weather intelligence will appear after the next successful sync.",
                    "Precipitation timing is unavailable until the next weather sync.",
                    "Comfort interpretation is unavailable.",
                    "Wind interpretation is unavailable.",
                    "Visibility interpretation is unavailable.",
                    "Pressure trend is unavailable.",
                    "Weather data is unavailable.",
                    "",
                    null,
                    null,
                    Integer.MAX_VALUE,
                    resolved
            );
        }

        @NonNull public PrecipitationState getPrecipitationState() { return precipitationState; }
        @NonNull public ConfidenceLevel getConfidenceLevel() { return confidenceLevel; }
        @NonNull public String getHeadline() { return headline; }
        @NonNull public String getPrecipitationSummary() { return precipitationSummary; }
        @NonNull public String getComfortSummary() { return comfortSummary; }
        @NonNull public String getWindSummary() { return windSummary; }
        @NonNull public String getVisibilitySummary() { return visibilitySummary; }
        @NonNull public String getPressureSummary() { return pressureSummary; }
        @NonNull public String getConfidenceSummary() { return confidenceSummary; }
        @NonNull public String getNextPrecipitationTime() { return nextPrecipitationTime; }
        @Nullable public Double getNextPrecipitationProbability() { return nextPrecipitationProbability; }
        @Nullable public Double getNextPrecipitationAmountMm() { return nextPrecipitationAmountMm; }
        public int getNextPrecipitationHours() { return nextPrecipitationHours; }
        @NonNull public LiveConditionResolver.ResolvedCondition getResolvedCondition() { return resolvedCondition; }

        public boolean isPrecipitationNowConfirmed() {
            return precipitationState == PrecipitationState.PRECIPITATION_NOW_CONFIRMED
                    || precipitationState == PrecipitationState.THUNDERSTORM_NOW;
        }
    }
}
