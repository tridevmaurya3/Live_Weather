package com.tridev.liveweather.ui.scene;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.tridev.liveweather.R;
import com.tridev.liveweather.data.local.WallpaperPreferences;
import com.tridev.liveweather.domain.scene.SceneryMode;
import com.tridev.liveweather.domain.scene.SceneryVariantRuntimeState;

/**
 * Compact user-facing scenery selector for the Wallpaper page.
 *
 * The selector changes only persisted visual scenery preferences. Weather truth,
 * astronomy, precipitation, cloud state, alerts and cached observations are untouched.
 * S4 adds four stable composition variations per scenery mode, producing 28 selectable
 * visual combinations across the seven user-facing scene categories.
 */
public final class ScenerySelectorView extends LinearLayout {

    private static final SceneryMode[] SELECTABLE_MODES = {
            SceneryMode.OPEN_SKY,
            SceneryMode.NATURAL_HILLS,
            SceneryMode.VILLAGE,
            SceneryMode.FARM_CROPS,
            SceneryMode.RIVER_LAKE,
            SceneryMode.FLOWERS_GREENERY,
            SceneryMode.URBAN_BUILDINGS
    };

    private final WallpaperPreferences preferences;
    private final TextView selectionSummary;
    private final TextView variationSummary;
    private final TextView variationAction;
    private final TextView[] chips = new TextView[SELECTABLE_MODES.length];

    @NonNull
    private SceneryMode selectedMode;
    private int selectedVariant;

    public ScenerySelectorView(@NonNull Context context) {
        this(context, null);
    }

    public ScenerySelectorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScenerySelectorView(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        preferences = new WallpaperPreferences(context);
        selectedMode = preferences.load().getSceneryMode();
        SceneryVariantRuntimeState.initialize(context);
        selectedVariant = SceneryVariantRuntimeState.get();

        selectionSummary = new TextView(context);
        selectionSummary.setTextAppearance(R.style.TextAppearance_LiveWeather_Caption);
        selectionSummary.setTextColor(ContextCompat.getColor(context, R.color.weather_aqua));
        selectionSummary.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        addView(selectionSummary, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.setClipToPadding(false);
        scroller.setOverScrollMode(OVER_SCROLL_NEVER);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(0, dp(8), 0, 0);

        for (int index = 0; index < SELECTABLE_MODES.length; index++) {
            SceneryMode mode = SELECTABLE_MODES[index];
            TextView chip = createChip(context, mode);
            chips[index] = chip;

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(48)
            );
            if (index < SELECTABLE_MODES.length - 1) {
                params.setMarginEnd(dp(8));
            }
            row.addView(chip, params);
        }

        scroller.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        addView(scroller, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout variationRow = new LinearLayout(context);
        variationRow.setOrientation(HORIZONTAL);
        variationRow.setGravity(Gravity.CENTER_VERTICAL);
        variationRow.setPadding(0, dp(8), 0, 0);

        variationSummary = new TextView(context);
        variationSummary.setTextAppearance(R.style.TextAppearance_LiveWeather_Caption);
        variationSummary.setTextColor(ContextCompat.getColor(context, R.color.weather_text_secondary));
        variationSummary.setGravity(Gravity.CENTER_VERTICAL);
        variationSummary.setMinHeight(dp(48));

        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
        );
        variationRow.addView(variationSummary, summaryParams);

        variationAction = new TextView(context);
        variationAction.setTextAppearance(R.style.TextAppearance_LiveWeather_Body);
        variationAction.setText(R.string.wallpaper_scenery_variation_action);
        variationAction.setGravity(Gravity.CENTER);
        variationAction.setSingleLine(true);
        variationAction.setMinWidth(dp(126));
        variationAction.setMinHeight(dp(48));
        variationAction.setPadding(dp(14), 0, dp(14), 0);
        variationAction.setClickable(true);
        variationAction.setFocusable(true);
        variationAction.setBackgroundResource(R.drawable.bg_weather_chip);
        variationAction.setTextColor(ContextCompat.getColor(context, R.color.weather_text_primary));
        variationAction.setOnClickListener(view -> changeVariation());

        variationRow.addView(variationAction, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(48)
        ));

        addView(variationRow, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        renderSelection();
    }

    @NonNull
    private TextView createChip(@NonNull Context context, @NonNull SceneryMode mode) {
        TextView chip = new TextView(context);
        chip.setTextAppearance(R.style.TextAppearance_LiveWeather_Body);
        chip.setText(labelRes(mode));
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setMinWidth(dp(76));
        chip.setMinHeight(dp(48));
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setContentDescription(getResources().getString(
                R.string.wallpaper_scenery_select_accessibility,
                getResources().getString(labelRes(mode))
        ));
        chip.setOnClickListener(view -> selectMode(mode));
        return chip;
    }

    private void selectMode(@NonNull SceneryMode mode) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (selectedMode == mode) {
            return;
        }

        WallpaperPreferences.Options current = preferences.load();
        WallpaperPreferences.Options updated = current.withSceneryMode(mode);
        preferences.save(updated);
        selectedMode = mode;
        renderSelection();

        announceForAccessibility(getResources().getString(
                R.string.wallpaper_scenery_changed_accessibility,
                getResources().getString(labelRes(mode))
        ));
    }

    private void changeVariation() {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        selectedVariant = SceneryVariantRuntimeState.nextAndPersist(getContext());
        renderVariation();
        announceForAccessibility(getResources().getString(
                R.string.wallpaper_scenery_variation_changed_accessibility,
                selectedVariant + 1,
                SceneryVariantRuntimeState.VARIANT_COUNT
        ));
    }

    private void renderSelection() {
        selectionSummary.setText(getResources().getString(
                R.string.wallpaper_scenery_selected_format,
                getResources().getString(labelRes(selectedMode))
        ));

        for (int index = 0; index < SELECTABLE_MODES.length; index++) {
            TextView chip = chips[index];
            boolean selected = SELECTABLE_MODES[index] == selectedMode;
            chip.setSelected(selected);
            chip.setActivated(selected);
            chip.setBackgroundResource(
                    selected ? R.drawable.bg_weather_chip_selected : R.drawable.bg_weather_chip
            );
            chip.setTextColor(ContextCompat.getColor(
                    getContext(),
                    selected ? R.color.weather_aqua : R.color.weather_text_primary
            ));
        }

        renderVariation();
    }

    private void renderVariation() {
        variationSummary.setText(getResources().getString(
                R.string.wallpaper_scenery_variation_format,
                selectedVariant + 1,
                SceneryVariantRuntimeState.VARIANT_COUNT
        ));
        variationAction.setContentDescription(getResources().getString(
                R.string.wallpaper_scenery_variation_accessibility,
                selectedVariant + 1,
                SceneryVariantRuntimeState.VARIANT_COUNT
        ));
    }

    private int labelRes(@NonNull SceneryMode mode) {
        switch (mode) {
            case OPEN_SKY:
                return R.string.wallpaper_scenery_open_sky;
            case VILLAGE:
                return R.string.wallpaper_scenery_village;
            case FARM_CROPS:
                return R.string.wallpaper_scenery_farm;
            case RIVER_LAKE:
                return R.string.wallpaper_scenery_river;
            case FLOWERS_GREENERY:
                return R.string.wallpaper_scenery_flowers;
            case URBAN_BUILDINGS:
                return R.string.wallpaper_scenery_urban;
            case AUTO:
                return R.string.wallpaper_scenery_auto;
            case NATURAL_HILLS:
            default:
                return R.string.wallpaper_scenery_natural_hills;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
