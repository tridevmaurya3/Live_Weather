package com.tridev.liveweather.ui.weather;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.tridev.liveweather.R;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.CelestialDayState;
import com.tridev.liveweather.domain.CelestialForecastEngine;
import com.tridev.liveweather.domain.LiveConditionResolver;
import com.tridev.liveweather.domain.WeatherUiState;
import com.tridev.liveweather.ui.sky.LiveSkyView;

import java.util.List;
import java.util.Locale;

/**
 * Advanced current-condition overlay renderer.
 *
 * Phase 18 routes user-facing interpretation through WeatherIntelligence2 so
 * probability, current precipitation evidence, comfort, wind, visibility,
 * pressure and confidence are described consistently across the app.
 */
public final class Phase6Renderer {

    private final Activity activity;

    private final android.view.View homeHeroCard;
    private final TextView homeCondition;
    private final TextView homeRainValue;
    private final TextView homeWeatherInsight;
    private final TextView homeWallpaperSummary;
    private final TextView wallpaperPreviewCondition;
    private final TextView wallpaperCelestialSummary;
    private final TextView forecastCurrentSummary;

    private final LiveSkyView forecastLiveSkyView;
    private final LiveSkyView wallpaperLiveSkyView;
    private final TextView forecastCelestialTodaySummary;
    private final LinearLayout forecastMoonPhaseContainer;

    private final TextView forecastConditionSourceValue;
    private final TextView forecastPrecipBreakdownValue;
    private final TextView forecastWindDetailValue;
    private final TextView forecastAtmosphereDetailValue;
    private final TextView forecastComfortDetailValue;
    private final TextView forecastDataQualityValue;

    private float locationAccuracyMeters = Float.NaN;
    private boolean preciseLocation;

    public Phase6Renderer(@NonNull Activity activity) {
        this.activity = activity;

        homeHeroCard = activity.findViewById(R.id.homeHeroCard);
        homeCondition = activity.findViewById(R.id.homeCondition);
        homeRainValue = activity.findViewById(R.id.homeRainValue);
        homeWeatherInsight = activity.findViewById(R.id.homeWeatherInsight);
        homeWallpaperSummary = activity.findViewById(R.id.homeWallpaperSummary);
        wallpaperPreviewCondition = activity.findViewById(R.id.wallpaperPreviewCondition);
        wallpaperCelestialSummary = activity.findViewById(R.id.wallpaperCelestialSummary);
        forecastCurrentSummary = activity.findViewById(R.id.forecastCurrentSummary);

        forecastLiveSkyView = activity.findViewById(R.id.forecastLiveSkyView);
        wallpaperLiveSkyView = activity.findViewById(R.id.wallpaperLiveSkyView);
        forecastCelestialTodaySummary = activity.findViewById(R.id.forecastCelestialTodaySummary);
        forecastMoonPhaseContainer = activity.findViewById(R.id.forecastMoonPhaseContainer);

        forecastConditionSourceValue = activity.findViewById(R.id.forecastConditionSourceValue);
        forecastPrecipBreakdownValue = activity.findViewById(R.id.forecastPrecipBreakdownValue);
        forecastWindDetailValue = activity.findViewById(R.id.forecastWindDetailValue);
        forecastAtmosphereDetailValue = activity.findViewById(R.id.forecastAtmosphereDetailValue);
        forecastComfortDetailValue = activity.findViewById(R.id.forecastComfortDetailValue);
        forecastDataQualityValue = activity.findViewById(R.id.forecastDataQualityValue);
    }

    public void setLocationAccuracy(float accuracyMeters, boolean precise) {
        locationAccuracyMeters = accuracyMeters;
        preciseLocation = precise;
    }

    public void clearLocationAccuracy() {
        locationAccuracyMeters = Float.NaN;
        preciseLocation = false;
    }

    public void render(@NonNull WeatherUiState state) {
        if (!state.hasWeather() || state.getWeather() == null
                || Double.isNaN(state.getLatitude()) || Double.isNaN(state.getLongitude())) {
            renderWaiting();
            return;
        }

        WeatherResponse response = state.getWeather();
        WeatherIntelligence2.Report intelligence = WeatherIntelligence2.analyze(response);
        LiveConditionResolver.ResolvedCondition resolved = intelligence.getResolvedCondition();
        WeatherResponse.CurrentWeather current = response.getCurrent();

        applyResolvedCurrentCondition(response, resolved, intelligence);

        forecastLiveSkyView.setWeatherData(response, state.getLatitude(), state.getLongitude());
        wallpaperLiveSkyView.setWeatherData(response, state.getLatitude(), state.getLongitude());

        renderCelestialTimeline(response, state.getLatitude(), state.getLongitude());
        renderAdvancedDetails(response, resolved, intelligence, state);

        if (current != null) {
            String symbol = WeatherFormatter.symbol(resolved.getWeatherCode(), current.getIsDay());
            wallpaperPreviewCondition.setText(String.format(
                    Locale.getDefault(),
                    "%s  %s · %s",
                    symbol,
                    resolved.getLabel(),
                    resolved.getSource()
            ));
            forecastCurrentSummary.setText(String.format(
                    Locale.getDefault(),
                    "%s %s · %s · Feels %s · Humidity %s · %s",
                    symbol,
                    WeatherFormatter.temperature(current.getTemperature2m()),
                    resolved.getLabel(),
                    WeatherFormatter.temperature(current.getApparentTemperature()),
                    WeatherFormatter.percent(current.getRelativeHumidity2m()),
                    DashboardIntelligence.visibility(current.getVisibility())
            ));
        }
    }

