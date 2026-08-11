package com.tridev.liveweather.ui.alert;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.tridev.liveweather.R;
import com.tridev.liveweather.domain.alert.AlertUiState;
import com.tridev.liveweather.domain.alert.WeatherAlert;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.util.List;

public final class Phase8Renderer {

    private final Activity activity;
    private final LinearLayout homeAlertCard;
    private final TextView homeAlertBadge;
    private final TextView homeAlertTitle;
    private final TextView homeAlertMessage;

    private final LinearLayout alertsSection;
    private final TextView alertsLocation;
    private final TextView alertsStatus;
    private final TextView notificationsAction;
    private final LinearLayout alertsList;

    private Runnable openAlertsAction;
    private Runnable refreshAction;
    private Runnable notificationsActionCallback;

    public Phase8Renderer(@NonNull Activity activity) {
        this.activity = activity;

        View hero = activity.findViewById(R.id.homeHeroCard);
        LinearLayout homeRoot = (LinearLayout) hero.getParent();
        homeAlertCard = card(true);
        int heroIndex = homeRoot.indexOfChild(hero);
        homeRoot.addView(
                homeAlertCard,
                Math.min(heroIndex + 1, homeRoot.getChildCount()),
                top(-1, -2, 8)
        );
        homeAlertCard.setVisibility(View.GONE);

        homeAlertBadge = captionAccent("WEATHER ALERT");
        homeAlertTitle = title("Weather alert");
        homeAlertMessage = body("Alert details");
        homeAlertCard.addView(homeAlertBadge);
        homeAlertCard.addView(homeAlertTitle, top(-1, -2, 6));
        homeAlertCard.addView(homeAlertMessage, top(-1, -2, 5));
        homeAlertCard.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (openAlertsAction != null) openAlertsAction.run();
        });

        TextView cityActive = activity.findViewById(R.id.cityActiveLocation);
        LinearLayout cityCard = (LinearLayout) cityActive.getParent();
        LinearLayout moreRoot = (LinearLayout) cityCard.getParent();
        alertsSection = card(true);
        int cityIndex = moreRoot.indexOfChild(cityCard);
        moreRoot.addView(
                alertsSection,
                Math.min(cityIndex + 1, moreRoot.getChildCount()),
                top(-1, -2, 10)
        );

        alertsSection.addView(section("Weather Alerts Center"));
        alertsSection.addView(caption(
                "Official IMD CAP warnings + separately labelled Smart Risk signals. Official and model-derived alerts are never mixed as the same source."
        ), top(-1, -2, 4));

        alertsLocation = bodyLarge("Waiting for active location");
        alertsStatus = caption("Alert engine waiting for weather sync.");
        alertsSection.addView(alertsLocation, top(-1, -2, 12));
        alertsSection.addView(alertsStatus, top(-1, -2, 5));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        notificationsAction = actionChip("Enable alerts");
        TextView refresh = actionChip("Refresh alerts");
        actions.addView(notificationsAction, weight(1f, 0, 4));
        actions.addView(refresh, weight(1f, 4, 0));
        alertsSection.addView(actions, top(-1, -2, 12));

        alertsSection.addView(sectionWithTop("Active alerts", 16));
        alertsList = new LinearLayout(activity);
        alertsList.setOrientation(LinearLayout.VERTICAL);
        alertsSection.addView(alertsList, top(-1, -2, 8));

        notificationsAction.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (notificationsActionCallback != null) notificationsActionCallback.run();
        });
        refresh.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            if (refreshAction != null) refreshAction.run();
        });
    }

    public void setCallbacks(
            Runnable openAlertsAction,
            Runnable refreshAction,
            Runnable notificationsActionCallback
    ) {
        this.openAlertsAction = openAlertsAction;
        this.refreshAction = refreshAction;
        this.notificationsActionCallback = notificationsActionCallback;
    }

    public void setNotificationsEnabled(boolean enabled, boolean permissionGranted) {
        notificationsAction.setText(enabled && permissionGranted
                ? "Alerts: On"
                : "Enable alerts");
    }

    public void render(@NonNull AlertUiState state) {
        if (state.getLocation() != null) {
            alertsLocation.setText("Alert location · " + state.getLocation().displayLabel());
        } else {
            alertsLocation.setText("Resolving alert district…");
        }

        String status = state.isLoading() ? "Checking alerts…" : "Alerts checked";
        if (state.getUpdatedAt() > 0L) {
            status += " · " + WeatherFormatter.updatedTime(state.getUpdatedAt());
        }
        if (state.getMessage() != null) status += "\n" + state.getMessage();
        alertsStatus.setText(status);

        List<WeatherAlert> alerts = state.getAlerts();
        alertsList.removeAllViews();
        if (alerts.isEmpty()) {
            TextView empty = body("No active weather alert is detected for this location right now.");
            empty.setTextColor(ContextCompat.getColor(activity, R.color.weather_success));
            alertsList.addView(empty);
            homeAlertCard.setVisibility(View.GONE);
            return;
        }

        for (WeatherAlert alert : alerts) {
            alertsList.addView(alertRow(alert), top(-1, -2, alertsList.getChildCount() == 0 ? 0 : 8));
        }

        WeatherAlert highest = state.highestAlert();
        if (highest != null) {
            homeAlertCard.setVisibility(View.VISIBLE);
            homeAlertBadge.setText(highest.sourceLabel() + " · " + severityName(highest.getSeverity()));
            homeAlertBadge.setTextColor(severityColor(highest.getSeverity()));
            homeAlertTitle.setText(highest.getTitle());
            homeAlertMessage.setText(highest.getMessage()
                    + (highest.getValidLabel() == null ? "" : "\n" + highest.getValidLabel()));
        }
    }

    public void scrollToAlerts() {
        View parent = (View) alertsSection.getParent();
        while (parent != null && !(parent instanceof NestedScrollView)) {
            android.view.ViewParent next = parent.getParent();
            parent = next instanceof View ? (View) next : null;
        }
        if (parent instanceof NestedScrollView) {
            NestedScrollView scroll = (NestedScrollView) parent;
            scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, alertsSection.getTop() - dp(12))));
        }
    }

    private View alertRow(WeatherAlert alert) {
        LinearLayout row = card(false);
        TextView badge = captionAccent(alert.sourceLabel() + " · " + severityName(alert.getSeverity()));
        badge.setTextColor(severityColor(alert.getSeverity()));
        row.addView(badge);
        row.addView(title(alert.getTitle()), top(-1, -2, 5));
        row.addView(body(alert.getMessage()), top(-1, -2, 5));
        String meta = "";
        if (alert.getLocationLabel() != null) meta += alert.getLocationLabel();
        if (alert.getValidLabel() != null) meta += (meta.isEmpty() ? "" : " · ") + alert.getValidLabel();
        if (!meta.isEmpty()) row.addView(caption(meta), top(-1, -2, 7));
        return row;
    }

    private int severityColor(WeatherAlert.Severity severity) {
        switch (severity) {
            case RED: return ContextCompat.getColor(activity, R.color.weather_danger);
            case ORANGE: return ContextCompat.getColor(activity, R.color.weather_warning);
            case YELLOW: return ContextCompat.getColor(activity, R.color.weather_sun_warm);
            default: return ContextCompat.getColor(activity, R.color.weather_aqua);
        }
    }

    private String severityName(WeatherAlert.Severity severity) {
        switch (severity) {
            case RED: return "WARNING";
            case ORANGE: return "ALERT";
            case YELLOW: return "WATCH";
            default: return "INFO";
        }
    }

    private LinearLayout card(boolean accent) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackgroundResource(accent
                ? R.drawable.bg_weather_card_accent
                : R.drawable.bg_weather_card_compact);
        return card;
    }

    private TextView section(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        view.setTextSize(17);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView sectionWithTop(String text, int topDp) {
        TextView view = section(text);
        view.setPadding(0, dp(topDp), 0, 0);
        return view;
    }

    private TextView title(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        view.setTextSize(16);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView bodyLarge(String text) {
        TextView view = body(text);
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_secondary));
        view.setTextSize(14);
        view.setLineSpacing(0f, 1.08f);
        return view;
    }

    private TextView caption(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_tertiary));
        view.setTextSize(12);
        return view;
    }

    private TextView captionAccent(String text) {
        TextView view = caption(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_aqua));
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLetterSpacing(0.08f);
        return view;
    }

    private TextView actionChip(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        view.setTextSize(12);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundResource(R.drawable.bg_weather_chip);
        view.setMinHeight(dp(44));
        view.setMaxLines(1);
        view.setPadding(dp(8), dp(7), dp(8), dp(7));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private LinearLayout.LayoutParams top(int width, int height, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width == -1 ? ViewGroup.LayoutParams.MATCH_PARENT : width,
                height == -2 ? ViewGroup.LayoutParams.WRAP_CONTENT : height
        );
        params.topMargin = dp(topDp);
        return params;
    }

    private LinearLayout.LayoutParams weight(float weight, int start, int end) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), weight);
        params.setMarginStart(dp(start));
        params.setMarginEnd(dp(end));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
