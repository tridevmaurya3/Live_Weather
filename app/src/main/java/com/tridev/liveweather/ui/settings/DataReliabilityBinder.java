package com.tridev.liveweather.ui.settings;

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
import androidx.core.view.ViewCompat;

import com.tridev.liveweather.R;
import com.tridev.liveweather.core.DataReliabilityPolicy;

/** Adds the Phase 23 cache/location diagnostics card to the More page. */
public final class DataReliabilityBinder {

    private static final String TAG_CARD = "phase23_data_reliability_card";
    private static final String TAG_BODY = "phase23_data_reliability_body";

    private DataReliabilityBinder() {
    }

    public static void bind(@NonNull Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;

        View existingCard = root.findViewWithTag(TAG_CARD);
        if (existingCard != null) {
            updateBody(activity, existingCard);
            return;
        }

        TextView performanceTitle = findTextView(
                (ViewGroup) root,
                activity.getString(R.string.more_performance)
        );
        if (performanceTitle == null || !(performanceTitle.getParent() instanceof View)) return;
        View performanceCard = (View) performanceTitle.getParent();
        if (!(performanceCard.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) performanceCard.getParent();

        LinearLayout card = new LinearLayout(activity);
        card.setTag(TAG_CARD);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        card.setBackgroundResource(R.drawable.bg_weather_card_accent);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(activity, 10);

        TextView title = new TextView(activity);
        title.setText("Data Reliability");
        title.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        ViewCompat.setAccessibilityHeading(title, true);
        card.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText("Offline/cache identity, age and cross-surface diagnostics");
        subtitle.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_secondary));
        subtitle.setTextSize(12f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = dp(activity, 4);
        card.addView(subtitle, subtitleParams);

        TextView body = new TextView(activity);
        body.setTag(TAG_BODY);
        body.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_secondary));
        body.setTextSize(13f);
        body.setLineSpacing(0f, 1.08f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        bodyParams.topMargin = dp(activity, 10);
        card.addView(body, bodyParams);

        TextView refresh = new TextView(activity);
        refresh.setText("Refresh diagnostics");
        refresh.setTextColor(ContextCompat.getColor(activity, R.color.weather_text_primary));
        refresh.setTextSize(13f);
        refresh.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackgroundResource(R.drawable.bg_weather_chip);
        refresh.setClickable(true);
        refresh.setFocusable(true);
        refresh.setMinHeight(dp(activity, 48));
        refresh.setContentDescription("Refresh local weather cache and location identity diagnostics");
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 48)
        );
        refreshParams.topMargin = dp(activity, 10);
        card.addView(refresh, refreshParams);
        refresh.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            updateBody(activity, card);
        });

        int index = parent.indexOfChild(performanceCard);
        parent.addView(card, Math.min(index + 1, parent.getChildCount()), cardParams);
        updateBody(activity, card);
    }

    private static void updateBody(@NonNull Activity activity, @NonNull View card) {
        View raw = card.findViewWithTag(TAG_BODY);
        if (!(raw instanceof TextView)) return;
        TextView body = (TextView) raw;
        String report = DataReliabilityPolicy.diagnostics(activity);
        body.setText(report);
        body.setContentDescription("Data reliability diagnostics. " + report.replace('\n', ' '));
    }

    private static TextView findTextView(@NonNull ViewGroup root, @NonNull String text) {
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (child instanceof TextView && text.contentEquals(((TextView) child).getText())) {
                return (TextView) child;
            }
            if (child instanceof ViewGroup) {
                TextView found = findTextView((ViewGroup) child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int dp(@NonNull Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
