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
import com.tridev.liveweather.domain.scene.SceneryRuntimeState;
import com.tridev.liveweather.domain.scene.SceneryVariantRuntimeState;

/**
 * Compact professional scenery selector for the Wallpaper page.
 *
 * S9 exposes the existing AUTO preference as a real user-facing mode, clearly separates
 * requested Auto/manual selection from the currently resolved render scene, and replaces
 * the old cycle-only variation action with direct 1..4 variation chips.
 *
 * This view changes only visual scenery preferences. Weather truth, astronomy,
 * precipitation, cloud state, alerts and cached observations are untouched.
 */
public final class ScenerySelectorView extends LinearLayout {

    private static final SceneryMode[] SELECTABLE_MODES = {
            SceneryMode.AUTO,
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
    private final TextView selectionDetail;
    private final TextView variationSummary;
    private final TextView[] sceneChips = new TextView[SELECTABLE_MODES.length];
    private final TextView[] variationChips =
            new TextView[SceneryVariantRuntimeState.VARIANT_COUNT];

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
        selectedVariant = SceneryVariantRuntimeState.get();

        selectionSummary = new TextView(context);
        selectionSummary.setTextAppearance(R.style.TextAppearance_LiveWeather_Body);
        selectionSummary.setTextColor(ContextCompat.getColor(context, R.color.weather_aqua));
        selectionSummary.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        addView(selectionSummary, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        selectionDetail = new TextView(context);
        selectionDetail.setTextAppearance(R.style.TextAppearance_LiveWeather_Caption);
        selectionDetail.setTextColor(ContextCompat.getColor(context, R.color.weather_text_secondary));
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = dp(3);
        addView(selectionDetail, detailParams);

        HorizontalScrollView sceneScroller = new HorizontalScrollView(context);
        sceneScroller.setHorizontalScrollBarEnabled(false);
        sceneScroller.setVerticalScrollBarEnabled(false);
        sceneScroller.setFillViewport(false);
        sceneScroller.setClipToPadding(false);
        sceneScroller.setOverScrollMode(OVER_SCROLL_NEVER);

        LinearLayout sceneRow = new LinearLayout(context);
        sceneRow.setOrientation(HORIZONTAL);
        sceneRow.setGravity(Gravity.CENTER_VERTICAL);
        sceneRow.setClipChildren(false);
        sceneRow.setClipToPadding(false);
        sceneRow.setPadding(0, dp(10), 0, 0);

        for (int index = 0; index < SELECTABLE_MODES.length; index++) {
            SceneryMode mode = SELECTABLE_MODES[index];
            TextView chip = createSceneChip(context, mode);
            sceneChips[index] = chip;

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(48)
            );
            if (index < SELECTABLE_MODES.length - 1) {
                params.setMarginEnd(dp(8));
            }
            sceneRow.addView(chip, params);
        }

        sceneScroller.addView(sceneRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        addView(sceneScroller, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        variationSummary = new TextView(context);
        variationSummary.setTextAppearance(R.style.TextAppearance_LiveWeather_Caption);
        variationSummary.setTextColor(ContextCompat.getColor(context, R.color.weather_text_secondary));
        variationSummary.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams variationSummaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        variationSummaryParams.topMargin = dp(10);
        addView(variationSummary, variationSummaryParams);

        LinearLayout variationRow = new LinearLayout(context);
        variationRow.setOrientation(HORIZONTAL);
        variationRow.setGravity(Gravity.CENTER_VERTICAL);
        variationRow.setPadding(0, dp(6), 0, 0);

        for (int variant = 0; variant < SceneryVariantRuntimeState.VARIANT_COUNT; variant++) {
            TextView chip = createVariationChip(context, variant);
            variationChips[variant] = chip;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
            if (variant < SceneryVariantRuntimeState.VARIANT_COUNT - 1) {
                params.setMarginEnd(dp(8));
            }
            variationRow.addView(chip, params);
        }

        addView(variationRow, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        renderSelection();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        selectedMode = preferences.load().getSceneryMode();
        selectedVariant = SceneryVariantRuntimeState.get();
        renderSelection();
    }

    @NonNull
    private TextView createSceneChip(@NonNull Context context, @NonNull SceneryMode mode) {
        TextView chip = new TextView(context);
        chip.setTextAppearance(R.style.TextAppearance_LiveWeather_Body);
        chip.setText(labelRes(mode));
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setMinWidth(dp(mode == SceneryMode.AUTO ? 96 : 76));
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

    @NonNull
    private TextView createVariationChip(@NonNull Context context, int variant) {
        TextView chip = new TextView(context);
        chip.setTextAppearance(R.style.TextAppearance_LiveWeather_Body);
        chip.setText(String.valueOf(variant + 1));
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setMinWidth(dp(48));
        chip.setMinHeight(dp(48));
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setContentDescription(getResources().getString(
                R.string.wallpaper_scenery_variation_select_accessibility,
                variant + 1,
                SceneryVariantRuntimeState.VARIANT_COUNT
        ));
        chip.setOnClickListener(view -> selectVariation(variant));
        return chip;
    }

    private void selectMode(@NonNull SceneryMode mode) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        if (selectedMode == mode) {
            if (mode == SceneryMode.AUTO) {
                preferences.refreshRuntimeScenery();
            }
            renderSelection();
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

    private void selectVariation(int variant) {
        if (selectedVariant == variant) {
            return;
        }

        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        selectedVariant = SceneryVariantRuntimeState.setAndPersist(getContext(), variant);
        if (selectedMode == SceneryMode.AUTO) {
            preferences.refreshRuntimeScenery();
        }
        renderSelection();

        announceForAccessibility(getResources().getString(
                R.string.wallpaper_scenery_variation_changed_accessibility,
                selectedVariant + 1,
                SceneryVariantRuntimeState.VARIANT_COUNT
        ));
    }

    private void renderSelection() {
        if (selectedMode == SceneryMode.AUTO) {
            SceneryMode resolved = SceneryRuntimeState.get();
            selectionSummary.setText(getResources().getString(
                    R.string.wallpaper_scenery_auto_selected_format,
                    getResources().getString(labelRes(resolved))
            ));
            selectionDetail.setText(R.string.wallpaper_scenery_auto_detail);
        } else {
            selectionSummary.setText(getResources().getString(
                    R.string.wallpaper_scenery_selected_format,
                    getResources().getString(labelRes(selectedMode))
            ));
            selectionDetail.setText(R.string.wallpaper_scenery_manual_detail);
        }

        for (int index = 0; index < SELECTABLE_MODES.length; index++) {
            TextView chip = sceneChips[index];
            boolean selected = SELECTABLE_MODES[index] == selectedMode;
            applyChipState(chip, selected);
        }

        renderVariation();
    }

    private void renderVariation() {
        variationSummary.setText(getResources().getString(
                R.string.wallpaper_scenery_variation_format,
                selectedVariant + 1,
                SceneryVariantRuntimeState.VARIANT_COUNT
        ));

        for (int variant = 0; variant < variationChips.length; variant++) {
            applyChipState(variationChips[variant], variant == selectedVariant);
        }
    }

    private void applyChipState(@NonNull TextView chip, boolean selected) {
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

    private int labelRes(@NonNull SceneryMode mode) {
        switch (mode) {
            case AUTO:
                return R.string.wallpaper_scenery_auto;
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
            case NATURAL_HILLS:
            default:
                return R.string.wallpaper_scenery_natural_hills;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
