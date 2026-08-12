package com.tridev.liveweather.ui.settings;

import android.app.Activity;
import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.tridev.liveweather.R;
import com.tridev.liveweather.data.local.PerformancePreferences;
import com.tridev.liveweather.data.local.UnitPreferences;
import com.tridev.liveweather.ui.weather.WeatherFormatter;
import com.tridev.liveweather.widget.WeatherWidgetUpdater;

/**
 * Phase 16 binder for the existing More-page Units and Performance cards.
 *
 * The cards predate their functional settings behavior and do not have stable
 * view IDs. To avoid a risky large layout/MainActivity rewrite, the binder
 * locates each card by its localized title TextView and attaches behavior to
 * the parent card container when MainActivity becomes visible.
 */
public final class SettingsCardBinder {

    private static final int TAG_BOUND = 0x6c697665;

    private SettingsCardBinder() {
    }

    public static void bind(@NonNull Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (!(root instanceof ViewGroup)) return;

        bindUnitsCard(activity, (ViewGroup) root);
        bindPerformanceCard(activity, (ViewGroup) root);
    }

    private static void bindUnitsCard(@NonNull Activity activity, @NonNull ViewGroup root) {
        TextView title = findTextView(root, activity.getString(R.string.more_units));
        if (title == null || !(title.getParent() instanceof View)) return;

        View card = (View) title.getParent();
        if (isBound(card)) {
            updateCardSubtitle(card, new UnitPreferences(activity).load().summary());
            return;
        }

        markBound(card);
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(activity.getString(R.string.phase16_units_accessibility));
        updateCardSubtitle(card, new UnitPreferences(activity).load().summary());
        card.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showUnitsDialog(activity);
        });
    }

    private static void bindPerformanceCard(@NonNull Activity activity, @NonNull ViewGroup root) {
        TextView title = findTextView(root, activity.getString(R.string.more_performance));
        if (title == null || !(title.getParent() instanceof View)) return;

        View card = (View) title.getParent();
        if (isBound(card)) {
            updatePerformanceSubtitle(activity, card);
            return;
        }

        markBound(card);
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(activity.getString(R.string.phase16_performance_accessibility));
        updatePerformanceSubtitle(activity, card);
        card.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            showPerformanceDialog(activity);
        });
    }

    private static void showUnitsDialog(@NonNull Activity activity) {
        UnitPreferences preferences = new UnitPreferences(activity);
        UnitPreferences.Units current = preferences.load();

        String[] options = {
                activity.getString(R.string.phase16_units_metric),
                activity.getString(R.string.phase16_units_imperial),
                activity.getString(R.string.phase16_units_custom)
        };

        new AlertDialog.Builder(activity)
                .setTitle(R.string.phase16_units_title)
                .setMessage(current.summary())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        applyUnits(activity, UnitPreferences.metric());
                    } else if (which == 1) {
                        applyUnits(activity, UnitPreferences.imperial());
                    } else {
                        showCustomCategoryDialog(activity);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showCustomCategoryDialog(@NonNull Activity activity) {
        UnitPreferences.Units current = new UnitPreferences(activity).load();
        String[] categories = {
                activity.getString(R.string.phase16_temperature_row,
                        current.getTemperature() == UnitPreferences.TemperatureUnit.CELSIUS ? "°C" : "°F"),
                activity.getString(R.string.phase16_wind_row,
                        current.getWind() == UnitPreferences.WindUnit.KMH ? "km/h" : "mph"),
                activity.getString(R.string.phase16_pressure_row,
                        current.getPressure() == UnitPreferences.PressureUnit.HPA ? "hPa" : "inHg"),
                activity.getString(R.string.phase16_precip_row,
                        current.getPrecipitation() == UnitPreferences.PrecipitationUnit.MM ? "mm" : "in"),
                activity.getString(R.string.phase16_distance_row,
                        current.getDistance() == UnitPreferences.DistanceUnit.KM ? "km" : "mi")
        };

        new AlertDialog.Builder(activity)
                .setTitle(R.string.phase16_custom_units_title)
                .setItems(categories, (dialog, which) -> showUnitChoiceDialog(activity, which))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showUnitChoiceDialog(@NonNull Activity activity, int category) {
        UnitPreferences preferences = new UnitPreferences(activity);
        UnitPreferences.Units current = preferences.load();

        String title;
        String[] choices;
        int checked;

        switch (category) {
            case 0:
                title = activity.getString(R.string.phase16_temperature_title);
                choices = new String[]{"Celsius (°C)", "Fahrenheit (°F)"};
                checked = current.getTemperature() == UnitPreferences.TemperatureUnit.CELSIUS ? 0 : 1;
                break;
            case 1:
                title = activity.getString(R.string.phase16_wind_title);
                choices = new String[]{"Kilometres/hour (km/h)", "Miles/hour (mph)"};
                checked = current.getWind() == UnitPreferences.WindUnit.KMH ? 0 : 1;
                break;
            case 2:
                title = activity.getString(R.string.phase16_pressure_title);
                choices = new String[]{"Hectopascal (hPa)", "Inches of mercury (inHg)"};
                checked = current.getPressure() == UnitPreferences.PressureUnit.HPA ? 0 : 1;
                break;
            case 3:
                title = activity.getString(R.string.phase16_precipitation_title);
                choices = new String[]{"Millimetres (mm)", "Inches (in)"};
                checked = current.getPrecipitation() == UnitPreferences.PrecipitationUnit.MM ? 0 : 1;
                break;
            case 4:
            default:
                title = activity.getString(R.string.phase16_distance_title);
                choices = new String[]{"Kilometres (km)", "Miles (mi)"};
                checked = current.getDistance() == UnitPreferences.DistanceUnit.KM ? 0 : 1;
                break;
        }

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setSingleChoiceItems(choices, checked, (dialog, which) -> {
                    UnitPreferences.Units updated = withChoice(current, category, which);
                    dialog.dismiss();
                    applyUnits(activity, updated);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @NonNull
    private static UnitPreferences.Units withChoice(
            @NonNull UnitPreferences.Units current,
            int category,
            int choice
    ) {
        UnitPreferences.TemperatureUnit temperature = current.getTemperature();
        UnitPreferences.WindUnit wind = current.getWind();
        UnitPreferences.PressureUnit pressure = current.getPressure();
        UnitPreferences.PrecipitationUnit precipitation = current.getPrecipitation();
        UnitPreferences.DistanceUnit distance = current.getDistance();

        switch (category) {
            case 0:
                temperature = choice == 0
                        ? UnitPreferences.TemperatureUnit.CELSIUS
                        : UnitPreferences.TemperatureUnit.FAHRENHEIT;
                break;
            case 1:
                wind = choice == 0
                        ? UnitPreferences.WindUnit.KMH
                        : UnitPreferences.WindUnit.MPH;
                break;
            case 2:
                pressure = choice == 0
                        ? UnitPreferences.PressureUnit.HPA
                        : UnitPreferences.PressureUnit.INHG;
                break;
            case 3:
                precipitation = choice == 0
                        ? UnitPreferences.PrecipitationUnit.MM
                        : UnitPreferences.PrecipitationUnit.INCH;
                break;
            case 4:
            default:
                distance = choice == 0
                        ? UnitPreferences.DistanceUnit.KM
                        : UnitPreferences.DistanceUnit.MILE;
                break;
        }

        return new UnitPreferences.Units(temperature, wind, pressure, precipitation, distance);
    }

    private static void applyUnits(
            @NonNull Activity activity,
            @NonNull UnitPreferences.Units units
    ) {
        new UnitPreferences(activity).save(units);
        WeatherFormatter.configure(units);
        WeatherWidgetUpdater.updateAll(activity);
        Toast.makeText(
                activity,
                activity.getString(R.string.phase16_units_applied, units.summary()),
                Toast.LENGTH_SHORT
        ).show();
        activity.recreate();
    }

    private static void showPerformanceDialog(@NonNull Activity activity) {
        PerformancePreferences preferences = new PerformancePreferences(activity);
        PerformancePreferences.Mode current = preferences.loadMode();
        String[] options = {
                activity.getString(R.string.phase16_performance_auto),
                activity.getString(R.string.phase16_performance_smooth),
                activity.getString(R.string.phase16_performance_battery)
        };
        int checked = current == PerformancePreferences.Mode.SMOOTH
                ? 1
                : current == PerformancePreferences.Mode.BATTERY ? 2 : 0;

        new AlertDialog.Builder(activity)
                .setTitle(R.string.phase16_performance_title)
                .setSingleChoiceItems(options, checked, (dialog, which) -> {
                    PerformancePreferences.Mode mode = which == 1
                            ? PerformancePreferences.Mode.SMOOTH
                            : which == 2
                            ? PerformancePreferences.Mode.BATTERY
                            : PerformancePreferences.Mode.AUTO;
                    preferences.saveMode(mode);
                    dialog.dismiss();
                    Toast.makeText(
                            activity,
                            activity.getString(R.string.phase16_performance_applied, performanceLabel(mode)),
                            Toast.LENGTH_SHORT
                    ).show();
                    activity.recreate();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void updatePerformanceSubtitle(@NonNull Activity activity, @NonNull View card) {
        PerformancePreferences.Mode mode = new PerformancePreferences(activity).loadMode();
        updateCardSubtitle(
                card,
                activity.getString(R.string.phase16_performance_summary, performanceLabel(mode))
        );
    }

    @NonNull
    private static String performanceLabel(@NonNull PerformancePreferences.Mode mode) {
        switch (mode) {
            case SMOOTH:
                return "Smooth";
            case BATTERY:
                return "Battery";
            case AUTO:
            default:
                return "Auto";
        }
    }

    private static void updateCardSubtitle(@NonNull View card, @NonNull String text) {
        if (!(card instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) card;
        TextView firstText = null;
        TextView secondText = null;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                if (firstText == null) firstText = (TextView) child;
                else {
                    secondText = (TextView) child;
                    break;
                }
            }
        }
        if (secondText != null) secondText.setText(text);
    }

    @Nullable
    private static TextView findTextView(@NonNull ViewGroup root, @NonNull String target) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView) {
                CharSequence value = ((TextView) child).getText();
                if (value != null && target.contentEquals(value)) return (TextView) child;
            }
            if (child instanceof ViewGroup) {
                TextView nested = findTextView((ViewGroup) child, target);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static boolean isBound(@NonNull View view) {
        Object tag = view.getTag();
        return tag instanceof Integer && ((Integer) tag) == TAG_BOUND;
    }

    private static void markBound(@NonNull View view) {
        view.setTag(TAG_BOUND);
    }
}
