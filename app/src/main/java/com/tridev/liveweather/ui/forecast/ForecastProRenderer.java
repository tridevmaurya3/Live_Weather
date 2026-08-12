package com.tridev.liveweather.ui.forecast;

import android.app.Activity;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.tridev.liveweather.R;
import com.tridev.liveweather.data.remote.dto.WeatherResponse;
import com.tridev.liveweather.domain.CelestialDayState;
import com.tridev.liveweather.domain.CelestialForecastEngine;
import com.tridev.liveweather.domain.WeatherUiState;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Phase 19 interactive Forecast Pro presentation layer.
 *
 * It intentionally overlays the existing forecast renderer instead of
 * rewriting Home/Wallpaper rendering. One selected-hour state is shared by the
 * hourly strip, rain timeline and both charts.
 */
public final class ForecastProRenderer {

    private static final int HOURS_VISIBLE = 24;
    private static final int RAIN_TIMELINE_HOURS = 12;

    private final Activity activity;
    private final TextView forecastCurrentSummary;
    private final TextView forecastDailySummary;
    private final TextView forecastStatus;
    private final LinearLayout forecastHourlyContainer;
    private final LinearLayout forecastDailyContainer;
    private final ForecastChartView temperatureChart;
    private final ForecastChartView rainChart;
    private final LinearLayout rainTimelineContainer;

    @Nullable private WeatherResponse currentResponse;
    private int chartStartIndex;
    private int selectedHourlyIndex = -1;
    private int expandedDayIndex = 0;
    private double currentLatitude = Double.NaN;
    private double currentLongitude = Double.NaN;

    public ForecastProRenderer(@NonNull Activity activity) {
        this.activity = activity;
        forecastCurrentSummary = activity.findViewById(R.id.forecastCurrentSummary);
        forecastDailySummary = activity.findViewById(R.id.forecastDailySummary);
        forecastStatus = activity.findViewById(R.id.forecastStatus);
        forecastHourlyContainer = activity.findViewById(R.id.forecastHourlyContainer);
        forecastDailyContainer = activity.findViewById(R.id.forecastDailyContainer);
        temperatureChart = activity.findViewById(R.id.forecastTemperatureChart);
        rainChart = activity.findViewById(R.id.forecastRainChart);
        rainTimelineContainer = installRainTimeline();

        temperatureChart.setOnPointSelectedListener(this::selectRelativeChartHour);
        rainChart.setOnPointSelectedListener(this::selectRelativeChartHour);
    }

    public void render(@NonNull WeatherUiState state) {
        if (!state.hasWeather() || state.getWeather() == null) {
            currentResponse = null;
            renderNoData(state);
            return;
        }

        currentResponse = state.getWeather();
        currentLatitude = state.getLatitude();
        currentLongitude = state.getLongitude();
        if (Double.isNaN(currentLatitude) && currentResponse.getLatitude() != null) {
            currentLatitude = currentResponse.getLatitude();
        }
        if (Double.isNaN(currentLongitude) && currentResponse.getLongitude() != null) {
            currentLongitude = currentResponse.getLongitude();
        }

        WeatherResponse.HourlyWeather hourly = currentResponse.getHourly();
        chartStartIndex = WeatherFormatter.findCurrentHourlyIndex(currentResponse);
        if (hourly == null || hourly.getTime() == null || hourly.getTime().isEmpty()) {
            selectedHourlyIndex = -1;
        } else {
            int end = Math.min(hourly.getTime().size(), chartStartIndex + HOURS_VISIBLE);
            if (selectedHourlyIndex < chartStartIndex || selectedHourlyIndex >= end) {
                selectedHourlyIndex = chartStartIndex;
            }
        }

        renderStatus(state);
        renderSelectedHour();
        renderHourlyStrip();
        renderRainTimeline();
        renderDailyPro();
        applyChartSelection();
    }