    private void applyResolvedCurrentCondition(
            @NonNull WeatherResponse response,
            @NonNull LiveConditionResolver.ResolvedCondition resolved,
            @NonNull WeatherIntelligence2.Report intelligence
    ) {
        WeatherResponse.CurrentWeather current = response.getCurrent();
        Integer isDay = current == null ? resolved.getIsDay() : current.getIsDay();
        homeCondition.setText(
                WeatherFormatter.symbol(resolved.getWeatherCode(), isDay)
                        + "  " + resolved.getLabel()
        );
        homeWeatherInsight.setText(intelligence.getHeadline());

        if (intelligence.isPrecipitationNowConfirmed()
                && resolved.getPrecipitationSignalMm() > 0.02d) {
            homeRainValue.setText(
                    WeatherFormatter.precipitation(resolved.getPrecipitationSignalMm()) + " signal"
            );
        }

        if (current != null) {
            String dayPart = current.getIsDay() != null && current.getIsDay() == 0
                    ? "Night"
                    : "Day";
            homeWallpaperSummary.setText(String.format(
                    Locale.getDefault(),
                    "Reality source: %s · %s · Clouds %s · Gusts %s · %s",
                    resolved.getLabel(),
                    resolved.getSource(),
                    WeatherFormatter.percent(current.getCloudCover()),
                    WeatherFormatter.wind(current.getWindGusts10m()),
                    dayPart
            ));
        }

        applyHero(DashboardIntelligence.heroMode(resolved.getWeatherCode(), isDay));
    }

    private void applyHero(@NonNull DashboardIntelligence.HeroMode mode) {
        int background;
        switch (mode) {
            case CLEAR_DAY:
                background = R.drawable.bg_weather_hero_clear_day;
                break;
            case CLEAR_NIGHT:
                background = R.drawable.bg_weather_hero_clear_night;
                break;
            case RAIN:
                background = R.drawable.bg_weather_hero_rain;
                break;
            case STORM:
                background = R.drawable.bg_weather_hero_storm;
                break;
            case SNOW:
                background = R.drawable.bg_weather_hero_snow;
                break;
            case FOG:
                background = R.drawable.bg_weather_hero_fog;
                break;
            case CLOUDY:
            default:
                background = R.drawable.bg_weather_hero_cloudy;
                break;
        }
        homeHeroCard.setBackgroundResource(background);
    }

