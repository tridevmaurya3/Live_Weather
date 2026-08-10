package com.tridev.liveweather.ui.weather;

import android.app.Activity;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.tridev.liveweather.R;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.SkyRealityEngine;
import com.tridev.liveweather.domain.SkyRealityState;
import com.tridev.liveweather.domain.WeatherUiState;
import com.tridev.liveweather.ui.forecast.ForecastChartView;
import com.tridev.liveweather.ui.forecast.ForecastIntelligence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Renders one shared WeatherUiState into Home, Forecast and Wallpaper preview.
 */
public final class WeatherScreenRenderer {

    private final Activity activity;
    private final View skyAtmosphereOverlay;

    private final View homeHeroCard;
    private final TextView homeTemperature;
    private final TextView homeCondition;
    private final TextView homeFeelsLike;
    private final TextView homeHighLow;
    private final TextView homeSyncStatus;
    private final TextView homeHumidityValue;
    private final TextView homeWindValue;
    private final TextView homeRainValue;
    private final TextView homeWeatherInsight;
    private final TextView homeSunValue;
    private final TextView homeDaylightValue;
    private final TextView homeUvValue;
    private final TextView homePressureValue;
    private final TextView homeVisibilityValue;
    private final TextView homeCloudValue;
    private final TextView homeDewPointValue;
    private final TextView homeGustValue;
    private final TextView homeRainChanceValue;
    private final TextView homeTenDaySummary;
    private final TextView homeWallpaperSummary;
    private final LinearLayout homeHourlyContainer;

    private final TextView forecastSmartSummary;
    private final ForecastChartView forecastTemperatureChart;
    private final ForecastChartView forecastRainChart;
    private final TextView forecastCurrentSummary;
    private final TextView forecastDailySummary;
    private final TextView forecastWindSummary;
    private final TextView forecastStatus;
    private final LinearLayout forecastHourlyContainer;
    private final LinearLayout forecastDailyContainer;
    private final TextView forecastSkyStageValue;
    private final TextView forecastSunPositionValue;
    private final TextView forecastMoonPositionValue;
    private final TextView forecastStarVisibilityValue;
    private final TextView forecastSceneLightValue;

    private final TextView wallpaperPreviewTemperature;
    private final TextView wallpaperPreviewCondition;

