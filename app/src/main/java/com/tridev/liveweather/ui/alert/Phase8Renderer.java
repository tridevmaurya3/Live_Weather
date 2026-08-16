package com.tridev.liveweather.ui.alert;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

import com.tridev.liveweather.R;
import com.tridev.liveweather.data.local.AlertPreferences;
import com.tridev.liveweather.domain.alert.AlertTruthPolicy;
import com.tridev.liveweather.domain.alert.AlertUiState;
import com.tridev.liveweather.domain.alert.WeatherAlert;
import com.tridev.liveweather.notification.AlertNotificationManager;
import com.tridev.liveweather.ui.weather.WeatherFormatter;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class Phase8Renderer {

    private final Activity activity;
    private final AlertPreferences alertPreferences;
    private final AlertNotificationManager notificationManager;

    private final LinearLayout homeAlertCard;
    private final TextView homeAlertBadge;
    private final TextView homeAlertTitle;
    private final TextView homeAlertMessage;

    private final LinearLayout alertsSection;
    private final TextView alertsLocation;
    private final TextView alertsStatus;
    private final TextView notificationsAction;
    private final TextView officialFilterAction;
    private final TextView smartRiskFilterAction;
    private final TextView severityFilterAction;
    private final TextView filterSummary;
    private final TextView officialNotificationAction;
    private final TextView smartNotificationAction;
    private final TextView systemNotificationSettingsAction;
    private final TextView notificationStatus;
    private final LinearLayout alertsList;

    private Runnable openAlertsAction;
    private Runnable refreshAction;
    private Runnable notificationsActionCallback;
    private AlertUiState lastState;

    public Phase8Renderer(@NonNull Activity activity) {
        this.activity = activity;
        alertPreferences = new AlertPreferences(activity);
        notificationManager = new AlertNotificationManager(activity);

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
                "Official IMD warning data and app-derived Smart Risk signals stay separate. Saved/stale official data is labelled and is never presented as a live all-clear."
        ), top(-1, -2, 4));

        alertsLocation = bodyLarge("Waiting for active location");
        alertsStatus = caption("Alert engine waiting for weather sync.");
        alertsSection.addView(alertsLocation, top(-1, -2, 12));
        alertsSection.addView(alertsStatus, top(-1, -2, 5));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        notificationsAction = actionChip("Enable notifications");
        TextView refresh = actionChip("Refresh alerts");
        actions.addView(notificationsAction, weight(1f, 0, 4));
        actions.addView(refresh, weight(1f, 4, 0));
        alertsSection.addView(actions, top(-1, -2, 12));

        alertsSection.addView(sectionWithTop("Alert preferences", 14));
        LinearLayout sourceFilters = new LinearLayout(activity);
        sourceFilters.setOrientation(LinearLayout.HORIZONTAL);
        officialFilterAction = actionChip("Official: On");
        smartRiskFilterAction = actionChip("Smart Risk: On");
        sourceFilters.addView(officialFilterAction, weight(1f, 0, 4));
        sourceFilters.addView(smartRiskFilterAction, weight(1f, 4, 0));
        alertsSection.addView(sourceFilters, top(-1, -2, 8));

        severityFilterAction = actionChip("Minimum severity: Yellow+");
        alertsSection.addView(severityFilterAction, top(-1, -2, 8));
        filterSummary = caption("");
        alertsSection.addView(filterSummary, top(-1, -2, 5));

        alertsSection.addView(sectionWithTop("Notification delivery", 14));
        LinearLayout notificationSources = new LinearLayout(activity);
        notificationSources.setOrientation(LinearLayout.HORIZONTAL);
        officialNotificationAction = actionChip("Notify Official: On");
        smartNotificationAction = actionChip("Notify Smart: On");
        notificationSources.addView(officialNotificationAction, weight(1f, 0, 4));
        notificationSources.addView(smartNotificationAction, weight(1f, 4, 0));
        alertsSection.addView(notificationSources, top(-1, -2, 8));

        systemNotificationSettingsAction = actionChip("Android notification settings");
        alertsSection.addView(systemNotificationSettingsAction, top(-1, -2, 8));
        notificationStatus = caption("");
        alertsSection.addView(notificationStatus, top(-1, -2, 5));

        alertsSection.addView(sectionWithTop("Active alerts", 14));
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
        officialFilterAction.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            alertPreferences.setOfficialAlertsEnabled(!alertPreferences.isOfficialAlertsEnabled());
            updateFilterControls();
            updateNotificationControls();
            rerenderLastState();
        });
        smartRiskFilterAction.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            alertPreferences.setSmartRiskEnabled(!alertPreferences.isSmartRiskEnabled());
            updateFilterControls();
            updateNotificationControls();
            rerenderLastState();
        });
        severityFilterAction.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            alertPreferences.cycleMinimumSeverity();
            updateFilterControls();
            updateNotificationControls();
            rerenderLastState();
        });
        officialNotificationAction.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            alertPreferences.setOfficialNotificationsEnabled(
                    !alertPreferences.isOfficialNotificationsEnabled()
            );
            updateNotificationControls();
        });
        smartNotificationAction.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            alertPreferences.setSmartNotificationsEnabled(
                    !alertPreferences.isSmartNotificationsEnabled()
            );
            updateNotificationControls();
        });
        systemNotificationSettingsAction.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            openSystemNotificationSettings();
        });

        updateFilterControls();
        updateNotificationControls();
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
        updateNotificationControls();
    }

    public void render(@NonNull AlertUiState state) {
        lastState = state;
        updateFilterControls();
        updateNotificationControls();

        if (state.getLocation() != null) {
            alertsLocation.setText("Alert location · " + state.getLocation().displayLabel());
        } else {
            alertsLocation.setText("Resolving alert district…");
        }

        long now = System.currentTimeMillis();
        String status = state.isLoading() ? "Checking alerts…" : "Alerts checked";
        if (state.getCheckedAt() > 0L) {
            status += " · " + WeatherFormatter.updatedTime(state.getCheckedAt());
        }
        status += "\n" + state.officialDeliveryLabel(now);
        if (state.getMessage() != null) status += "\n" + state.getMessage();
        alertsStatus.setText(status);
        boolean warningStatus = state.isOfficialStale(now)
                || state.getOfficialDelivery() == AlertTruthPolicy.OfficialDelivery.UNAVAILABLE;
        alertsStatus.setTextColor(ContextCompat.getColor(
                activity,
                warningStatus ? R.color.weather_warning : R.color.weather_text_tertiary
        ));

        List<WeatherAlert> allAlerts = state.getAlerts();
        List<WeatherAlert> visibleAlerts = visibleAlerts(allAlerts);
        alertsList.removeAllViews();
        if (visibleAlerts.isEmpty()) {
            TextView empty;
            if (!allAlerts.isEmpty()) {
                empty = body(
                        "No alerts match the current source/severity filters. Source status above remains active; change filters to show hidden alerts."
                );
                empty.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_secondary));
            } else {
                empty = body(emptyStateMessage(state, now));
                boolean confirmedEmpty = !state.isLoading() && state.canConfirmNoOfficialMatch(now);
                empty.setTextColor(ContextCompat.getColor(
                        activity,
                        confirmedEmpty ? R.color.weather_success : R.color.weather_warning
                ));
            }
            alertsList.addView(empty);
            homeAlertCard.setVisibility(View.GONE);
            return;
        }

        for (WeatherAlert alert : visibleAlerts) {
            alertsList.addView(
                    alertRow(alert, state, now),
                    top(-1, -2, alertsList.getChildCount() == 0 ? 0 : 8)
            );
        }

        WeatherAlert highest = highestAlert(visibleAlerts);
        if (highest != null) {
            homeAlertCard.setVisibility(View.VISIBLE);
            boolean staleOfficial = highest.isOfficial() && state.isOfficialStale(now);
            homeAlertBadge.setText((staleOfficial ? "SAVED OFFICIAL" : highest.sourceLabel())
                    + " · " + severityName(highest.getSeverity()));
            homeAlertBadge.setTextColor(severityColor(highest.getSeverity()));
            homeAlertTitle.setText(highest.getTitle());
            String message = highest.getMessage()
                    + (highest.getValidLabel() == null ? "" : "\n" + highest.getValidLabel());
            if (staleOfficial) {
                message += "\nSaved official warning shown as fallback; source refresh is stale.";
            }
            homeAlertMessage.setText(message);
        }
    }

    @NonNull
    private List<WeatherAlert> visibleAlerts(@NonNull List<WeatherAlert> alerts) {
        List<WeatherAlert> visible = new ArrayList<>();
        for (WeatherAlert alert : alerts) {
            if (alert != null && alertPreferences.shouldShow(alert)) {
                visible.add(alert);
            }
        }
        return visible;
    }

    @Nullable
    private WeatherAlert highestAlert(@NonNull List<WeatherAlert> alerts) {
        WeatherAlert highest = null;
        for (WeatherAlert alert : alerts) {
            if (highest == null || severityRank(alert.getSeverity()) > severityRank(highest.getSeverity())) {
                highest = alert;
            }
        }
        return highest;
    }

    @NonNull
    private String emptyStateMessage(@NonNull AlertUiState state, long now) {
        if (state.isLoading()) {
            return "Checking official warning data and Smart Risk signals…";
        }
        if (state.canConfirmNoOfficialMatch(now)) {
            return "No active official warning matched this location in the latest available check, and Smart Risk has no active threshold signal. Conditions can still change.";
        }
        if (state.getOfficialDelivery() == AlertTruthPolicy.OfficialDelivery.NOT_APPLICABLE) {
            return "No Smart Risk threshold signal is active for this location. Official IMD warning coverage is not applicable here.";
        }
        if (state.isOfficialStale(now)) {
            return "No current all-clear can be confirmed. Saved official warning data is stale and the latest source refresh was unavailable.";
        }
        return "No alert is currently displayed, but the official warning source could not be verified. This is not an official all-clear.";
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

    private View alertRow(
            @NonNull WeatherAlert alert,
            @NonNull AlertUiState state,
            long now
    ) {
        LinearLayout row = card(false);
        boolean staleOfficial = alert.isOfficial() && state.isOfficialStale(now);
        TextView badge = captionAccent((staleOfficial ? "SAVED OFFICIAL" : alert.sourceLabel())
                + " · " + severityName(alert.getSeverity()));
        badge.setTextColor(severityColor(alert.getSeverity()));
        row.addView(badge);
        row.addView(title(alert.getTitle()), top(-1, -2, 5));
        row.addView(body(alert.getMessage()), top(-1, -2, 5));

        String meta = "";
        if (alert.getLocationLabel() != null) meta += alert.getLocationLabel();
        if (alert.getValidLabel() != null) meta += (meta.isEmpty() ? "" : " · ") + alert.getValidLabel();
        if (!meta.isEmpty()) row.addView(caption(meta), top(-1, -2, 7));

        if (staleOfficial) {
            TextView stale = caption("Saved official warning · latest source refresh unavailable/stale");
            stale.setTextColor(ContextCompat.getColor(activity, R.color.weather_warning));
            row.addView(stale, top(-1, -2, 6));
        }

        TextView details = caption("Tap for alert details");
        details.setTextColor(ContextCompat.getColor(activity, R.color.weather_aqua));
        row.addView(details, top(-1, -2, 7));
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(
                (alert.isOfficial() ? "Official weather warning. " : "Smart Risk. ")
                        + alert.getTitle() + ". Tap for details."
        );
        row.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showAlertDetail(alert, state, now);
        });
        return row;
    }

    private void showAlertDetail(
            @NonNull WeatherAlert alert,
            @NonNull AlertUiState state,
            long now
    ) {
        boolean staleOfficial = alert.isOfficial() && state.isOfficialStale(now);
        StringBuilder details = new StringBuilder();
        details.append("Source: ")
                .append(alert.isOfficial() ? "IMD official CAP warning" : "Smart Risk · app-derived")
                .append('\n');
        details.append("Severity: ").append(severityName(alert.getSeverity())).append('\n');

        if (alert.getLocationLabel() != null && !alert.getLocationLabel().trim().isEmpty()) {
            details.append("Area: ").append(alert.getLocationLabel()).append('\n');
        }
        if (alert.getValidLabel() != null && !alert.getValidLabel().trim().isEmpty()) {
            details.append("Validity: ").append(alert.getValidLabel()).append('\n');
        }
        if (alert.getIssuedAt() > 0L) {
            details.append("Issued: ")
                    .append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(new Date(alert.getIssuedAt())))
                    .append('\n');
        }

        details.append('\n').append(alert.getMessage());
        if (staleOfficial) {
            details.append("\n\nSaved official warning shown as fallback. The latest source refresh is stale/unavailable, so this item must not be interpreted as newly verified.");
        } else if (!alert.isOfficial()) {
            details.append("\n\nSmart Risk is derived by the app from weather data/model thresholds. It is not an official IMD warning.");
        }

        new AlertDialog.Builder(activity)
                .setTitle(alert.getTitle())
                .setMessage(details.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void updateFilterControls() {
        boolean officialEnabled = alertPreferences.isOfficialAlertsEnabled();
        boolean smartEnabled = alertPreferences.isSmartRiskEnabled();
        WeatherAlert.Severity minimum = alertPreferences.getMinimumSeverity();

        applyToggleState(
                officialFilterAction,
                officialEnabled,
                officialEnabled ? "Official: On" : "Official: Hidden",
                officialEnabled
                        ? "Official alert filter on. Tap to hide official alerts and suppress their notifications."
                        : "Official alerts hidden. Tap to show official alerts and allow eligible notifications."
        );
        applyToggleState(
                smartRiskFilterAction,
                smartEnabled,
                smartEnabled ? "Smart Risk: On" : "Smart Risk: Hidden",
                smartEnabled
                        ? "Smart Risk filter on. Tap to hide Smart Risk and suppress its notifications."
                        : "Smart Risk hidden. Tap to show Smart Risk and allow eligible notifications."
        );

        severityFilterAction.setText("Minimum severity: " + severityFilterLabel(minimum));
        severityFilterAction.setBackgroundResource(R.drawable.bg_weather_chip_selected);
        severityFilterAction.setAlpha(1f);
        severityFilterAction.setContentDescription(
                "Minimum alert severity " + severityFilterLabel(minimum)
                        + ". Tap to change minimum severity."
        );

        filterSummary.setText(
                "Display filters apply instantly to the Alerts Center and Home alert card. Hidden sources are also excluded from notification delivery. Smart Risk push delivery keeps its conservative Orange/Red floor."
        );
    }

    private void updateNotificationControls() {
        boolean master = alertPreferences.isNotificationsEnabled();
        boolean canPost = notificationManager.canPostNotifications();
        boolean officialPref = alertPreferences.isOfficialNotificationsEnabled();
        boolean smartPref = alertPreferences.isSmartNotificationsEnabled();
        boolean officialChannel = notificationManager.isOfficialChannelEnabled();
        boolean smartChannel = notificationManager.isSmartChannelEnabled();

        boolean masterActive = master && canPost;
        applyToggleState(
                notificationsAction,
                masterActive,
                masterActive ? "Notifications: On" : "Enable notifications",
                masterActive
                        ? "Weather alert notification master switch on."
                        : "Weather alert notifications off or blocked by Android. Tap to enable."
        );
        applyToggleState(
                officialNotificationAction,
                officialPref,
                officialPref ? "Notify Official: On" : "Notify Official: Off",
                "Toggle official warning notifications."
        );
        applyToggleState(
                smartNotificationAction,
                smartPref,
                smartPref ? "Notify Smart: On" : "Notify Smart: Off",
                "Toggle Smart Risk notifications. Smart Risk remains app-derived, not official."
        );

        String status;
        boolean warning = false;
        if (!master) {
            status = "Background alert notifications are off. In-app alert checking remains available.";
        } else if (!canPost) {
            warning = true;
            status = "Android is blocking app notifications or notification permission is unavailable. Open Android notification settings to review it.";
        } else if (!officialChannel || !smartChannel) {
            warning = true;
            status = "Notification master is on, but "
                    + (!officialChannel && !smartChannel
                    ? "both Official and Smart Risk channels are blocked in Android settings."
                    : !officialChannel
                    ? "the Official channel is blocked in Android settings."
                    : "the Smart Risk channel is blocked in Android settings.");
        } else {
            status = "Notification delivery ready · Official "
                    + (officialPref ? "On" : "Off")
                    + " · Smart Risk " + (smartPref ? "On" : "Off")
                    + " · minimum " + severityFilterLabel(alertPreferences.getMinimumSeverity()) + ".";
        }
        notificationStatus.setText(status);
        notificationStatus.setTextColor(ContextCompat.getColor(
                activity,
                warning ? R.color.weather_warning : R.color.weather_text_tertiary
        ));
    }

    private void applyToggleState(
            @NonNull TextView view,
            boolean enabled,
            @NonNull String label,
            @NonNull String contentDescription
    ) {
        view.setText(label);
        view.setBackgroundResource(enabled
                ? R.drawable.bg_weather_chip_selected
                : R.drawable.bg_weather_chip);
        view.setAlpha(enabled ? 1f : 0.72f);
        view.setContentDescription(contentDescription);
    }

    private void openSystemNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
        try {
            activity.startActivity(intent);
        } catch (RuntimeException ignored) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + activity.getPackageName()));
            try {
                activity.startActivity(fallback);
            } catch (RuntimeException ignoredAgain) {
                // Alerts Center remains usable even if this OEM exposes no settings activity.
            }
        }
    }

    private void rerenderLastState() {
        if (lastState != null) render(lastState);
    }

    @NonNull
    private String severityFilterLabel(@NonNull WeatherAlert.Severity severity) {
        switch (severity) {
            case INFO:
                return "All";
            case ORANGE:
                return "Orange+";
            case RED:
                return "Red only";
            default:
                return "Yellow+";
        }
    }

    private int severityColor(@NonNull WeatherAlert.Severity severity) {
        switch (severity) {
            case RED:
                return ContextCompat.getColor(activity, R.color.weather_danger);
            case ORANGE:
                return ContextCompat.getColor(activity, R.color.weather_warning);
            case YELLOW:
                return ContextCompat.getColor(activity, R.color.weather_sun_warm);
            default:
                return ContextCompat.getColor(activity, R.color.weather_aqua);
        }
    }

    @NonNull
    private String severityName(@NonNull WeatherAlert.Severity severity) {
        switch (severity) {
            case RED:
                return "WARNING";
            case ORANGE:
                return "ALERT";
            case YELLOW:
                return "WATCH";
            default:
                return "INFO";
        }
    }

    private int severityRank(@NonNull WeatherAlert.Severity severity) {
        switch (severity) {
            case RED:
                return 4;
            case ORANGE:
                return 3;
            case YELLOW:
                return 2;
            default:
                return 1;
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

    private TextView section(@NonNull String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        view.setTextSize(17);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView sectionWithTop(@NonNull String text, int topDp) {
        TextView view = section(text);
        view.setPadding(0, dp(topDp), 0, 0);
        return view;
    }

    private TextView title(@NonNull String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        view.setTextSize(16);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView bodyLarge(@NonNull String text) {
        TextView view = body(text);
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView body(@NonNull String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_secondary));
        view.setTextSize(14);
        view.setLineSpacing(0f, 1.08f);
        return view;
    }

    private TextView caption(@NonNull String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_tertiary));
        view.setTextSize(12);
        return view;
    }

    private TextView captionAccent(@NonNull String text) {
        TextView view = caption(text);
        view.setTextColor(ContextCompat.getColor(activity, R.color.weather_aqua));
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLetterSpacing(0.08f);
        return view;
    }

    private TextView actionChip(@NonNull String text) {
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