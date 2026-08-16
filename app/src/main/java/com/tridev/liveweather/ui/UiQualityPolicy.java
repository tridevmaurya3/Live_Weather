package com.tridev.liveweather.ui;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.core.widget.TextViewCompat;

import com.tridev.liveweather.R;

import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Phase 22 shared UX/accessibility policy for the primary app shell.
 *
 * This deliberately changes presentation only. Weather truth, networking,
 * navigation destinations and renderer data remain owned by their existing
 * components.
 */
public final class UiQualityPolicy {

    private static final int TOUCH_TARGET_DP = 48;
    private static final long MIN_RESCAN_INTERVAL_MILLIS = 350L;

    private static final WeakHashMap<Activity, Boolean> INSTALLED = new WeakHashMap<>();
    private static final WeakHashMap<Activity, Long> LAST_SCAN = new WeakHashMap<>();

    private UiQualityPolicy() {
    }

    public static void install(@NonNull Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        synchronized (INSTALLED) {
            if (!Boolean.TRUE.equals(INSTALLED.get(activity))) {
                INSTALLED.put(activity, true);
                ViewTreeObserver observer = content.getViewTreeObserver();
                observer.addOnGlobalLayoutListener(() -> scheduleApply(activity, content));
            }
        }
        scheduleApply(activity, content);
    }

    public static void applyNow(@NonNull Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        applyResponsivePageGeometry(activity);
        applyKnownSemantics(activity);
        applyStatusPresentation(activity);
        auditTree(content);
    }