    public WeatherScreenRenderer(@NonNull Activity activity) {
        this.activity = activity;
        skyAtmosphereOverlay = activity.findViewById(R.id.skyAtmosphereOverlay);

        homeHeroCard = activity.findViewById(R.id.homeHeroCard);
        homeTemperature = activity.findViewById(R.id.homeTemperature);
        homeCondition = activity.findViewById(R.id.homeCondition);
        homeFeelsLike = activity.findViewById(R.id.homeFeelsLike);
        homeHighLow = activity.findViewById(R.id.homeHighLow);
        homeSyncStatus = activity.findViewById(R.id.homeSyncStatus);
        homeHumidityValue = activity.findViewById(R.id.homeHumidityValue);
        homeWindValue = activity.findViewById(R.id.homeWindValue);
        homeRainValue = activity.findViewById(R.id.homeRainValue);
        homeWeatherInsight = activity.findViewById(R.id.homeWeatherInsight);
        homeSunValue = activity.findViewById(R.id.homeSunValue);
        homeDaylightValue = activity.findViewById(R.id.homeDaylightValue);
        homeUvValue = activity.findViewById(R.id.homeUvValue);
        homePressureValue = activity.findViewById(R.id.homePressureValue);
        homeVisibilityValue = activity.findViewById(R.id.homeVisibilityValue);
        homeCloudValue = activity.findViewById(R.id.homeCloudValue);
        homeDewPointValue = activity.findViewById(R.id.homeDewPointValue);
        homeGustValue = activity.findViewById(R.id.homeGustValue);
        homeRainChanceValue = activity.findViewById(R.id.homeRainChanceValue);
        homeTenDaySummary = activity.findViewById(R.id.homeTenDaySummary);
        homeWallpaperSummary = activity.findViewById(R.id.homeWallpaperSummary);
        homeHourlyContainer = activity.findViewById(R.id.homeHourlyContainer);

        forecastSmartSummary = activity.findViewById(R.id.forecastSmartSummary);
        forecastTemperatureChart = activity.findViewById(R.id.forecastTemperatureChart);
        forecastRainChart = activity.findViewById(R.id.forecastRainChart);
        forecastCurrentSummary = activity.findViewById(R.id.forecastCurrentSummary);
        forecastDailySummary = activity.findViewById(R.id.forecastDailySummary);
        forecastWindSummary = activity.findViewById(R.id.forecastWindSummary);
        forecastStatus = activity.findViewById(R.id.forecastStatus);
        forecastHourlyContainer = activity.findViewById(R.id.forecastHourlyContainer);
        forecastDailyContainer = activity.findViewById(R.id.forecastDailyContainer);
        forecastSkyStageValue = activity.findViewById(R.id.forecastSkyStageValue);
        forecastSunPositionValue = activity.findViewById(R.id.forecastSunPositionValue);
        forecastMoonPositionValue = activity.findViewById(R.id.forecastMoonPositionValue);
        forecastStarVisibilityValue = activity.findViewById(R.id.forecastStarVisibilityValue);
        forecastSceneLightValue = activity.findViewById(R.id.forecastSceneLightValue);

        forecastTemperatureChart.setMode(ForecastChartView.Mode.TEMPERATURE);
        forecastRainChart.setMode(ForecastChartView.Mode.PRECIPITATION);

        wallpaperPreviewTemperature = activity.findViewById(R.id.wallpaperPreviewTemperature);
        wallpaperPreviewCondition = activity.findViewById(R.id.wallpaperPreviewCondition);
    }

    public void render(@NonNull WeatherUiState state) {
        if (state.hasWeather() && state.getWeather() != null) {
            double latitude = state.getLatitude();
            double longitude = state.getLongitude();
            if (Double.isNaN(latitude) && state.getWeather().getLatitude() != null) {
                latitude = state.getWeather().getLatitude();
            }
            if (Double.isNaN(longitude) && state.getWeather().getLongitude() != null) {
                longitude = state.getWeather().getLongitude();
            }
            renderWeather(state.getWeather(), latitude, longitude);
        } else {
            renderEmptyWeather();
        }
        renderStatus(state);
    }