    private void renderCelestialTimeline(
            @NonNull WeatherResponse response,
            double latitude,
            double longitude
    ) {
        List<CelestialDayState> days = CelestialForecastEngine.build(
                response,
                latitude,
                longitude,
                10,
                System.currentTimeMillis()
        );

        forecastMoonPhaseContainer.removeAllViews();
        if (days.isEmpty()) {
            forecastCelestialTodaySummary.setText(R.string.phase6_celestial_waiting);
            wallpaperCelestialSummary.setText(R.string.phase6_celestial_waiting);
            return;
        }

        CelestialDayState today = days.get(0);
        String summary = String.format(
                Locale.getDefault(),
                "%s · %.0f%% illuminated\nSun ↑ %s  ↓ %s · Moon ↑ %s  ↓ %s",
                today.getPhaseName(),
                today.getIlluminationPercent(),
                today.getSunrise(),
                today.getSunset(),
                today.getMoonrise(),
                today.getMoonset()
        );
        forecastCelestialTodaySummary.setText(summary);
        wallpaperCelestialSummary.setText(summary.replace("\n", " · "));

        for (CelestialDayState day : days) {
            TextView chip = new TextView(activity);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(132), dp(150));
            if (forecastMoonPhaseContainer.getChildCount() > 0) {
                params.setMarginStart(dp(8));
            }
            chip.setLayoutParams(params);
            chip.setBackgroundResource(R.drawable.bg_weather_chip);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(8), dp(8), dp(8), dp(8));
            chip.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
            chip.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            chip.setText(String.format(
                    Locale.getDefault(),
                    "%s\n%s  %.0f%%\n%s\nMoon ↑ %s\nMoon ↓ %s",
                    day.getDayLabel(),
                    CelestialForecastEngine.phaseSymbol(day),
                    day.getIlluminationPercent(),
                    day.getPhaseName(),
                    day.getMoonrise(),
                    day.getMoonset()
            ));
            forecastMoonPhaseContainer.addView(chip);
        }
    }

    private void renderAdvancedDetails(
            @NonNull WeatherResponse response,
            @NonNull LiveConditionResolver.ResolvedCondition resolved,
            @NonNull WeatherIntelligence2.Report intelligence,
            @NonNull WeatherUiState state
    ) {
        WeatherResponse.CurrentWeather current = response.getCurrent();
        if (current == null) {
            renderWaiting();
            return;
        }

        forecastConditionSourceValue.setText(String.format(
                Locale.getDefault(),
                "NOW · %s · %s\nSignal source: %s",
                resolved.getLabel(),
                precipitationStateLabel(intelligence.getPrecipitationState()),
                resolved.getSource()
        ));

        forecastPrecipBreakdownValue.setText(String.format(
                Locale.getDefault(),
                "%s\nRaw model · Total %s · Rain %s · Showers %s · Snow %.2f cm",
                intelligence.getPrecipitationSummary(),
                WeatherFormatter.precipitation(current.getPrecipitation()),
                WeatherFormatter.precipitation(current.getRain()),
                WeatherFormatter.precipitation(current.getShowers()),
                current.getSnowfall() == null ? 0d : current.getSnowfall()
        ));

        forecastWindDetailValue.setText(intelligence.getWindSummary());

        forecastAtmosphereDetailValue.setText(String.format(
                Locale.getDefault(),
                "%s\n%s\nCloud cover %s",
                intelligence.getVisibilitySummary(),
                intelligence.getPressureSummary(),
                WeatherFormatter.percent(current.getCloudCover())
        ));

        forecastComfortDetailValue.setText(intelligence.getComfortSummary());

        String locationQuality;
        if (Float.isNaN(locationAccuracyMeters)) {
            locationQuality = "selected city coordinates";
        } else {
            locationQuality = String.format(
                    Locale.getDefault(),
                    "%s location ±%.0f m",
                    preciseLocation ? "precise" : "approximate",
                    locationAccuracyMeters
            );
        }

        forecastDataQualityValue.setText(String.format(
                Locale.getDefault(),
                "%s\nLocation: %s · %s · updated %s%s",
                intelligence.getConfidenceSummary(),
                locationQuality,
                freshnessLabel(state.getUpdatedAt()),
                WeatherFormatter.updatedTime(state.getUpdatedAt()),
                state.isFromCache() ? " · saved/offline snapshot" : ""
        ));
    }

    @NonNull
    private static String precipitationStateLabel(
            @NonNull WeatherIntelligence2.PrecipitationState state
    ) {
        switch (state) {
            case THUNDERSTORM_NOW:
                return "thunderstorm now";
            case PRECIPITATION_NOW_CONFIRMED:
                return "precipitation now";
            case WEAK_SIGNAL_UNCONFIRMED:
                return "weak signal unconfirmed";
            case RAIN_LIKELY_SOON:
                return "rain likely soon";
            case RAIN_POSSIBLE_SOON:
                return "rain possible soon";
            case RAIN_LIKELY_LATER:
                return "rain likely later";
            case RAIN_POSSIBLE_LATER:
                return "rain possible later";
            case DRY_NOW:
                return "dry now";
            case UNKNOWN:
            default:
                return "timing unavailable";
        }
    }

    @NonNull
    private static String freshnessLabel(long updatedAt) {
        if (updatedAt <= 0L) return "unknown age";
        long ageMinutes = Math.max(0L, (System.currentTimeMillis() - updatedAt) / 60_000L);
        if (ageMinutes <= 30L) return "fresh data";
        if (ageMinutes <= 90L) return "recent data";
        if (ageMinutes <= 180L) return "aging data";
        return "stale data";
    }

    private void renderWaiting() {
        forecastLiveSkyView.clearWeatherData();
        wallpaperLiveSkyView.clearWeatherData();
        forecastMoonPhaseContainer.removeAllViews();
        forecastCelestialTodaySummary.setText(R.string.phase6_celestial_waiting);
        wallpaperCelestialSummary.setText(R.string.phase6_celestial_waiting);
        forecastConditionSourceValue.setText(R.string.phase6_details_waiting);
        forecastPrecipBreakdownValue.setText(R.string.phase6_details_waiting);
        forecastWindDetailValue.setText(R.string.phase6_details_waiting);
        forecastAtmosphereDetailValue.setText(R.string.phase6_details_waiting);
        forecastComfortDetailValue.setText(R.string.phase6_details_waiting);
        forecastDataQualityValue.setText(R.string.phase6_data_quality_waiting);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