    private void renderSelectedHour() {
        if (currentResponse == null || currentResponse.getHourly() == null || selectedHourlyIndex < 0) {
            forecastCurrentSummary.setText("Hourly detail will appear after forecast data is available.");
            return;
        }
        WeatherResponse.HourlyWeather hourly = currentResponse.getHourly();
        boolean isNow = selectedHourlyIndex == chartStartIndex;
        forecastCurrentSummary.setText(
                ForecastIntelligence.hourlyDetails(hourly, selectedHourlyIndex, isNow)
        );
        forecastCurrentSummary.setContentDescription(
                "Selected hourly forecast. " + forecastCurrentSummary.getText()
        );
    }

    private void renderHourlyStrip() {
        forecastHourlyContainer.removeAllViews();
        if (currentResponse == null || currentResponse.getHourly() == null
                || currentResponse.getHourly().getTime() == null
                || currentResponse.getHourly().getTime().isEmpty()) {
            addWaitingChip(forecastHourlyContainer, "No hourly forecast available");
            return;
        }

        WeatherResponse.HourlyWeather hourly = currentResponse.getHourly();
        int start = chartStartIndex;
        int end = Math.min(hourly.getTime().size(), start + HOURS_VISIBLE);
        for (int index = start; index < end; index++) {
            final int selected = index;
            boolean isNow = index == start;
            String time = isNow
                    ? "Now"
                    : WeatherFormatter.hourLabel(WeatherFormatter.valueAt(hourly.getTime(), index));
            Integer code = WeatherFormatter.valueAt(hourly.getWeatherCode(), index);
            Integer isDay = WeatherFormatter.valueAt(hourly.getIsDay(), index);
            Double temperature = WeatherFormatter.valueAt(hourly.getTemperature2m(), index);
            Double probability = WeatherFormatter.valueAt(hourly.getPrecipitationProbability(), index);
            Double gust = WeatherFormatter.valueAt(hourly.getWindGusts10m(), index);

            TextView chip = new TextView(activity);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(96), dp(106));
            if (forecastHourlyContainer.getChildCount() > 0) params.setMarginStart(dp(8));
            chip.setLayoutParams(params);
            chip.setBackgroundResource(index == selectedHourlyIndex
                    ? R.drawable.bg_weather_card_accent
                    : R.drawable.bg_weather_chip);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(6), dp(7), dp(6), dp(7));
            chip.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            chip.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            chip.setText(String.format(
                    Locale.getDefault(),
                    "%s\n%s %s\nRisk %s\nGust %s",
                    time,
                    WeatherFormatter.symbol(code, isDay),
                    WeatherFormatter.temperature(temperature),
                    WeatherFormatter.percent(probability),
                    WeatherFormatter.wind(gust)
            ));
            chip.setContentDescription("Forecast " + chip.getText().toString().replace('\n', ' '));
            chip.setOnClickListener(view -> {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                selectAbsoluteHour(selected);
            });
            forecastHourlyContainer.addView(chip);
        }
    }

    private void renderRainTimeline() {
        rainTimelineContainer.removeAllViews();
        if (currentResponse == null || currentResponse.getHourly() == null
                || currentResponse.getHourly().getTime() == null
                || currentResponse.getHourly().getTime().isEmpty()) {
            addWaitingChip(rainTimelineContainer, "Rain-risk timeline unavailable");
            return;
        }

        WeatherResponse.HourlyWeather hourly = currentResponse.getHourly();
        int start = chartStartIndex;
        int end = Math.min(hourly.getTime().size(), start + RAIN_TIMELINE_HOURS);
        for (int index = start; index < end; index++) {
            final int selected = index;
            boolean isNow = index == start;
            String time = isNow
                    ? "Now"
                    : WeatherFormatter.hourLabel(WeatherFormatter.valueAt(hourly.getTime(), index));
            Double probability = WeatherFormatter.valueAt(hourly.getPrecipitationProbability(), index);
            Double amount = WeatherFormatter.valueAt(hourly.getPrecipitation(), index);
            Integer code = WeatherFormatter.valueAt(hourly.getWeatherCode(), index);
            double chance = probability == null ? 0d : probability;

            TextView chip = new TextView(activity);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(92), dp(82));
            if (rainTimelineContainer.getChildCount() > 0) params.setMarginStart(dp(7));
            chip.setLayoutParams(params);
            chip.setBackgroundResource(index == selectedHourlyIndex || chance >= 60d
                    ? R.drawable.bg_weather_card_accent
                    : R.drawable.bg_weather_chip);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(5), dp(6), dp(5), dp(6));
            chip.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            chip.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            chip.setText(String.format(
                    Locale.getDefault(),
                    "%s  %s\nForecast %s\n%s",
                    time,
                    WeatherFormatter.symbol(code, WeatherFormatter.valueAt(hourly.getIsDay(), index)),
                    WeatherFormatter.percent(probability),
                    WeatherFormatter.precipitation(amount)
            ));
            chip.setContentDescription(
                    "Forecast rain risk " + time + ", probability "
                            + WeatherFormatter.percent(probability) + ", model precipitation "
                            + WeatherFormatter.precipitation(amount)
            );
            chip.setOnClickListener(view -> {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                selectAbsoluteHour(selected);
            });
            rainTimelineContainer.addView(chip);
        }
    }

    private void renderDailyPro() {
        forecastDailyContainer.removeAllViews();
        if (currentResponse == null || currentResponse.getDaily() == null
                || currentResponse.getDaily().getTime() == null
                || currentResponse.getDaily().getTime().isEmpty()) {
            forecastDailySummary.setText("No daily forecast is available yet.");
            addWaitingRow(forecastDailyContainer, "Daily forecast unavailable");
            return;
        }

        WeatherResponse.DailyWeather daily = currentResponse.getDaily();
        int count = Math.min(10, daily.getTime().size());
        List<CelestialDayState> celestial = (!Double.isNaN(currentLatitude) && !Double.isNaN(currentLongitude))
                ? CelestialForecastEngine.build(
                        currentResponse,
                        currentLatitude,
                        currentLongitude,
                        count,
                        System.currentTimeMillis()
                )
                : Collections.emptyList();

        forecastDailySummary.setText(ForecastIntelligence.tenDayOverview(currentResponse));
        if (expandedDayIndex >= count) expandedDayIndex = 0;

        for (int index = 0; index < count; index++) {
            final int selectedDay = index;
            Integer code = WeatherFormatter.valueAt(daily.getWeatherCode(), index);
            Double high = WeatherFormatter.valueAt(daily.getTemperature2mMax(), index);
            Double low = WeatherFormatter.valueAt(daily.getTemperature2mMin(), index);
            Double probability = WeatherFormatter.valueAt(daily.getPrecipitationProbabilityMax(), index);
            Double uv = WeatherFormatter.valueAt(daily.getUvIndexMax(), index);
            String day = WeatherFormatter.dayLabel(
                    WeatherFormatter.valueAt(daily.getTime(), index), index
            );
            boolean expanded = index == expandedDayIndex;

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            if (index > 0) cardParams.topMargin = dp(7);
            card.setLayoutParams(cardParams);
            card.setBackgroundResource(expanded
                    ? R.drawable.bg_weather_card_accent
                    : R.drawable.bg_weather_chip);
            card.setClickable(true);
            card.setFocusable(true);

            TextView header = new TextView(activity);
            header.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            header.setPadding(dp(2), dp(3), dp(2), dp(3));
            header.setText(String.format(
                    Locale.getDefault(),
                    "%s  %s %s  %s\nH %s · L %s · Forecast precip %s · UV %s",
                    expanded ? "▾" : "▸",
                    WeatherFormatter.symbol(code, null),
                    day,
                    WeatherFormatter.condition(code),
                    WeatherFormatter.temperature(high),
                    WeatherFormatter.temperature(low),
                    WeatherFormatter.percent(probability),
                    uv == null ? "—" : String.format(Locale.getDefault(), "%.1f", uv)
            ));

            TextView details = new TextView(activity);
            details.setVisibility(expanded ? View.VISIBLE : View.GONE);
            details.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_secondary));
            details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            details.setPadding(dp(2), dp(9), dp(2), dp(4));
            String detailText = ForecastIntelligence.dailyDetails(daily, index);
            if (index < celestial.size()) {
                CelestialDayState sky = celestial.get(index);
                detailText += String.format(
                        Locale.getDefault(),
                        "\nMoon %s · %.0f%% illuminated · Moonrise %s · Moonset %s",
                        sky.getPhaseName(),
                        sky.getIlluminationPercent(),
                        sky.getMoonrise(),
                        sky.getMoonset()
                );
            }
            details.setText(detailText);

            card.addView(header);
            card.addView(details);
            card.setContentDescription(
                    day + " forecast. " + WeatherFormatter.condition(code)
                            + ". Tap to " + (expanded ? "collapse" : "expand") + " details."
            );
            card.setOnClickListener(view -> {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                expandedDayIndex = expandedDayIndex == selectedDay ? -1 : selectedDay;
                renderDailyPro();
            });
            forecastDailyContainer.addView(card);
        }
    }

    private void selectRelativeChartHour(int relativeIndex) {
        selectAbsoluteHour(chartStartIndex + relativeIndex);
    }

    private void selectAbsoluteHour(int absoluteIndex) {
        if (currentResponse == null || currentResponse.getHourly() == null
                || currentResponse.getHourly().getTime() == null) return;
        int end = Math.min(
                currentResponse.getHourly().getTime().size(),
                chartStartIndex + HOURS_VISIBLE
        );
        if (absoluteIndex < chartStartIndex || absoluteIndex >= end) return;
        selectedHourlyIndex = absoluteIndex;
        renderSelectedHour();
        renderHourlyStrip();
        renderRainTimeline();
        applyChartSelection();
    }

    private void applyChartSelection() {
        int relative = selectedHourlyIndex < chartStartIndex
                ? -1
                : selectedHourlyIndex - chartStartIndex;
        temperatureChart.setSelectedIndex(relative);
        rainChart.setSelectedIndex(relative);
    }

    private void renderStatus(@NonNull WeatherUiState state) {
        String updated = WeatherFormatter.updatedTime(state.getUpdatedAt());
        long ageMinutes = state.getUpdatedAt() <= 0L
                ? Long.MAX_VALUE
                : Math.max(0L, (System.currentTimeMillis() - state.getUpdatedAt()) / 60_000L);

        if (state.isLoading()) {
            forecastStatus.setText("Refreshing forecast · showing available data · updated " + updated + ".");
        } else if (state.isFromCache()) {
            forecastStatus.setText(
                    (ageMinutes > 180L ? "Stale saved forecast" : "Saved/offline forecast")
                            + " · updated " + updated + " · tap to refresh."
            );
        } else if (state.getMessage() != null) {
            forecastStatus.setText("Forecast refresh issue · showing available data · updated " + updated + ".");
        } else {
            forecastStatus.setText("Live forecast · updated " + updated + " · tap to refresh.");
        }
    }

    private void renderNoData(@NonNull WeatherUiState state) {
        forecastHourlyContainer.removeAllViews();
        rainTimelineContainer.removeAllViews();
        forecastDailyContainer.removeAllViews();
        selectedHourlyIndex = -1;
        chartStartIndex = 0;
        temperatureChart.setSelectedIndex(-1);
        rainChart.setSelectedIndex(-1);
        temperatureChart.setData(Collections.emptyList(), Collections.emptyList());
        rainChart.setData(Collections.emptyList(), Collections.emptyList());

        if (state.isLoading()) {
            forecastCurrentSummary.setText("Loading hourly forecast…");
            forecastDailySummary.setText("Loading daily forecast…");
            forecastStatus.setText("Loading forecast…");
            temperatureChart.setEmptyMessage("Loading temperature forecast…");
            rainChart.setEmptyMessage("Loading precipitation forecast…");
            addWaitingChip(forecastHourlyContainer, "Loading hourly forecast…");
            addWaitingChip(rainTimelineContainer, "Loading rain-risk timeline…");
            addWaitingRow(forecastDailyContainer, "Loading daily forecast…");
        } else if (state.getMessage() != null) {
            forecastCurrentSummary.setText("Forecast unavailable. Tap the status line to retry.");
            forecastDailySummary.setText("No saved daily forecast is available.");
            forecastStatus.setText("Forecast unavailable · tap to retry.");
            temperatureChart.setEmptyMessage("Forecast unavailable");
            rainChart.setEmptyMessage("Forecast unavailable");
            addWaitingChip(forecastHourlyContainer, "Forecast unavailable");
            addWaitingChip(rainTimelineContainer, "Forecast unavailable");
            addWaitingRow(forecastDailyContainer, "Forecast unavailable · refresh to retry");
        } else {
            forecastCurrentSummary.setText("Waiting for location and forecast data.");
            forecastDailySummary.setText("Waiting for daily forecast data.");
            forecastStatus.setText("Waiting for location and forecast data.");
            temperatureChart.setEmptyMessage("Waiting for forecast data");
            rainChart.setEmptyMessage("Waiting for forecast data");
            addWaitingChip(forecastHourlyContainer, "Waiting for hourly forecast");
            addWaitingChip(rainTimelineContainer, "Waiting for rain-risk timeline");
            addWaitingRow(forecastDailyContainer, "Waiting for daily forecast");
        }
    }

    @NonNull
    private LinearLayout installRainTimeline() {
        View parent = (View) forecastHourlyContainer.getParent();
        if (!(parent instanceof HorizontalScrollView) || !(parent.getParent() instanceof LinearLayout)) {
            return forecastHourlyContainer;
        }
        HorizontalScrollView hourlyScroll = (HorizontalScrollView) parent;
        LinearLayout card = (LinearLayout) hourlyScroll.getParent();

        TextView title = new TextView(activity);
        title.setText("12-hour forecast rain-risk timeline");
        title.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(14);
        title.setLayoutParams(titleParams);

        TextView hint = new TextView(activity);
        hint.setText("Probability and model amount are forecast signals, not proof that rain is falling now.");
        hint.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_tertiary));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        hintParams.topMargin = dp(3);
        hint.setLayoutParams(hintParams);

        HorizontalScrollView rainScroll = new HorizontalScrollView(activity);
        rainScroll.setHorizontalScrollBarEnabled(false);
        rainScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rainScroll.setClipToPadding(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        scrollParams.topMargin = dp(8);
        rainScroll.setLayoutParams(scrollParams);

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        rainScroll.addView(container);

        int hourlyIndex = card.indexOfChild(hourlyScroll);
        card.addView(title, hourlyIndex + 1);
        card.addView(hint, hourlyIndex + 2);
        card.addView(rainScroll, hourlyIndex + 3);
        return container;
    }

    private void addWaitingChip(@NonNull LinearLayout container, @NonNull String message) {
        TextView chip = new TextView(activity);
        chip.setLayoutParams(new LinearLayout.LayoutParams(dp(190), dp(72)));
        chip.setBackgroundResource(R.drawable.bg_weather_chip);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(8), dp(10), dp(8));
        chip.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_secondary));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
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
        row.setPadding(dp(4), dp(12), dp(4), dp(12));
        row.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_secondary));
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        row.setText(message);
        container.addView(row);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