    private void renderWeather(
            @NonNull WeatherResponse response,
            double latitude,
            double longitude
    ) {
        WeatherResponse.CurrentWeather current = response.getCurrent();
        WeatherResponse.DailyWeather daily = response.getDaily();

        homeWeatherInsight.setText(DashboardIntelligence.insight(response));
        homeSunValue.setText(DashboardIntelligence.sunriseSunset(daily));
        homeDaylightValue.setText(DashboardIntelligence.daylight(daily));
        homeUvValue.setText(DashboardIntelligence.uv(
                daily == null ? null : WeatherFormatter.valueAt(daily.getUvIndexMax(), 0)
        ));
        homePressureValue.setText(DashboardIntelligence.pressure(response));
        homeRainChanceValue.setText(DashboardIntelligence.rainChance(daily));

        forecastSmartSummary.setText(ForecastIntelligence.next24Hours(response));
        forecastDailySummary.setText(ForecastIntelligence.tenDayOverview(response));

        if (current != null) {
            String condition = WeatherFormatter.condition(current.getWeatherCode());
            String symbol = WeatherFormatter.symbol(
                    current.getWeatherCode(),
                    current.getIsDay()
            );

            applyHeroMode(DashboardIntelligence.heroMode(current));

            homeTemperature.setText(WeatherFormatter.temperature(current.getTemperature2m()));
            homeCondition.setText(symbol + "  " + condition);
            homeFeelsLike.setText(
                    "Feels like " + WeatherFormatter.temperature(current.getApparentTemperature())
            );
            homeHumidityValue.setText(
                    WeatherFormatter.percent(current.getRelativeHumidity2m())
            );
            homeWindValue.setText(String.format(
                    Locale.getDefault(),
                    "%s %s",
                    WeatherFormatter.wind(current.getWindSpeed10m()),
                    WeatherFormatter.windDirection(current.getWindDirection10m())
            ));
            homeRainValue.setText(WeatherFormatter.precipitation(current.getPrecipitation()));
            homeVisibilityValue.setText(DashboardIntelligence.visibility(current.getVisibility()));
            homeCloudValue.setText(DashboardIntelligence.clouds(current.getCloudCover()));
            homeDewPointValue.setText(DashboardIntelligence.dewPoint(current.getDewPoint2m()));
            homeGustValue.setText(DashboardIntelligence.gusts(current.getWindGusts10m()));

            forecastCurrentSummary.setText(String.format(
                    Locale.getDefault(),
                    "%s %s · Feels %s · Humidity %s · %s",
                    symbol,
                    WeatherFormatter.temperature(current.getTemperature2m()),
                    WeatherFormatter.temperature(current.getApparentTemperature()),
                    WeatherFormatter.percent(current.getRelativeHumidity2m()),
                    DashboardIntelligence.visibility(current.getVisibility())
            ));

            String dayPart = current.getIsDay() != null && current.getIsDay() == 0
                    ? "Night"
                    : "Day";
            homeWallpaperSummary.setText(String.format(
                    Locale.getDefault(),
                    "Live scene source: %s · Clouds %s · Gusts %s · %s",
                    condition,
                    WeatherFormatter.percent(current.getCloudCover()),
                    WeatherFormatter.wind(current.getWindGusts10m()),
                    dayPart
            ));
            wallpaperPreviewTemperature.setText(
                    WeatherFormatter.temperature(current.getTemperature2m())
            );
            wallpaperPreviewCondition.setText(symbol + "  " + condition + " · " + dayPart);
        } else {
            applyHeroMode(DashboardIntelligence.HeroMode.CLOUDY);
            homeVisibilityValue.setText(R.string.metric_placeholder);
            homeCloudValue.setText(R.string.metric_placeholder);
            homeDewPointValue.setText(R.string.metric_placeholder);
            homeGustValue.setText(R.string.metric_placeholder);
        }

        renderDailyHeadline(daily);
        renderCharts(response);
        renderHourly(response);
        renderDaily(response);
        renderWindSummary(current, daily);
        renderSkyReality(response, latitude, longitude);
    }

