package com.tridev.liveweather.ui;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import com.tridev.liveweather.R;

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

        int horizontalPaddingDp;
        if (widthDp >= 840) horizontalPaddingDp = 48;
        else if (widthDp >= 600) horizontalPaddingDp = 32;
        else horizontalPaddingDp = 16;

        applyPageHorizontalPadding(activity.findViewById(R.id.pageHome), horizontalPaddingDp);
        applyPageHorizontalPadding(activity.findViewById(R.id.pageForecast), horizontalPaddingDp);
        applyPageHorizontalPadding(activity.findViewById(R.id.pageWallpaper), horizontalPaddingDp);
        applyPageHorizontalPadding(activity.findViewById(R.id.pageMore), horizontalPaddingDp);
        applyPageHorizontalPadding(activity.findViewById(R.id.pageRadar), horizontalPaddingDp);

        View radarMap = activity.findViewById(R.id.radarMapHost);
        if (radarMap != null) {
            int mapMinDp;
            if (widthDp >= 600) mapMinDp = 320;
            else if (heightDp < 640 || fontScale >= 1.30f) mapMinDp = 200;
            else mapMinDp = 260;
            radarMap.setMinimumHeight(dp(activity, mapMinDp));
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
        describe(activity, R.id.homeSyncStatus,
                "Weather data status. Tap to refresh current weather, air quality and alerts.");
        describe(activity, R.id.homeForecastAction,
                "Open detailed hourly and ten day forecast.");
        describe(activity, R.id.homeRadarAction,
                "Open weather radar.");
        describe(activity, R.id.homeAirAction,
                "Open air quality details.");
        describe(activity, R.id.homeWallpaperAction,
                "Open live wallpaper controls.");

        describe(activity, R.id.forecastStatus,
                "Forecast data status. Tap to refresh forecast, air quality and alerts.");

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

        View forecastSky = activity.findViewById(R.id.forecastLiveSkyView);
        if (forecastSky != null) forecastSky.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        View wallpaperSky = activity.findViewById(R.id.wallpaperLiveSkyView);
        if (wallpaperSky != null) wallpaperSky.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        View appSky = activity.findViewById(R.id.appLiveNatureBackground);
        if (appSky != null) appSky.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
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
            if (!TextUtils.isEmpty(label)) {
                view.setContentDescription(label);
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

    private static int dp(@NonNull Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static int dp(@NonNull View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
