package com.tridev.liveweather.ui.phase7;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.tridev.liveweather.R;
import com.tridev.liveweather.data.remote.dto.AirQualityResponse;
import com.tridev.liveweather.domain.AirQualityUiState;
import com.tridev.liveweather.domain.CelestialIntelligence;
import com.tridev.liveweather.domain.WeatherUiState;
import com.tridev.liveweather.ui.air.AirQualityIntelligence;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.util.List;
import java.util.Locale;

public final class Phase7Renderer {

    private final Activity activity;
    private final LinearLayout airSection;
    private final TextView airAqiValue;
    private final TextView airCategory;
    private final TextView airPollutants;
    private final TextView airHaze;
    private final TextView airUv;
    private final TextView airTrend;
    private final TextView airStatus;
    private final LinearLayout airHourlyContainer;
    private final TextView airRefresh;

    private final LinearLayout celestialSection;
    private final TextView sunNow;
    private final TextView moonNow;
    private final TextView daylightProgress;
    private final TextView skyVisibility;
    private final LinearLayout lunarEvents;

    private Runnable refreshAirQualityAction;

    public Phase7Renderer(@NonNull Activity activity) {
        this.activity = activity;

        TextView cityActive = activity.findViewById(R.id.cityActiveLocation);
        LinearLayout cityCard = (LinearLayout) cityActive.getParent();
        LinearLayout moreRoot = (LinearLayout) cityCard.getParent();
        airSection = createCard(true);
        int cityIndex = moreRoot.indexOfChild(cityCard);
        moreRoot.addView(airSection, Math.min(cityIndex + 1, moreRoot.getChildCount()));

        TextView airTitle = sectionTitle("Air Quality Intelligence");
        airSection.addView(airTitle);
        airSection.addView(caption("CAMS model via Open-Meteo · model estimate, not a local sensor reading."));

        LinearLayout heroRow = horizontalRow();
        LinearLayout heroLeft = column(0, 1f);
        airAqiValue = bigValue("—");
        heroLeft.addView(captionAccent("US AQI"));
        heroLeft.addView(airAqiValue);
        airCategory = bodyLarge("Waiting for air-quality sync");
        heroLeft.addView(airCategory);
        heroRow.addView(heroLeft);
        airRefresh = actionChip("Refresh AQI");
        heroRow.addView(airRefresh, new LinearLayout.LayoutParams(dp(108), dp(44)));
        airSection.addView(heroRow);

        airPollutants = body("Pollutants will appear after sync.");
        airHaze = body("Haze / aerosol data waiting.");
        airUv = body("UV atmosphere comparison waiting.");
        airTrend = body("24-hour AQI trend waiting.");
        airStatus = caption("Waiting for active weather location.");
        addSpaced(airSection, airPollutants, 12);
        addSpaced(airSection, airHaze, 8);
        addSpaced(airSection, airUv, 8);
        addSpaced(airSection, airTrend, 8);

        airSection.addView(sectionTitleWithTop("Next 24 hours", 14));
        HorizontalScrollView airScroll = new HorizontalScrollView(activity);
        airScroll.setHorizontalScrollBarEnabled(false);
        airScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        airHourlyContainer = new LinearLayout(activity);
        airHourlyContainer.setOrientation(LinearLayout.HORIZONTAL);
        airScroll.addView(airHourlyContainer, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        airSection.addView(airScroll, marginTopParams(-1, -2, 8));
        airSection.addView(airStatus, marginTopParams(-1, -2, 10));

        View forecastLiveSky = activity.findViewById(R.id.forecastLiveSkyView);
        LinearLayout liveSkyCard = (LinearLayout) forecastLiveSky.getParent();
        LinearLayout forecastRoot = (LinearLayout) liveSkyCard.getParent();
        celestialSection = createCard(false);
        int liveSkyIndex = forecastRoot.indexOfChild(liveSkyCard);
        forecastRoot.addView(celestialSection, Math.min(liveSkyIndex + 1, forecastRoot.getChildCount()));

        celestialSection.addView(sectionTitle("Sun & Moon Intelligence"));
        celestialSection.addView(caption("Real observer-relative position, daylight progress and upcoming lunar quarter events."));
        sunNow = bodyLarge("Sun intelligence waiting.");
        moonNow = bodyLarge("Moon intelligence waiting.");
        daylightProgress = body("Daylight progress waiting.");
        skyVisibility = body("Sky visibility intelligence waiting.");
        addSpaced(celestialSection, sunNow, 12);
        addSpaced(celestialSection, moonNow, 8);
        addSpaced(celestialSection, daylightProgress, 8);
        addSpaced(celestialSection, skyVisibility, 8);
        celestialSection.addView(sectionTitleWithTop("Next lunar events", 14));
        lunarEvents = new LinearLayout(activity);
        lunarEvents.setOrientation(LinearLayout.VERTICAL);
        celestialSection.addView(lunarEvents, marginTopParams(-1, -2, 6));

        airRefresh.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (refreshAirQualityAction != null) {
                refreshAirQualityAction.run();
            }
        });
    }

    public void setRefreshAirQualityAction(Runnable action) {
        refreshAirQualityAction = action;
    }

    public void renderAirQuality(@NonNull AirQualityUiState state) {
        if (!state.hasData() || state.getData() == null || state.getData().getCurrent() == null) {
            airAqiValue.setText("—");
            airCategory.setText(state.isLoading() ? "Loading air quality…" : "Air quality unavailable");
            airPollutants.setText("Pollutants will appear after sync.");
            airHaze.setText("Haze / aerosol data waiting.");
            airUv.setText("UV atmosphere comparison waiting.");
            airTrend.setText("24-hour AQI trend waiting.");
            airStatus.setText(state.getMessage() == null ? "Waiting for active location." : state.getMessage());
            airHourlyContainer.removeAllViews();
            return;
        }

        AirQualityResponse response = state.getData();
        AirQualityResponse.CurrentAirQuality current = response.getCurrent();
        airAqiValue.setText(current.getUsAqi() == null ? "—" : Long.toString(Math.round(current.getUsAqi())));
        airCategory.setText(AirQualityIntelligence.usCategory(current.getUsAqi())
                + " · EU AQI "
                + (current.getEuropeanAqi() == null ? "—" : Math.round(current.getEuropeanAqi()))
                + " (" + AirQualityIntelligence.euCategory(current.getEuropeanAqi()) + ")");
        airPollutants.setText(AirQualityIntelligence.currentSummary(response) + "\n"
                + AirQualityIntelligence.pollutantLine(current));
        airHaze.setText(AirQualityIntelligence.hazeLine(current) + String.format(
                Locale.getDefault(),
                " · scene haze %.0f%%",
                AirQualityIntelligence.hazeIntensity(response) * 100d
        ));
        airUv.setText(AirQualityIntelligence.uvLine(current));
        airTrend.setText(AirQualityIntelligence.next24Hours(response));
        airStatus.setText((state.isFromCache() ? "Saved AQI" : "Live model AQI")
                + " · updated " + WeatherFormatter.updatedTime(state.getUpdatedAt())
                + (state.getMessage() == null ? "" : " · " + state.getMessage()));
        renderHourly(response);
    }

    public void renderCelestial(@NonNull WeatherUiState state) {
        if (!state.hasWeather() || state.getWeather() == null
                || Double.isNaN(state.getLatitude()) || Double.isNaN(state.getLongitude())) {
            sunNow.setText("Sun intelligence waiting.");
            moonNow.setText("Moon intelligence waiting.");
            daylightProgress.setText("Daylight progress waiting.");
            skyVisibility.setText("Sky visibility intelligence waiting.");
            lunarEvents.removeAllViews();
            return;
        }
        long now = System.currentTimeMillis();
        sunNow.setText(CelestialIntelligence.sunNow(
                state.getWeather(), state.getLatitude(), state.getLongitude(), now
        ));
        moonNow.setText(CelestialIntelligence.moonNow(
                state.getWeather(), state.getLatitude(), state.getLongitude(), now
        ));
        daylightProgress.setText(CelestialIntelligence.daylightProgress(state.getWeather(), now));
        skyVisibility.setText(CelestialIntelligence.visibilitySummary(
                state.getWeather(), state.getLatitude(), state.getLongitude(), now
        ));
        lunarEvents.removeAllViews();
        List<String> events = CelestialIntelligence.nextMoonQuarterEvents(state.getWeather(), now, 4);
        for (String event : events) {
            TextView row = body(event);
            row.setBackgroundResource(R.drawable.bg_weather_chip);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            lunarEvents.addView(row, marginTopParams(-1, -2, lunarEvents.getChildCount() == 0 ? 0 : 6));
        }
    }

    public void scrollToAirQuality() {
        View parent = (View) airSection.getParent();
        while (parent != null && !(parent instanceof NestedScrollView)) {
            android.view.ViewParent next = parent.getParent();
            parent = next instanceof View ? (View) next : null;
        }
        if (parent instanceof NestedScrollView) {
            NestedScrollView scroll = (NestedScrollView) parent;
            scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, airSection.getTop() - dp(12))));
        }
    }

    private void renderHourly(AirQualityResponse response) {
        airHourlyContainer.removeAllViews();
        AirQualityResponse.HourlyAirQuality hourly = response.getHourly();
        if (hourly == null || hourly.getTime() == null || hourly.getUsAqi() == null) return;
        int start = AirQualityIntelligence.findCurrentIndex(response);
        for (int step = 0; step < 8; step++) {
            int index = start + step * 3;
            if (index >= hourly.getTime().size() || index >= hourly.getUsAqi().size()) break;
            Double aqi = hourly.getUsAqi().get(index);
            String time = hourly.getTime().get(index);
            LinearLayout card = column(dp(112), 0f);
            card.setBackgroundResource(R.drawable.bg_weather_chip);
            card.setPadding(dp(10), dp(10), dp(10), dp(10));
            TextView t = caption(step == 0 ? "Now" : WeatherFormatter.hourLabel(time));
            TextView v = bodyLarge(aqi == null ? "—" : Long.toString(Math.round(aqi)));
            v.setTypeface(Typeface.DEFAULT_BOLD);
            TextView c = caption(AirQualityIntelligence.usCategory(aqi));
            card.addView(t);
            card.addView(v, marginTopParams(-1, -2, 4));
            card.addView(c, marginTopParams(-1, -2, 4));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (step > 0) p.setMarginStart(dp(8));
            airHourlyContainer.addView(card, p);
        }
    }

    private LinearLayout createCard(boolean accent) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundResource(accent ? R.drawable.bg_weather_card_accent : R.drawable.bg_weather_card_compact);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(marginTopParams(-1, -2, 12));
        return row;
    }

    private LinearLayout column(int width, float weight) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width == 0 ? 0 : width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                weight
        );
        column.setLayoutParams(params);
        return column;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        view.setTextSize(17f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView sectionTitleWithTop(String text, int topDp) {
        TextView view = sectionTitle(text);
        view.setLayoutParams(marginTopParams(-1, -2, topDp));
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_secondary));
        view.setTextSize(14f);
        view.setLineSpacing(0f, 1.12f);
        return view;
    }

    private TextView bodyLarge(String text) {
        TextView view = body(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        view.setTextSize(15f);
        return view;
    }

    private TextView caption(String text) {
        TextView view = body(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_tertiary));
        view.setTextSize(12f);
        return view;
    }

    private TextView captionAccent(String text) {
        TextView view = caption(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_aqua));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView bigValue(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        view.setTextSize(36f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView actionChip(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundResource(R.drawable.bg_weather_button_primary);
        view.setTextColor(ContextCompat.getColor(activity, R.color.white));
        view.setTextSize(13f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private void addSpaced(LinearLayout parent, View child, int topDp) {
        parent.addView(child, marginTopParams(-1, -2, topDp));
    }

    private LinearLayout.LayoutParams marginTopParams(int width, int height, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width == -1 ? ViewGroup.LayoutParams.MATCH_PARENT : width,
                height == -2 ? ViewGroup.LayoutParams.WRAP_CONTENT : height
        );
        params.topMargin = dp(topDp);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