    private void applyHeroMode(@NonNull DashboardIntelligence.HeroMode mode) {
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

    private void renderDailyHeadline(WeatherResponse.DailyWeather daily) {
        if (daily == null) {
            homeHighLow.setText(R.string.home_high_low);
            homeTenDaySummary.setText(R.string.quick_ten_day_sub);
            return;
        }

        Double high = WeatherFormatter.valueAt(daily.getTemperature2mMax(), 0);
        Double low = WeatherFormatter.valueAt(daily.getTemperature2mMin(), 0);
        Integer code = WeatherFormatter.valueAt(daily.getWeatherCode(), 0);
        Double rainChance = WeatherFormatter.valueAt(
                daily.getPrecipitationProbabilityMax(),
                0
        );
        Double uv = WeatherFormatter.valueAt(daily.getUvIndexMax(), 0);

        homeHighLow.setText(
                "H: " + WeatherFormatter.temperature(high)
                        + "  ·  L: " + WeatherFormatter.temperature(low)
        );

        String uvText = uv == null
                ? "—"
                : String.format(Locale.getDefault(), "%.1f", uv);
        homeTenDaySummary.setText(String.format(
                Locale.getDefault(),
                "Today · %s · Rain %s · UV %s",
                WeatherFormatter.condition(code),
                WeatherFormatter.percent(rainChance),
                uvText
        ));
    }

    private void renderCharts(@NonNull WeatherResponse response) {
        WeatherResponse.HourlyWeather hourly = response.getHourly();
        if (hourly == null || hourly.getTime() == null || hourly.getTime().isEmpty()) {
            forecastTemperatureChart.setData(Collections.emptyList(), Collections.emptyList());
            forecastRainChart.setData(Collections.emptyList(), Collections.emptyList());
            return;
        }

        int start = WeatherFormatter.findCurrentHourlyIndex(response);
        int end = Math.min(hourly.getTime().size(), start + 24);
        List<String> labels = new ArrayList<>();
        List<Double> temperatures = new ArrayList<>();
        List<Double> rainChances = new ArrayList<>();

        for (int index = start; index < end; index++) {
            labels.add(index == start
                    ? "Now"
                    : WeatherFormatter.hourLabel(
                            WeatherFormatter.valueAt(hourly.getTime(), index)
                    ));
            temperatures.add(WeatherFormatter.valueAt(hourly.getTemperature2m(), index));
            rainChances.add(WeatherFormatter.valueAt(
                    hourly.getPrecipitationProbability(),
                    index
            ));
        }

        forecastTemperatureChart.setData(labels, temperatures);
        forecastRainChart.setData(labels, rainChances);
    }

    private void renderHourly(@NonNull WeatherResponse response) {
        WeatherResponse.HourlyWeather hourly = response.getHourly();
        homeHourlyContainer.removeAllViews();
        forecastHourlyContainer.removeAllViews();

        if (hourly == null || hourly.getTime() == null || hourly.getTime().isEmpty()) {
            addWaitingChip(homeHourlyContainer, "Waiting for hourly data");
            addWaitingChip(forecastHourlyContainer, "Waiting for hourly data");
            return;
        }

        int startIndex = WeatherFormatter.findCurrentHourlyIndex(response);
        int available = hourly.getTime().size() - startIndex;
        int homeCount = Math.min(6, Math.max(0, available));
        int forecastCount = Math.min(24, Math.max(0, available));

        for (int offset = 0; offset < homeCount; offset++) {
            int index = startIndex + offset;
            addHourlyChip(homeHourlyContainer, hourly, index, offset == 0, 78);
        }

        for (int offset = 0; offset < forecastCount; offset++) {
            int index = startIndex + offset;
            addHourlyChip(forecastHourlyContainer, hourly, index, offset == 0, 88);
        }
    }

    private void addHourlyChip(
            @NonNull LinearLayout container,
            @NonNull WeatherResponse.HourlyWeather hourly,
            int index,
            boolean isNow,
            int widthDp
    ) {
        String time = isNow
                ? "Now"
                : WeatherFormatter.hourLabel(
                        WeatherFormatter.valueAt(hourly.getTime(), index)
                );
        Double temperature = WeatherFormatter.valueAt(hourly.getTemperature2m(), index);
        Integer weatherCode = WeatherFormatter.valueAt(hourly.getWeatherCode(), index);
        Integer isDay = WeatherFormatter.valueAt(hourly.getIsDay(), index);
        Double rainChance = WeatherFormatter.valueAt(
                hourly.getPrecipitationProbability(),
                index
        );

        TextView chip = new TextView(activity);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(widthDp),
                dp(88)
        );
        if (container.getChildCount() > 0) {
            params.setMarginStart(dp(8));
        }
        chip.setLayoutParams(params);
        chip.setBackgroundResource(R.drawable.bg_weather_chip);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(6), dp(7), dp(6), dp(7));
        chip.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        chip.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        chip.setText(String.format(
                Locale.getDefault(),
                "%s\n%s %s\nRain %s",
                time,
                WeatherFormatter.symbol(weatherCode, isDay),
                WeatherFormatter.temperature(temperature),
                WeatherFormatter.percent(rainChance)
        ));
        container.addView(chip);
    }

    private void renderDaily(@NonNull WeatherResponse response) {
        WeatherResponse.DailyWeather daily = response.getDaily();
        forecastDailyContainer.removeAllViews();

        if (daily == null || daily.getTime() == null || daily.getTime().isEmpty()) {
            addWaitingRow(forecastDailyContainer, "Waiting for daily forecast");
            return;
        }

        int count = Math.min(10, daily.getTime().size());
        for (int index = 0; index < count; index++) {
            String day = WeatherFormatter.dayLabel(
                    WeatherFormatter.valueAt(daily.getTime(), index),
                    index
            );
            Integer code = WeatherFormatter.valueAt(daily.getWeatherCode(), index);
            Double high = WeatherFormatter.valueAt(daily.getTemperature2mMax(), index);
            Double low = WeatherFormatter.valueAt(daily.getTemperature2mMin(), index);
            Double rainChance = WeatherFormatter.valueAt(
                    daily.getPrecipitationProbabilityMax(),
                    index
            );
            Double wind = WeatherFormatter.valueAt(daily.getWindSpeed10mMax(), index);

            LinearLayout dayContainer = new LinearLayout(activity);
            dayContainer.setOrientation(LinearLayout.VERTICAL);
            dayContainer.setPadding(dp(2), dp(4), dp(2), dp(4));
            dayContainer.setClickable(true);
            dayContainer.setFocusable(true);
            dayContainer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            TextView header = new TextView(activity);
            header.setMinHeight(dp(62));
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            header.setText(String.format(
                    Locale.getDefault(),
                    "%s   %s %s\nH %s · L %s · Rain %s · Wind %s",
                    day,
                    WeatherFormatter.symbol(code, null),
                    WeatherFormatter.condition(code),
                    WeatherFormatter.temperature(high),
                    WeatherFormatter.temperature(low),
                    WeatherFormatter.percent(rainChance),
                    WeatherFormatter.wind(wind)
            ));

            TextView details = new TextView(activity);
            details.setVisibility(View.GONE);
            details.setBackgroundResource(R.drawable.bg_weather_chip);
            details.setPadding(dp(12), dp(10), dp(12), dp(10));
            details.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_secondary));
            details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            details.setText(ForecastIntelligence.dailyDetails(daily, index));

            dayContainer.addView(header);
            dayContainer.addView(details);
            dayContainer.setOnClickListener(view -> {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                details.setVisibility(
                        details.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE
                );
            });

            forecastDailyContainer.addView(dayContainer);

            if (index < count - 1) {
                View divider = new View(activity);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)
                ));
                divider.setBackgroundColor(
                        ContextCompat.getColor(activity, R.color.weather_divider)
                );
                forecastDailyContainer.addView(divider);
            }
        }
    }

    private void renderWindSummary(
            WeatherResponse.CurrentWeather current,
            WeatherResponse.DailyWeather daily
    ) {
        if (current == null) {
            forecastWindSummary.setText(R.string.forecast_wind_waiting);
            return;
        }

        Double todayRainChance = daily == null
                ? null
                : WeatherFormatter.valueAt(daily.getPrecipitationProbabilityMax(), 0);
        Double wetHours = daily == null
                ? null
                : WeatherFormatter.valueAt(daily.getPrecipitationHours(), 0);

        String wetHoursText = wetHours == null
                ? "—"
                : String.format(Locale.getDefault(), "%.1f h", wetHours);

        forecastWindSummary.setText(String.format(
                Locale.getDefault(),
                "Wind %s %s · Gusts %s · Today rain chance %s · Wet hours %s",
                WeatherFormatter.wind(current.getWindSpeed10m()),
                WeatherFormatter.windDirection(current.getWindDirection10m()),
                WeatherFormatter.wind(current.getWindGusts10m()),
                WeatherFormatter.percent(todayRainChance),
                wetHoursText
        ));
    }

    private void renderSkyReality(
            @NonNull WeatherResponse response,
            double latitude,
            double longitude
    ) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            renderEmptySkyReality();
            return;
        }

        try {
            SkyRealityState sky = SkyRealityEngine.calculate(
                    response,
                    latitude,
                    longitude,
                    System.currentTimeMillis()
            );

            applySkyAtmosphere(sky);
            forecastSkyStageValue.setText(sky.getSkyStage());
            forecastSunPositionValue.setText(String.format(
                    Locale.getDefault(),
                    "%s · alt %.1f° · az %.0f°",
                    sky.getSunAltitude() >= -0.8 ? "visible" : "below horizon",
                    sky.getSunAltitude(),
                    sky.getSunAzimuth()
            ));
            forecastMoonPositionValue.setText(String.format(
                    Locale.getDefault(),
                    "%s · %.0f%% lit\n%s · alt %.1f° · az %.0f°",
                    sky.getMoonPhaseName(),
                    sky.getMoonIlluminationPercent(),
                    sky.getMoonAltitude() >= -0.8 ? "visible" : "below horizon",
                    sky.getMoonAltitude(),
                    sky.getMoonAzimuth()
            ));
            forecastStarVisibilityValue.setText(String.format(
                    Locale.getDefault(),
                    "%d%% · %s",
                    sky.getStarVisibilityPercent(),
                    starVisibilityLabel(sky.getStarVisibilityPercent())
            ));
            forecastSceneLightValue.setText(String.format(
                    Locale.getDefault(),
                    "%d%% · %s",
                    sky.getAmbientLightPercent(),
                    sky.getSkyStage()
            ));

            WeatherResponse.CurrentWeather current = response.getCurrent();
            String condition = current == null
                    ? "Weather"
                    : WeatherFormatter.condition(current.getWeatherCode());
            wallpaperPreviewCondition.setText(String.format(
                    Locale.getDefault(),
                    "%s · %s\nMoon %.0f%% · Stars %d%% · Light %d%%",
                    condition,
                    sky.getSkyStage(),
                    sky.getMoonIlluminationPercent(),
                    sky.getStarVisibilityPercent(),
                    sky.getAmbientLightPercent()
            ));
        } catch (RuntimeException exception) {
            renderEmptySkyReality();
        }
    }

    private void applySkyAtmosphere(@NonNull SkyRealityState sky) {
        int color;
        float alpha;
        String stage = sky.getSkyStage();
        float light = sky.getAmbientLightPercent() / 100f;

        if ("Daylight".equals(stage)) {
            color = R.color.weather_sky_blue;
            alpha = 0.07f + (0.10f * light);
        } else if ("Golden hour".equals(stage)) {
            color = R.color.weather_sun_warm;
            alpha = 0.14f;
        } else if ("Civil twilight".equals(stage)) {
            color = R.color.weather_violet;
            alpha = 0.11f;
        } else if ("Nautical twilight".equals(stage)) {
            color = R.color.weather_sky_blue;
            alpha = 0.075f;
        } else if ("Astronomical twilight".equals(stage)) {
            color = R.color.weather_violet;
            alpha = 0.055f;
        } else {
            color = R.color.weather_background_deep;
            alpha = 0.28f;
        }

        skyAtmosphereOverlay.setBackgroundColor(ContextCompat.getColor(activity, color));
        skyAtmosphereOverlay.animate().alpha(alpha).setDuration(700L).start();
    }

    private String starVisibilityLabel(int percent) {
        if (percent <= 5) {
            return "not visible";
        }
        if (percent <= 25) {
            return "low";
        }
        if (percent <= 55) {
            return "moderate";
        }
        if (percent <= 80) {
            return "good";
        }
        return "excellent";
    }

    private void renderEmptySkyReality() {
        forecastSkyStageValue.setText(R.string.metric_placeholder);
        forecastSunPositionValue.setText(R.string.metric_placeholder);
        forecastMoonPositionValue.setText(R.string.metric_placeholder);
        forecastStarVisibilityValue.setText(R.string.metric_placeholder);
        forecastSceneLightValue.setText(R.string.metric_placeholder);
        skyAtmosphereOverlay.setBackgroundColor(
                ContextCompat.getColor(activity, R.color.weather_sky_blue)
        );
        skyAtmosphereOverlay.animate().alpha(0.05f).setDuration(500L).start();
    }

    private void renderEmptyWeather() {
        applyHeroMode(DashboardIntelligence.HeroMode.CLOUDY);
        homeTemperature.setText(R.string.home_temperature_placeholder);
        homeCondition.setText(R.string.home_condition_waiting);
        homeFeelsLike.setText(R.string.home_feels_like);
        homeHighLow.setText(R.string.home_high_low);
        homeHumidityValue.setText(R.string.metric_placeholder);
        homeWindValue.setText(R.string.metric_placeholder);
        homeRainValue.setText(R.string.metric_placeholder);
        homeWeatherInsight.setText(R.string.dashboard_insight_waiting);
        homeSunValue.setText(R.string.metric_placeholder);
        homeDaylightValue.setText(R.string.metric_placeholder);
        homeUvValue.setText(R.string.metric_placeholder);
        homePressureValue.setText(R.string.metric_placeholder);
        homeVisibilityValue.setText(R.string.metric_placeholder);
        homeCloudValue.setText(R.string.metric_placeholder);
        homeDewPointValue.setText(R.string.metric_placeholder);
        homeGustValue.setText(R.string.metric_placeholder);
        homeRainChanceValue.setText(R.string.metric_placeholder);
        homeTenDaySummary.setText(R.string.quick_ten_day_sub);
        homeWallpaperSummary.setText(R.string.home_wallpaper_body);

        forecastSmartSummary.setText(R.string.forecast_smart_waiting);
        forecastCurrentSummary.setText(R.string.forecast_chart_waiting);
        forecastDailySummary.setText(R.string.forecast_daily_waiting);
        forecastWindSummary.setText(R.string.forecast_wind_waiting);
        forecastTemperatureChart.setData(Collections.emptyList(), Collections.emptyList());
        forecastRainChart.setData(Collections.emptyList(), Collections.emptyList());
        renderEmptySkyReality();

        wallpaperPreviewTemperature.setText(R.string.home_temperature_placeholder);
        wallpaperPreviewCondition.setText(R.string.wallpaper_preview_waiting);

        homeHourlyContainer.removeAllViews();
        forecastHourlyContainer.removeAllViews();
        forecastDailyContainer.removeAllViews();
        addWaitingChip(homeHourlyContainer, "Waiting for live weather");
        addWaitingChip(forecastHourlyContainer, "Waiting for live weather");
        addWaitingRow(forecastDailyContainer, "Waiting for live weather");
    }

    private void renderStatus(@NonNull WeatherUiState state) {
        String status;

        if (state.isLoading()) {
            status = activity.getString(R.string.home_sync_weather_loading);
        } else if (state.hasWeather()) {
            String updated = WeatherFormatter.updatedTime(state.getUpdatedAt());
            if (state.isFromCache() || state.getMessage() != null) {
                status = "Saved weather · updated " + updated + " · tap to refresh.";
            } else {
                status = "Live · updated " + updated + " · tap to refresh.";
            }
        } else if (state.getMessage() != null) {
            status = activity.getString(R.string.home_sync_weather_retry);
        } else {
            status = "Waiting for location and live weather.";
        }

        homeSyncStatus.setText(status);
        forecastStatus.setText(status);
    }

    private void addWaitingChip(@NonNull LinearLayout container, @NonNull String message) {
        TextView chip = new TextView(activity);
        chip.setLayoutParams(new LinearLayout.LayoutParams(dp(180), dp(72)));
        chip.setBackgroundResource(R.drawable.bg_weather_chip);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), dp(8), dp(12), dp(8));
        chip.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_secondary));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        chip.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        chip.setText(message);
        container.addView(chip);
    }

    private void addWaitingRow(@NonNull LinearLayout container, @NonNull String message) {
        TextView row = new TextView(activity);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.setPadding(0, dp(12), 0, dp(12));
        row.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_secondary));
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        row.setTypeface(Typeface.DEFAULT);
        row.setText(message);
        container.addView(row);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