    private static void scheduleApply(@NonNull Activity activity, @NonNull View content) {
        long now = SystemClock.uptimeMillis();
        synchronized (LAST_SCAN) {
            Long previous = LAST_SCAN.get(activity);
            if (previous != null && now - previous < MIN_RESCAN_INTERVAL_MILLIS) return;
            LAST_SCAN.put(activity, now);
        }
        content.post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                applyNow(activity);
            }
        });
    }

    private static void applyResponsivePageGeometry(@NonNull Activity activity) {
        Configuration configuration = activity.getResources().getConfiguration();
        int widthDp = configuration.screenWidthDp;
        int heightDp = configuration.screenHeightDp;
        float fontScale = configuration.fontScale;
        boolean compactLargeText = widthDp < 360 || fontScale >= 1.30f;

        int horizontalPaddingDp;
        if (widthDp >= 840) horizontalPaddingDp = 48;
        else if (widthDp >= 600) horizontalPaddingDp = 32;
        else horizontalPaddingDp = 16;

        View homePage = activity.findViewById(R.id.pageHome);
        View forecastPage = activity.findViewById(R.id.pageForecast);
        View radarPage = activity.findViewById(R.id.pageRadar);
        View wallpaperPage = activity.findViewById(R.id.pageWallpaper);
        View morePage = activity.findViewById(R.id.pageMore);

        applyPageHorizontalPadding(homePage, horizontalPaddingDp);
        applyPageHorizontalPadding(forecastPage, horizontalPaddingDp);
        applyPageHorizontalPadding(wallpaperPage, horizontalPaddingDp);
        applyPageHorizontalPadding(morePage, horizontalPaddingDp);
        applyPageHorizontalPadding(radarPage, horizontalPaddingDp);

        View radarMap = activity.findViewById(R.id.radarMapHost);
        if (radarMap != null) {
            int mapMinDp;
            if (widthDp >= 600) mapMinDp = 320;
            else if (heightDp < 640 || compactLargeText) mapMinDp = 200;
            else mapMinDp = 260;
            radarMap.setMinimumHeight(dp(activity, mapMinDp));
        }

        TextView temperature = activity.findViewById(R.id.homeTemperature);
        if (temperature != null) {
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    temperature,
                    compactLargeText ? 30 : 36,
                    64,
                    2,
                    TypedValue.COMPLEX_UNIT_SP
            );
        }

        if (compactLargeText) {
            stackWeightedRows(homePage);
            stackWeightedRows(forecastPage);
            stackWeightedRows(morePage);
            stackParent(activity.findViewById(R.id.homeFeelsLike));
            stackParent(activity.findViewById(R.id.citySearchInput));
            stackGrandparent(activity.findViewById(R.id.radarLocationValue));
        }
    }

    private static void applyPageHorizontalPadding(View page, int horizontalPaddingDp) {
        if (page == null) return;
        View target = page;
        if (page instanceof NestedScrollView && page instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) page;
            if (group.getChildCount() > 0) target = group.getChildAt(0);
        }
        int horizontal = dp(target, horizontalPaddingDp);
        target.setPaddingRelative(
                horizontal,
                target.getPaddingTop(),
                horizontal,
                target.getPaddingBottom()
        );
    }

    private static void stackWeightedRows(View root) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                if (row.getOrientation() == LinearLayout.HORIZONTAL && allChildrenWeighted(row)) {
                    stackLinearLayout(row);
                }
            }
            stackWeightedRows(child);
        }
    }

    private static boolean allChildrenWeighted(@NonNull LinearLayout row) {
        if (row.getChildCount() < 2) return false;
        for (int index = 0; index < row.getChildCount(); index++) {
            ViewGroup.LayoutParams raw = row.getChildAt(index).getLayoutParams();
            if (!(raw instanceof LinearLayout.LayoutParams)) return false;
            if (((LinearLayout.LayoutParams) raw).weight <= 0f) return false;
        }
        return true;
    }

    private static void stackParent(View child) {
        if (child == null || !(child.getParent() instanceof LinearLayout)) return;
        stackLinearLayout((LinearLayout) child.getParent());
    }

    private static void stackGrandparent(View child) {
        if (child == null || !(child.getParent() instanceof View)) return;
        View parent = (View) child.getParent();
        if (parent.getParent() instanceof LinearLayout) {
            stackLinearLayout((LinearLayout) parent.getParent());
        }
    }

    private static void stackLinearLayout(@NonNull LinearLayout row) {
        if (row.getOrientation() == LinearLayout.VERTICAL) return;
        row.setOrientation(LinearLayout.VERTICAL);
        for (int index = 0; index < row.getChildCount(); index++) {
            View child = row.getChildAt(index);
            ViewGroup.LayoutParams raw = child.getLayoutParams();
            if (!(raw instanceof LinearLayout.LayoutParams)) continue;
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) raw;
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.weight = 0f;
            params.setMarginStart(0);
            params.setMarginEnd(0);
            if (index > 0 && params.topMargin < dp(child, 8)) {
                params.topMargin = dp(child, 8);
            }
            child.setLayoutParams(params);
        }
    }

    private static void applyKnownSemantics(@NonNull Activity activity) {
        setPane(activity, R.id.pageHome, "Home weather");
        setPane(activity, R.id.pageForecast, "Forecast");
        setPane(activity, R.id.pageRadar, "Weather radar");
        setPane(activity, R.id.pageWallpaper, "Live wallpaper");
        setPane(activity, R.id.pageMore, "More weather tools and settings");

        markFirstTextHeading(activity.findViewById(R.id.pageHome));
        markFirstTextHeading(activity.findViewById(R.id.pageForecast));
        markFirstTextHeading(activity.findViewById(R.id.pageRadar));
        markFirstTextHeading(activity.findViewById(R.id.pageWallpaper));
        markFirstTextHeading(activity.findViewById(R.id.pageMore));

        describe(activity, R.id.homeLocationValue,
                "Active weather location. Tap to change the saved city or request device location.");
        describe(activity, R.id.homeRefreshAction,
                "Refresh current weather, air quality and weather alerts.");
        describe(activity, R.id.homeForecastAction,
                "Open detailed hourly and ten day forecast.");
        describe(activity, R.id.homeRadarAction,
                "Open weather radar.");
        describe(activity, R.id.homeAirAction,
                "Open air quality details.");
        describe(activity, R.id.homeWallpaperAction,
                "Open live wallpaper controls.");

        describe(activity, R.id.radarRefreshButton,
                "Refresh observed radar metadata and current model overlay data.");
        describe(activity, R.id.radarRecenterButton,
                "Recenter radar map on the active weather location.");
        describe(activity, R.id.radarLayerRain,
                "Show observed precipitation radar layer.");
        describe(activity, R.id.radarLayerClouds,
                "Show current model cloud field layer.");
        describe(activity, R.id.radarLayerWind,
                "Show current model wind field layer.");
        describe(activity, R.id.radarLayerTemp,
                "Show current model temperature field layer.");
        describe(activity, R.id.radarPlayButton,
                "Replay observed radar history.");

        describe(activity, R.id.applyWallpaperButton,
                "Open Android live wallpaper preview and apply screen.");
        describe(activity, R.id.cityUseCurrentButton,
                "Use current device location for weather.");
        describe(activity, R.id.citySearchButton,
                "Search for the city entered in the location search field.");
        describe(activity, R.id.moreWidgetsAction,
                "Open weather widget choices.");

        // Canvas-rendered charts duplicate data already exposed by the accessible
        // hourly controls and selected-hour text. Hiding the canvas from TalkBack
        // avoids an unlabeled interactive surface while preserving touch use.
        hideFromAccessibility(activity.findViewById(R.id.forecastTemperatureChart));
        hideFromAccessibility(activity.findViewById(R.id.forecastRainChart));
        hideFromAccessibility(activity.findViewById(R.id.forecastLiveSkyView));
        hideFromAccessibility(activity.findViewById(R.id.wallpaperLiveSkyView));
        hideFromAccessibility(activity.findViewById(R.id.appLiveNatureBackground));
    }

    private static void applyStatusPresentation(@NonNull Activity activity) {
        styleStatus(activity, R.id.homeSyncStatus, true,
                "Weather data status. Tap to refresh current weather, air quality and alerts.");
        styleStatus(activity, R.id.forecastStatus, true,
                "Forecast data status. Tap to refresh forecast, air quality and alerts.");
        styleStatus(activity, R.id.radarStatusValue, false,
                "Radar data status.");
        styleStatus(activity, R.id.radarFreshnessValue, false,
                "Radar and model data freshness.");
        styleStatus(activity, R.id.citySearchStatus, false,
                "City search status.");
    }

    private static void styleStatus(
            @NonNull Activity activity,
            int id,
            boolean retryAction,
            @NonNull String prefix
    ) {
        View raw = activity.findViewById(id);
        if (!(raw instanceof TextView)) return;
        TextView view = (TextView) raw;
        CharSequence text = view.getText();
        String value = text == null ? "" : text.toString();
        String lower = value.toLowerCase(Locale.ROOT);

        int color;
        if (containsAny(lower, "error", "issue", "retry", "unavailable", "failed", "stale", "delayed")) {
            color = R.color.weather_warning;
        } else if (containsAny(lower, "loading", "refreshing", "checking", "requesting", "waiting")) {
            color = R.color.weather_sky_blue;
        } else if (containsAny(lower, "live", "ready", "updated", "synchronized", "latest")) {
            color = R.color.weather_aqua;
        } else {
            color = R.color.weather_text_secondary;
        }
        view.setTextColor(ContextCompat.getColor(activity, color));
        ViewCompat.setAccessibilityLiveRegion(view, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);

        StringBuilder description = new StringBuilder(prefix);
        if (!TextUtils.isEmpty(text)) description.append(' ').append(text);
        if (retryAction) description.append(" Tap to refresh.");
        view.setContentDescription(description.toString());
    }

    private static boolean containsAny(@NonNull String value, @NonNull String... terms) {
        for (String term : terms) {
            if (value.contains(term)) return true;
        }
        return false;
    }

    private static void auditTree(@NonNull View view) {
        boolean interactive = view.isClickable()
                || view.isLongClickable()
                || view instanceof EditText
                || view instanceof android.widget.CompoundButton
                || view instanceof android.widget.SeekBar;

        if (interactive) {
            int target = dp(view, TOUCH_TARGET_DP);
            if (view.getMinimumHeight() < target) view.setMinimumHeight(target);
            if (view.getMinimumWidth() < target) view.setMinimumWidth(target);
            if (!(view instanceof EditText)) view.setFocusable(true);
            ensureInteractiveDescription(view);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                auditTree(group.getChildAt(index));
            }
        }
    }

    private static void ensureInteractiveDescription(@NonNull View view) {
        if (!TextUtils.isEmpty(view.getContentDescription())) return;
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence label = textView.getText();
            if (TextUtils.isEmpty(label)) label = textView.getHint();
            if (!TextUtils.isEmpty(label)) view.setContentDescription(label);
            return;
        }
        if (view instanceof ViewGroup) {
            String summary = collectText((ViewGroup) view, 3, 180);
            if (!TextUtils.isEmpty(summary)) view.setContentDescription(summary);
        }
    }

    @NonNull
    private static String collectText(@NonNull ViewGroup group, int maxParts, int maxCharacters) {
        StringBuilder builder = new StringBuilder();
        appendText(group, builder, maxParts, maxCharacters, new int[]{0});
        return builder.toString().trim();
    }

    private static void appendText(
            @NonNull View view,
            @NonNull StringBuilder builder,
            int maxParts,
            int maxCharacters,
            @NonNull int[] parts
    ) {
        if (parts[0] >= maxParts || builder.length() >= maxCharacters) return;
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (!TextUtils.isEmpty(text)) {
                if (builder.length() > 0) builder.append(". ");
                String value = text.toString().replace('\n', ' ').trim();
                int remaining = maxCharacters - builder.length();
                if (value.length() > remaining) value = value.substring(0, Math.max(0, remaining));
                builder.append(value);
                parts[0]++;
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup nested = (ViewGroup) view;
            for (int index = 0; index < nested.getChildCount(); index++) {
                appendText(nested.getChildAt(index), builder, maxParts, maxCharacters, parts);
                if (parts[0] >= maxParts || builder.length() >= maxCharacters) return;
            }
        }
    }

    private static void setPane(@NonNull Activity activity, int id, @NonNull String title) {
        View view = activity.findViewById(id);
        if (view != null) ViewCompat.setAccessibilityPaneTitle(view, title);
    }

    private static void markFirstTextHeading(View page) {
        TextView first = findFirstTextView(page);
        if (first != null) ViewCompat.setAccessibilityHeading(first, true);
    }

    private static TextView findFirstTextView(View view) {
        if (view == null) return null;
        if (view instanceof TextView && !TextUtils.isEmpty(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = findFirstTextView(group.getChildAt(index));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void describe(@NonNull Activity activity, int id, @NonNull String description) {
        View view = activity.findViewById(id);
        if (view != null) view.setContentDescription(description);
    }

    private static void hideFromAccessibility(View view) {
        if (view != null) view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private static int dp(@NonNull Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static int dp(@NonNull View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
