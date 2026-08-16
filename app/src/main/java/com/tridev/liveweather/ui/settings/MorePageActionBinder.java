package com.tridev.liveweather.ui.settings;

import android.app.Activity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.tridev.liveweather.R;

/**
 * Phase 25 product-completeness binder for summary actions and legacy status polish.
 *
 * Alerts and Air quality already have full dynamic sections on the More page.
 * Their compact cards now navigate to those sections instead of looking static.
 * Old internal phase/status labels are also replaced at runtime with product-facing
 * language without altering live weather status text once the renderer owns it.
 */
public final class MorePageActionBinder {

    private MorePageActionBinder() {
    }

    public static void bind(@NonNull Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;

        ViewGroup content = (ViewGroup) root;
        applyProductStatusText(activity, content);
        bindScrollCard(
                activity,
                content,
                activity.getString(R.string.more_alerts),
                "Weather Alerts Center",
                "Open weather alerts center"
        );
        bindScrollCard(
                activity,
                content,
                activity.getString(R.string.more_air_quality),
                "Air Quality Intelligence",
                "Open air quality details"
        );
    }

    private static void applyProductStatusText(
            @NonNull Activity activity,
            @NonNull ViewGroup root
    ) {
        TextView homeStatus = activity.findViewById(R.id.homePhaseStatus);
        if (homeStatus != null) {
            homeStatus.setText(R.string.product_status_home);
        }

        TextView forecastStatus = activity.findViewById(R.id.forecastStatus);
        if (forecastStatus != null) {
            CharSequence current = forecastStatus.getText();
            String legacy = activity.getString(R.string.status_phase_six);
            if (current != null && legacy.contentEquals(current)) {
                forecastStatus.setText(R.string.product_status_forecast);
            }
        }

        TextView wallpaperStatus = findTextView(
                root,
                activity.getString(R.string.status_real_nature_engine)
        );
        if (wallpaperStatus != null) {
            wallpaperStatus.setText(R.string.product_status_wallpaper);
        }
    }

    private static void bindScrollCard(
            @NonNull Activity activity,
            @NonNull ViewGroup root,
            @NonNull String cardTitle,
            @NonNull String targetTitle,
            @NonNull String contentDescription
    ) {
        TextView cardTitleView = findTextView(root, cardTitle);
        TextView targetTitleView = findTextView(root, targetTitle);
        if (cardTitleView == null || targetTitleView == null) return;
        if (!(cardTitleView.getParent() instanceof View)) return;
        if (!(targetTitleView.getParent() instanceof View)) return;

        View card = (View) cardTitleView.getParent();
        View target = (View) targetTitleView.getParent();
        NestedScrollView scroll = findScrollParent(target);
        if (scroll == null) return;

        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(contentDescription);
        card.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            scroll.post(() -> {
                int y = Math.max(0, target.getTop() - dp(activity, 12));
                scroll.smoothScrollTo(0, y);
                target.requestFocus();
            });
        });
    }

    @Nullable
    private static NestedScrollView findScrollParent(@NonNull View view) {
        android.view.ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof NestedScrollView) return (NestedScrollView) parent;
            parent = parent.getParent();
        }
        return null;
    }

    @Nullable
    private static TextView findTextView(@NonNull ViewGroup root, @NonNull String text) {
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (child instanceof TextView) {
                CharSequence value = ((TextView) child).getText();
                if (value != null && text.contentEquals(value)) return (TextView) child;
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
